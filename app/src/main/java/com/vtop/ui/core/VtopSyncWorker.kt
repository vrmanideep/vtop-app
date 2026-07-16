package com.vtop.ui.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.vtop.network.VtopClient
import com.vtop.network.VtopException
import com.vtop.utils.NotificationHelper
import com.vtop.utils.*
import com.vtop.logic.*
import com.vtop.logic.MarksParser
import com.vtop.logic.AttendanceParser
import com.vtop.logic.OutingParser
import com.vtop.utils.SemesterTransitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.os.SystemClock
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

class VtopSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val maxRetry = 3
    private val tag = "VTOP_WORKER"

    private fun String?.clean(): String {
        if (this.isNullOrBlank() || this.trim() == "-" || this.trim().equals("TBD", ignoreCase = true) || this.trim().equals("N/A", ignoreCase = true) || this.trim().equals("null", ignoreCase = true)) {
            return " "
        }
        return this.trim()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workerStart = SystemClock.elapsedRealtime()

        Telemetry.log(
            level = TelemetryStatus.INFO,
            tag = "VTOP_WORKER",
            message = "Worker started",
            module = TelemetryModule.WORK,
            metadata = mapOf(
                "worker" to javaClass.simpleName,
                "runAttemptCount" to runAttemptCount
            )
        )
        Log.d(tag, "Background sync started.")

        try {
            val creds = Vault.getCredentials(context)
            val username = creds[0]
            val password = creds[1]

            if (username.isNullOrBlank() || password.isNullOrBlank()) {

                Telemetry.log(
                    level = TelemetryStatus.FAILURE,
                    tag = "VTOP_WORKER",
                    message = "Credentials missing",
                    module = TelemetryModule.WORK
                )

                return@withContext Result.failure()
            }

            val client = VtopClient(context, username, password)

            var loginSuccess = false
            var otpTriggered = false
            var attempts = 0

            while (attempts < maxRetry && !loginSuccess && !otpTriggered) {
                try {
                    loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                        override fun onStatusUpdate(message: String) {}
                        override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                            CoroutineScope(Dispatchers.IO).launch {
                                // Capture the exact moment the background worker hit the OTP wall
                                val otpRequestedTime = System.currentTimeMillis()
                                val savedEmail = Vault.getGoogleEmail(context)

                                if (savedEmail.isNotBlank()) {
                                    val extractedOtp = GmailOtpExtractor.getLatestVtopOtp(
                                        context,
                                        savedEmail,
                                        otpRequestedTime // <-- Pass the timestamp here
                                    )
                                    if (extractedOtp != null) {
                                        resolver.submit(extractedOtp)
                                        return@launch
                                    }
                                }

                                if (AppBridge.isAppInForeground) {
                                    withContext(Dispatchers.Main) { AppBridge.currentOtpResolver.value = resolver }
                                } else {
                                    otpTriggered = true
                                    val deferredOtp = kotlinx.coroutines.CompletableDeferred<String?>()
                                    AppBridge.pendingOtpDeferred = deferredOtp
                                    NotificationHelper.showOtpNotification(context)

                                    val userOtp = withTimeoutOrNull(180_000L) { deferredOtp.await() }

                                    if (userOtp != null) {
                                        resolver.submit(userOtp)
                                    } else {
                                        resolver.cancel()
                                        AppBridge.pendingOtpDeferred = null
                                        NotificationHelper.dismissNotification(context, NotificationHelper.OTP_NOTIFICATION_ID)
                                    }
                                }
                            }
                        }
                    })

                    if (otpTriggered) return@withContext Result.failure()

                    if (!loginSuccess) {
                        attempts++
                        client.reinitializeSession(context)
                    }
                } catch (_: VtopException.InvalidCredentials) {
                    Vault.saveCredentials(context, "", "")
                    NotificationHelper.showNotification(context, "VTOP Sync Failed", "Your password may have changed. Please open the app and log in again.", 999)
                    return@withContext Result.failure()
                } catch (_: VtopException.AuthenticationFailed) {
                    NotificationHelper.showNotification(context, "VTOP Account Locked", "Max attempts reached. Please login in VTOP manually.", 998)
                    return@withContext Result.failure()
                } catch (_: Exception) {
                    attempts++
                    client.reinitializeSession(context)
                } finally {
                    withContext(Dispatchers.Main) { AppBridge.currentOtpResolver.value = null }
                    AppBridge.pendingOtpDeferred = null
                }
            }

            if (!loginSuccess) return@withContext Result.retry()

            var authorizedId = Vault.getRegNo(context)
            if (authorizedId.isBlank() || authorizedId == "-") {
                authorizedId = username
            }

            client.setAuthorizedId(authorizedId)
            val semInfo = Vault.getSelectedSemester(context)
            val semId = semInfo[0] ?: ""

            // --- 1. CHECK ATTENDANCE ---
            val attHtml = client.fetchAttendanceRawHtml(semId, null) ?: ""
            val rawAttendance = AttendanceParser.parseSummary(attHtml)
            if (rawAttendance.isNotEmpty()) {
                Vault.saveAttendance(context, rawAttendance)
                withContext(Dispatchers.Main) { AppBridge.attendanceState.value = rawAttendance }
            }

            // --- 2. CHECK EXAM SEATS ---
            val isExamSync = inputData.getBoolean("IS_EXAM_SYNC", false)
            // Note: If you want it to check for exams on EVERY background sync, remove the 'if (isExamSync)' block
            if (isExamSync) {
                val oldExams = Vault.getExamSchedule(context) ?: emptyList()
                val examHtml = client.fetchExamScheduleRawHtml(semId, null) ?: ""
                val newExams = ExamScheduleParser.parse(examHtml)

                if (newExams.isNotEmpty()) {
                    val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time

                    val newlySeatedExams = newExams.filter { newExam ->
                        val hasSeat = !newExam.seatNumber.isNullOrBlank() && !newExam.seatNumber.contains("TBD", ignoreCase = true)
                        val isUpcoming = try { val d = sdf.parse(newExam.examDate); d != null && !d.before(today) } catch (_: Exception) { true }
                        val oldExam = oldExams.find { it.courseCode == newExam.courseCode && it.examType == newExam.examType }
                        hasSeat && isUpcoming && (oldExam == null || oldExam.seatNumber != newExam.seatNumber)
                    }

                    newlySeatedExams.forEachIndexed { index, exam ->
                        // Calculate exact start time for the sticky notification
                        val dateTimeString = "${exam.examDate} ${exam.reportingTime.clean().ifBlank { "09:00 AM" }}"
                        val examStartTimeMillis = try {
                            SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH).parse(dateTimeString)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }

                        // Use your new custom sticky notification
                        NotificationHelper.showExamSeatNotification(
                            context = context,
                            title = "Exam Seating Allotment",
                            message = "${exam.venue} | Seat ${exam.seatLocation} (${exam.seatNumber}) | ${exam.courseCode} ${exam.examType}",
                            examStartTimeMillis = examStartTimeMillis
                        )
                        delay(1000)
                    }
                    Vault.saveExamSchedule(context, newExams)
                    withContext(Dispatchers.Main) { AppBridge.examsState.value = newExams }
                }
            }

            // --- 3. CHECK MARKS ---
            val oldMarks = Vault.getMarks(context) ?: emptyList()
            val marksHtml = client.fetchMarksRawHtml(semId, null) ?: ""
            val newMarks = MarksParser.parseMarks(marksHtml)

            if (newMarks.isNotEmpty()) {
                var notificationCount = 0
                newMarks.forEach { newCourse ->
                    val oldCourse = oldMarks.find { it.courseCode == newCourse.courseCode && it.courseType == newCourse.courseType }
                    newCourse.details.forEach { newMark ->
                        val oldMark = oldCourse?.details?.find { it.title == newMark.title }
                        val validScore = newMark.scoredMark.isNotBlank() && newMark.scoredMark != "-"
                        if (validScore && (oldMark == null || oldMark.scoredMark != newMark.scoredMark)) {
                            NotificationHelper.showNotification(context, "New Marks Uploaded", "Your ${newMark.title} marks of ${newCourse.courseCode} - ${newCourse.courseType} have been updated.", 301 + notificationCount)
                            notificationCount++
                            delay(1000)
                        }
                    }
                }
                Vault.saveMarks(context, newMarks)
                withContext(Dispatchers.Main) { AppBridge.marksState.value = newMarks }
            }

            // --- 4. CHECK OUTINGS ---
            val oldOutings = Vault.getOutings(context) ?: emptyList()
            val genHtml = client.fetchGeneralOutingRawHtml(authorizedId, null) ?: ""
            val weekHtml = client.fetchWeekendOutingRawHtml(authorizedId, null) ?: ""
            val newOutings = OutingParser.parseGeneral(genHtml) + OutingParser.parseWeekend(weekHtml)

            if (newOutings.isNotEmpty()) {
                newOutings.forEach { newOut ->
                    val oldOut = oldOutings.find { it.id == newOut.id }

                    // --- THE FIX: EXPIRED LEAVE CHECK ---
                    val isExpired = try {
                        val sdfOut = if (newOut.fromDate.contains("-") && newOut.fromDate.split("-")[0].length == 4) {
                            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                        } else {
                            SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                        }
                        val startDate = sdfOut.parse(newOut.fromDate)

                        val todayMidnight = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.time

                        startDate != null && startDate.before(todayMidnight)
                    } catch (e: Exception) {
                        false // If parsing fails, default to false to be safe
                    }

                    // Only trigger notification if status changed AND the leave hasn't expired yet
                    if (oldOut != null && oldOut.status != newOut.status && !isExpired) {
                        val s = newOut.status.uppercase(Locale.getDefault())
                        val oldS = oldOut.status.uppercase(Locale.getDefault())
                        val notifId = 501 + (newOut.id.hashCode() % 1000)

                        if (s.contains("FORWARD") && !oldS.contains("FORWARD")) {
                            NotificationHelper.showNotification(context, "Outing Update", "Your mentor has approved your request.", notifId)
                        } else if ((s.contains("APPROVE") || s.contains("ACCEPT") || s.contains("ISSUED")) && !oldS.contains("APPROVE") && !oldS.contains("ACCEPT")) {
                            NotificationHelper.showNotification(context, "Outing Approved!", "Your warden has approved your request.", notifId)
                        } else if ((s.contains("REJECT") || s.contains("DECLINE")) && !oldS.contains("REJECT") && !oldS.contains("DECLINE")) {
                            NotificationHelper.showNotification(context, "Outing Rejected", "Your outing request was rejected.", notifId)
                        }
                    }
                }
                Vault.saveOutings(context, newOutings)
                withContext(Dispatchers.Main) { AppBridge.outingsState.value = newOutings }
            }

            Vault.saveLastSyncTime(context)

            // --- 5. SEMESTER TRANSITION ---
            val savedExams = Vault.getExamSchedule(context)
            if (SemesterTransitionEngine.checkIfLastFatIsOver(savedExams)) {
                withContext(Dispatchers.Main) { AppBridge.isSemesterCompleted.value = true }
                if (SemesterTransitionEngine.attemptAutoSwitch(context)) {
                    WorkManager.getInstance(context).enqueueUniqueWork("TRANSITION_SYNC", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<VtopSyncWorker>().build())
                }
            }

            val duration = SystemClock.elapsedRealtime() - workerStart

            Telemetry.log(
                level = TelemetryStatus.SUCCESS,
                tag = "VTOP_WORKER",
                message = "Worker completed",
                module = TelemetryModule.WORK,
                metadata = mapOf(
                    "worker" to javaClass.simpleName,
                    "durationMs" to duration
                )
            )

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Sync failed: ${e.message}")
            val duration = SystemClock.elapsedRealtime() - workerStart

            Telemetry.log(
                level = TelemetryStatus.ERROR,
                tag = "VTOP_WORKER",
                message = "Worker failed",
                module = TelemetryModule.WORK,
                metadata = mapOf(
                    "worker" to javaClass.simpleName,
                    "durationMs" to duration,
                    "exception" to e.javaClass.simpleName,
                    "message" to e.message
                )
            )

            return@withContext Result.retry()
        }
    }
}