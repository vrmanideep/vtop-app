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
            val username = creds[0]
            val password = creds[1]

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
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
                                val savedEmail = Vault.getGoogleEmail(context)

                                if (savedEmail.isNotBlank()) {
                                    val extractedOtp = GmailOtpExtractor.getLatestVtopOtp(context, savedEmail)
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
                authorizedId = username // Fallback if worker runs before first GlobalSync
            }

            // =========================================================
            // THE FIX: FORCING THE JAVA CLIENT TO USE THE SCRAPED ID
            // =========================================================
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
                        NotificationHelper.showNotification(context, "Exam Seating Allotment", "${exam.venue} | Seat ${exam.seatLocation} (${exam.seatNumber}) | ${exam.courseCode} ${exam.examType}", 401 + index)
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

            Vault.saveLastSyncTime(context)

            // --- 4. SEMESTER TRANSITION ---
            val savedExams = Vault.getExamSchedule(context)
            if (SemesterTransitionEngine.checkIfLastFatIsOver(savedExams)) {
                withContext(Dispatchers.Main) { AppBridge.isSemesterCompleted.value = true }
                if (SemesterTransitionEngine.attemptAutoSwitch(context)) {
                    WorkManager.getInstance(context).enqueueUniqueWork("TRANSITION_SYNC", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<VtopSyncWorker>().build())
                }
            }

            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Sync failed: ${e.message}")
            return@withContext Result.retry()
        }
    }
}