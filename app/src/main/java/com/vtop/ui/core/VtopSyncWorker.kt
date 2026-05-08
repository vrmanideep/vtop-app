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
import com.vtop.logic.ExamScheduleParser
import com.vtop.logic.MarksParser
import com.vtop.logic.AttendanceParser
import com.vtop.utils.SemesterTransitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(tag, "Background sync started.")

        try {
            val creds = Vault.getCredentials(context)
            val regNo = creds[0]
            val password = creds[1]

            if (regNo.isNullOrBlank() || password.isNullOrBlank()) {
                return@withContext Result.failure()
            }

            val semInfo = Vault.getSelectedSemester(context)
            val semId = semInfo[0] ?: ""

            val client = VtopClient(context, regNo, password)

            // --- 0. SMART LOGIN & ERROR TRAFFIC COP ---
            var loginSuccess = false
            var otpTriggered = false
            var attempts = 0

            while (attempts < maxRetry && !loginSuccess && !otpTriggered) {
                try {
                    loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                        override fun onStatusUpdate(message: String) {}

                        override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                            // Launch a background coroutine to handle the suspend functions safely
                            CoroutineScope(Dispatchers.IO).launch {
                                val savedEmail = Vault.getGoogleEmail(context)

                                // 1. Attempt Silent Interception First
                                if (savedEmail.isNotBlank()) {
                                    val extractedOtp = GmailOtpExtractor.getLatestVtopOtp(context, savedEmail)
                                    if (extractedOtp != null) {
                                        Log.d("SYNC", "Silently intercepted OTP: $extractedOtp")
                                        resolver.submit(extractedOtp)
                                        return@launch // Exit coroutine, OTP is handled
                                    }
                                }

                                // 2. Fallback to UI or Notification
                                if (AppBridge.isAppInForeground) {
                                    Log.d(tag, "OTP Required. App is in foreground, routing to UI.")
                                    withContext(Dispatchers.Main) {
                                        AppBridge.currentOtpResolver.value = resolver
                                    }
                                } else {
                                    Log.w(tag, "OTP Required. App in background. Pushing Notification.")
                                    otpTriggered = true

                                    val deferredOtp = kotlinx.coroutines.CompletableDeferred<String?>()
                                    AppBridge.pendingOtpDeferred = deferredOtp

                                    NotificationHelper.showOtpNotification(context)

                                    val userOtp = withTimeoutOrNull(180_000L) {
                                        deferredOtp.await()
                                    }

                                    if (userOtp != null) {
                                        Log.d(tag, "OTP received from notification. Resuming sync...")
                                        resolver.submit(userOtp)
                                    } else {
                                        Log.w(tag, "OTP request timed out. Discarding safely.")
                                        resolver.cancel()
                                        AppBridge.pendingOtpDeferred = null

                                        NotificationHelper.dismissNotification(context, NotificationHelper.OTP_NOTIFICATION_ID)
                                        NotificationHelper.showNotification(
                                            context = context,
                                            title = "Sync Aborted",
                                            message = "VTOP OTP request timed out while you were away.",
                                            notificationId = 997
                                        )
                                    }
                                }
                            }
                        }
                    })

                    if (otpTriggered) {
                        Log.w(tag, "Halting background sync completely due to OTP lock.")
                        return@withContext Result.failure()
                    }

                    if (!loginSuccess) {
                        attempts++
                        client.reinitializeSession(context)
                    }
                } catch (_: VtopException.InvalidCredentials) {
                    Log.e(tag, "Invalid credentials. Wiping saved creds and aborting worker.")
                    Vault.saveCredentials(context, "", "")
                    NotificationHelper.showNotification(
                        context = context,
                        title = "VTOP Sync Failed",
                        message = "Your password may have changed. Please open the app and log in again.",
                        notificationId = 999
                    )
                    return@withContext Result.failure()
                } catch (_: VtopException.AuthenticationFailed) {
                    Log.e(tag, "Account locked. Aborting sync to prevent further locks.")
                    NotificationHelper.showNotification(
                        context = context,
                        title = "VTOP Account Locked",
                        message = "Max attempts reached. Please login in VTOP manually and re-enable sync before syncing.",
                        notificationId = 998
                    )
                    return@withContext Result.failure()
                } catch (_: Exception) {
                    attempts++
                    client.reinitializeSession(context)
                } finally {
                    withContext(Dispatchers.Main) {
                        AppBridge.currentOtpResolver.value = null
                    }
                    AppBridge.pendingOtpDeferred = null
                }
            }

            if (!loginSuccess) {
                return@withContext Result.retry()
            }

            // --- 1. CHECK ATTENDANCE ---
            val attHtml = client.fetchAttendanceRawHtml(semId, null) ?: ""
            val rawAttendance = AttendanceParser.parseSummary(attHtml)
            if (rawAttendance.isNotEmpty()) {
                Vault.saveAttendance(context, rawAttendance)
                withContext(Dispatchers.Main) { AppBridge.attendanceState.value = rawAttendance }
            }

            // --- 2. CHECK EXAM SEATS (GRANULAR DIFFING) ---
            val isExamSync = inputData.getBoolean("IS_EXAM_SYNC", false)
            if (isExamSync) {
                val oldExams = Vault.getExamSchedule(context) ?: emptyList()
                val examHtml = client.fetchExamScheduleRawHtml(semId, null) ?: ""
                val newExams = ExamScheduleParser.parse(examHtml)

                if (newExams.isNotEmpty()) {
                    val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.time

                    val newlySeatedExams = newExams.filter { newExam ->
                        val hasSeat = !newExam.seatNumber.isNullOrBlank() && !newExam.seatNumber.contains("TBD", ignoreCase = true)

                        val isUpcoming = try {
                            val dateString = newExam.examDate
                            val examDate = sdf.parse(dateString)
                            examDate != null && !examDate.before(today)
                        } catch (_: Exception) {
                            true
                        }

                        val oldExam = oldExams.find { it.courseCode == newExam.courseCode && it.examType == newExam.examType }
                        val isNewOrChangedSeat = oldExam == null || oldExam.seatNumber != newExam.seatNumber

                        hasSeat && isUpcoming && isNewOrChangedSeat
                    }

                    newlySeatedExams.forEachIndexed { index, exam ->
                        NotificationHelper.showNotification(
                            context = context,
                            title = "Exam Seating Allotment",
                            message = "${exam.venue} | Seat ${exam.seatLocation} (${exam.seatNumber}) | ${exam.courseCode} ${exam.examType}",
                            notificationId = 401 + index
                        )
                        delay(1000)
                    }

                    Vault.saveExamSchedule(context, newExams)
                    withContext(Dispatchers.Main) { AppBridge.examsState.value = newExams }
                }
            }

            // --- 3. CHECK MARKS (GRANULAR DIFFING) ---
            val oldMarks = Vault.getMarks(context) ?: emptyList()
            val marksHtml = client.fetchMarksRawHtml(semId, null) ?: ""
            val newMarks = MarksParser.parseMarks(marksHtml)

            if (newMarks.isNotEmpty()) {
                var notificationCount = 0

                newMarks.forEach { newCourse ->
                    val oldCourse = oldMarks.find { it.courseCode == newCourse.courseCode && it.courseType == newCourse.courseType }
                    val newAssessments = newCourse.details
                    val oldAssessments = oldCourse?.details ?: emptyList()

                    newAssessments.forEach { newMark ->
                        val oldMark = oldAssessments.find { it.title == newMark.title }

                        val validScore = newMark.scoredMark.isNotBlank() && newMark.scoredMark != "-"
                        val hasChanged = oldMark == null || oldMark.scoredMark != newMark.scoredMark

                        if (validScore && hasChanged) {
                            NotificationHelper.showNotification(
                                context = context,
                                title = "New Marks Uploaded",
                                message = "Your ${newMark.title} marks of ${newCourse.courseCode} - ${newCourse.courseType} have been updated. Tap to see.",
                                notificationId = 301 + notificationCount
                            )
                            notificationCount++
                            delay(1000)
                        }
                    }
                }

                Vault.saveMarks(context, newMarks)
                withContext(Dispatchers.Main) { AppBridge.marksState.value = newMarks }
            }

            Vault.saveLastSyncTime(context)

            // --- 4. SEMESTER TRANSITION ENGINE PIPELINE ---
            val savedExams = Vault.getExamSchedule(context)
            if (SemesterTransitionEngine.checkIfLastFatIsOver(savedExams)) {
                withContext(Dispatchers.Main) { AppBridge.isSemesterCompleted.value = true }
                val successfullySwitched = SemesterTransitionEngine.attemptAutoSwitch(context)

                if (successfullySwitched) {
                    val transitionRequest = OneTimeWorkRequestBuilder<VtopSyncWorker>().build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "TRANSITION_SYNC",
                        ExistingWorkPolicy.REPLACE,
                        transitionRequest
                    )
                }
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Sync failed: ${e.message}")
            return@withContext Result.retry()
        }
    }
}