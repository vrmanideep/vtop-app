package com.vtop.utils

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vtop.models.ExamScheduleModel
import com.vtop.ui.core.VtopSyncWorker
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

                // 1. Calculate actual 7:01 AM Sync
                val morningTarget = Calendar.getInstance().apply {
                    time = examDate
                    set(Calendar.HOUR_OF_DAY, 7)
                    set(Calendar.MINUTE, 1)
                    set(Calendar.SECOND, 0)
                }

                if (morningTarget.after(now)) {
                    val delay = morningTarget.timeInMillis - now.timeInMillis
                    queueWorker(workManager, "EXAM_MORNING_${exam.courseCode}", delay)
                }

                // 2. Calculate actual 12:01 PM Sync (Only if it's a CAT)
                if (!isFat) {
                    val afternoonTarget = Calendar.getInstance().apply {
                        time = examDate
                        set(Calendar.HOUR_OF_DAY, 12)
                        set(Calendar.MINUTE, 1)
                        set(Calendar.SECOND, 0)
                    }

                    if (afternoonTarget.after(now)) {
                        val delay = afternoonTarget.timeInMillis - now.timeInMillis
                        queueWorker(workManager, "EXAM_AFTERNOON_${exam.courseCode}", delay)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse date for ${exam.courseCode}: ${e.message}")
            }
        }
    }

    private fun queueWorker(workManager: WorkManager, uniqueId: String, actualDelayInMillis: Long) {
        val inputData = Data.Builder()
            .putBoolean("IS_EXAM_SYNC", true)
            .build()

        // We removed the 10-second test override.
        // WorkManager will now wait for the exact 'actualDelayInMillis' calculated earlier.
        val syncRequest = OneTimeWorkRequestBuilder<VtopSyncWorker>()
            .setInitialDelay(actualDelayInMillis, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .build()

        // REPLACE ensures that if the app schedules the same exam twice,
        // it overwrites the old timer instead of creating a duplicate.
        workManager.enqueueUniqueWork(
            uniqueId,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
        Log.d(TAG, "REAL QUEUE: $uniqueId scheduled to fire in ${actualDelayInMillis / 1000 / 60} minutes.")
    }
}