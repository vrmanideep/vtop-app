package com.vtop.sync

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vtop.core.EventBus
import com.vtop.core.AppEvent
import com.vtop.core.SessionManager
import com.vtop.core.SessionType
import com.vtop.core.ExamsRepository
import com.vtop.core.MarksRepository
import com.vtop.core.OutingsRepository
import com.vtop.network.VtopClient
import com.vtop.network.VtopException
import com.vtop.utils.*
import com.vtop.logic.*
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
            metadata = mapOf("worker" to javaClass.simpleName, "runAttemptCount" to runAttemptCount)
        )
        Log.d(tag, "Background sync started.")

        try {
            val creds = Vault.getCredentials(context)
            val username = creds[0]
            val password = creds[1]

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                Telemetry.log(TelemetryStatus.FAILURE, "VTOP_WORKER", "Credentials missing", TelemetryModule.WORK)
                return@withContext Result.failure()
            }

            val client = SessionManager.getSyncClient() ?: SessionManager.createClient(context, SessionType.SYNC).first

            var loginSuccess = false
            var otpTriggered = false
            var attempts = 0

            while (attempts < maxRetry && !loginSuccess && !otpTriggered) {
                try {
                    loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                        override fun onStatusUpdate(message: String) {}
                        override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val otpRequestedTime = System.currentTimeMillis()
                                val savedEmail = Vault.getGoogleEmail(context)

                                if (savedEmail.isNotBlank()) {
                                    var extractedOtp: String? = null
                                    // Polling loop: Check every 3 seconds, up to 6 times (18s total)
                                    for (i in 1..6) {
                                        delay(3000)
                                        extractedOtp = GmailOtpExtractor.getLatestVtopOtp(context, savedEmail, otpRequestedTime)
                                        if (extractedOtp != null) break
                                    }

                                    if (extractedOtp != null) {
                                        resolver.submit(extractedOtp)
                                        return@launch
                                    }
                                }

                                otpTriggered = true

                                // Check if the UI is actually alive to catch the EventBus
                                if (com.vtop.core.AppState.isAppInForeground) {
                                    EventBus.emit(AppEvent.AuthOtpRequested(resolver))
                                } else {
                                    // If the app is killed/backgrounded. Push a standard notification to wake the user.
                                    NotificationHelper.showNotification(
                                        context = context,
                                        title = "VTOP Sync Paused",
                                        message = "VTOP requires an OTP. Tap to open the app and resume syncing.",
                                        notificationId = NotificationHelper.OTP_NOTIFICATION_ID
                                    )
                                    resolver.cancel()
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
                    NotificationHelper.showNotification(context, "VTOP Sync Failed", "Your password may have changed. Please log in again.", 999)
                    return@withContext Result.failure()
                } catch (_: VtopException.AuthenticationFailed) {
                    NotificationHelper.showNotification(context, "VTOP Account Locked", "Max attempts reached. Please login in VTOP manually.", 998)
                    return@withContext Result.failure()
                } catch (_: Exception) {
                    attempts++
                    client.reinitializeSession(context)
                }
            }

            if (!loginSuccess) return@withContext Result.retry()
            SessionManager.setSyncClient(client)

            var authorizedId = Vault.getRegNo(context)
            if (authorizedId.isBlank() || authorizedId == "-") authorizedId = username

            client.setAuthorizedId(authorizedId)
            val semInfo = Vault.getSelectedSemester(context)
            val semId = semInfo[0] ?: ""

            AttendanceSyncEngine.sync(
                context = context,
                client = client,
                semId = semId,
                authorizedId = authorizedId,
                mode = AttendanceSyncMode.OPTIMIZED,
                logTag = "ATT_OPT_BG"
            )

            if (inputData.getBoolean("IS_EXAM_SYNC", false)) {
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

                    newlySeatedExams.forEach { exam ->
                        val dateTimeString = "${exam.examDate} ${exam.reportingTime.clean().ifBlank { "09:00 AM" }}"
                        val examStartTimeMillis = try {
                            SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH).parse(dateTimeString)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) { System.currentTimeMillis() }

                        NotificationHelper.showExamSeatNotification(
                            context = context,
                            title = "Exam Seating Allotment",
                            message = "${exam.venue} | Seat ${exam.seatLocation} (${exam.seatNumber}) | ${exam.courseCode} ${exam.examType}",
                            examStartTimeMillis = examStartTimeMillis
                        )
                        delay(1000)
                    }
                    ExamsRepository.update(context, newExams)
                }
            }

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
                MarksRepository.update(context, newMarks)
            }

            val oldOutings = Vault.getOutings(context) ?: emptyList()
            val genHtml = client.fetchGeneralOutingRawHtml(authorizedId, null) ?: ""
            val weekHtml = client.fetchWeekendOutingRawHtml(authorizedId, null) ?: ""
            val newOutings = OutingParser.parseGeneral(genHtml) + OutingParser.parseWeekend(weekHtml)

            if (newOutings.isNotEmpty()) {
                newOutings.forEach { newOut ->
                    val oldOut = oldOutings.find { it.id == newOut.id }
                    val isExpired = try {
                        val sdfOut = if (newOut.fromDate.contains("-") && newOut.fromDate.split("-")[0].length == 4) SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) else SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                        val startDate = sdfOut.parse(newOut.fromDate)
                        val todayMidnight = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
                        startDate != null && startDate.before(todayMidnight)
                    } catch (e: Exception) { false }

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
                OutingsRepository.update(context, newOutings)
            }

            Vault.saveLastSyncTime(context)

            val duration = SystemClock.elapsedRealtime() - workerStart
            Telemetry.log(TelemetryStatus.SUCCESS, "VTOP_WORKER", "Worker completed", TelemetryModule.WORK, mapOf("worker" to javaClass.simpleName, "durationMs" to duration))

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Sync failed: ${e.message}")
            val duration = SystemClock.elapsedRealtime() - workerStart
            Telemetry.log(TelemetryStatus.ERROR, "VTOP_WORKER", "Worker failed", TelemetryModule.WORK, mapOf("worker" to javaClass.simpleName, "durationMs" to duration, "exception" to e.javaClass.simpleName, "message" to e.message))
            return@withContext Result.retry()
        }
    }
}