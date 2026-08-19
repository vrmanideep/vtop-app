package com.vtop.sync

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vtop.models.ExamScheduleModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

object ExamSeatScheduler {

    private const val TAG = "EXAM_QUEUE"

    fun buildExamQueue(context: Context, exams: List<ExamScheduleModel>) {
        if (exams.isEmpty()) return

        val workManager = WorkManager.getInstance(context)
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val now = Calendar.getInstance()

        for (exam in exams) {
            if (exam.examDate.isBlank() || exam.examDate.contains("TBD", ignoreCase = true) || exam.examDate == "-") {
                continue
            }
            try {
                val examDate = dateFormat.parse(exam.examDate) ?: continue
                val isFat = exam.examType.uppercase().contains("FAT")

                // 1. FIX: Prevent ID Collisions by combining Course Code and Exam Type
                val safeExamType = exam.examType.replace(" ", "_")
                val baseId = "${exam.courseCode}_$safeExamType"

                // 2. FIX: Schedule the 2-day reminder using ExamCheckWorker
                val reminderTarget = Calendar.getInstance().apply {
                    time = examDate
                    add(Calendar.DAY_OF_YEAR, -2) // 2 days before the exam
                    set(Calendar.HOUR_OF_DAY, 9)  // At 9:00 AM
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }

                if (reminderTarget.after(now)) {
                    val delay = reminderTarget.timeInMillis - now.timeInMillis
                    queueReminderWorker(workManager, "REMINDER_$baseId", delay, exam.examType)
                }

                // 3. Calculate actual 7:01 AM Sync
                val morningTarget = Calendar.getInstance().apply {
                    time = examDate
                    set(Calendar.HOUR_OF_DAY, 7)
                    set(Calendar.MINUTE, 1)
                    set(Calendar.SECOND, 0)
                }

                if (morningTarget.after(now)) {
                    val delay = morningTarget.timeInMillis - now.timeInMillis
                    queueSyncWorker(workManager, "EXAM_MORNING_$baseId", delay)
                }

                // 4. Calculate actual 12:01 PM Sync (Only if it's a CAT)
                if (!isFat) {
                    val afternoonTarget = Calendar.getInstance().apply {
                        time = examDate
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 1)
                        set(Calendar.SECOND, 0)
                    }

                    if (afternoonTarget.after(now)) {
                        val delay = afternoonTarget.timeInMillis - now.timeInMillis
                        queueSyncWorker(workManager, "EXAM_AFTERNOON_$baseId", delay)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse date for ${exam.courseCode}: ${e.message}")
            }
        }
    }

    private fun queueSyncWorker(workManager: WorkManager, uniqueId: String, actualDelayInMillis: Long) {
        val inputData = Data.Builder()
            .putBoolean("IS_EXAM_SYNC", true)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<VtopSyncWorker>()
            .setInitialDelay(actualDelayInMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            uniqueId,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Log.d(TAG, "SYNC QUEUE: $uniqueId scheduled to fire in ${actualDelayInMillis / 1000 / 60} minutes.")
    }

    private fun queueReminderWorker(workManager: WorkManager, uniqueId: String, actualDelayInMillis: Long, examType: String) {
        val inputData = Data.Builder()
            .putString("EXAM_TYPE", examType)
            .build()

        val reminderRequest = OneTimeWorkRequestBuilder<ExamCheckWorker>()
            .setInitialDelay(actualDelayInMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        workManager.enqueueUniqueWork(
            uniqueId,
            ExistingWorkPolicy.REPLACE,
            reminderRequest
        )
        Log.d(TAG, "REMINDER QUEUE: $uniqueId scheduled to fire in ${actualDelayInMillis / 1000 / 60} minutes.")
    }
}