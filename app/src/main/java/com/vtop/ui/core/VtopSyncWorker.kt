package com.vtop.ui.core

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import com.vtop.network.VtopClient
import com.vtop.network.VtopException
import com.vtop.utils.NotificationHelper
import com.vtop.utils.Vault
import com.vtop.logic.ExamScheduleParser
import com.vtop.logic.MarksParser
import com.vtop.logic.AttendanceParser
import com.vtop.utils.SemesterTransitionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VtopSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val MAX_RETRY = 3
    private val TAG = "VTOP_WORKER"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Background sync started.")

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
            var attempts = 0
            while (attempts < MAX_RETRY && !loginSuccess) {
                try {
                    loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                        override fun onStatusUpdate(message: String) {}
                        override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                            Log.w(TAG, "OTP Required during background sync. Aborting.")
                        }
                    })
                    if (!loginSuccess) {
                        attempts++
                        client.reinitializeSession(context)
                    }
                } catch (e: VtopException.InvalidCredentials) {
                    Log.e(TAG, "Invalid credentials. Wiping saved creds and aborting worker.")
                    Vault.saveCredentials(context, "", "") // Wipe credentials
                    NotificationHelper.showNotification(
                        context = context,
                        title = "VTOP Sync Failed",
                        message = "Your password may have changed. Please open the app and log in again.",
                        notificationId = 999
                    )
                    return@withContext Result.failure() // Do not retry
                } catch (e: VtopException.AuthenticationFailed) {
                    Log.e(TAG, "Account locked. Aborting sync to prevent further locks.")
                    NotificationHelper.showNotification(
                        context = context,
                        title = "VTOP Account Locked",
                        message = "Max attempts reached. Please login in VTOP manually and re-enable sync before syncing.",
                        notificationId = 998
                    )
                    return@withContext Result.failure() // Do not retry
                } catch (e: Exception) {
                    attempts++
                    client.reinitializeSession(context)
                }
            }

            if (!loginSuccess) {
                return@withContext Result.retry() // Standard timeout, allow Android to schedule a retry
            }

            // --- 1. CHECK ATTENDANCE (RAW DATA ONLY) ---
            val attHtml = client.fetchAttendanceRawHtml(semId, null) ?: ""
            val rawAttendance = AttendanceParser.parseSummary(attHtml)
            if (rawAttendance.isNotEmpty()) {
                Vault.saveAttendance(context, rawAttendance)
                withContext(Dispatchers.Main) { AppBridge.attendanceState.value = rawAttendance }
            }

            // --- 2. CHECK EXAM SEATS ---
            val isExamSync = inputData.getBoolean("IS_EXAM_SYNC", false)
            if (isExamSync) {
                val examHtml = client.fetchExamScheduleRawHtml(semId, null) ?: ""
                val newExams = ExamScheduleParser.parse(examHtml)

                if (newExams.isNotEmpty()) {
                    val updatedExam = newExams.firstOrNull {
                        !it.seatNumber.isNullOrBlank() && !it.seatNumber.contains("TBD", ignoreCase = true)
                    }

                    val messageText = updatedExam?.let {
                        " ${it.venue}  |   Seat ${it.seatLocation} (${it.seatNumber})  |  📚${it.courseCode} ${it.examType}"
                    } ?: "Your exam seating allotment has been updated."

                    NotificationHelper.showNotification(
                        context = context,
                        title = "Exam Seating Allotment",
                        message = messageText,
                        notificationId = 401
                    )

                    Vault.saveExamSchedule(context, newExams)
                    withContext(Dispatchers.Main) { AppBridge.examsState.value = newExams }
                }
            }

            // --- 3. CHECK MARKS ---
            val oldMarks = Vault.getMarks(context) ?: emptyList()
            val marksHtml = client.fetchMarksRawHtml(semId, null) ?: ""
            val newMarks = MarksParser.parseMarks(marksHtml)

            if (newMarks.isNotEmpty()) {
                val oldMarksString = com.google.gson.Gson().toJson(oldMarks)
                val newMarksString = com.google.gson.Gson().toJson(newMarks)
                if (oldMarksString != newMarksString && oldMarks.isNotEmpty()) {
                    NotificationHelper.showNotification(
                        context = context,
                        title = "New Marks Uploaded",
                        message = "Your academic marks have been updated in VTOP.",
                        notificationId = 301
                    )
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
            Log.e(TAG, "Sync failed: ${e.message}")
            return@withContext Result.retry()
        }
    }
}