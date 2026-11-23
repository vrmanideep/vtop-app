package com.vtop.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vtop.utils.AutoDownloadSettings
import com.vtop.utils.CourseFileManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object CourseAutoDownloadScheduler {
    private const val UNIQUE_INTERVAL_WORK = "COURSE_AUTO_DL_INTERVAL"
    private const val TAG_SPECIFIC_TIME = "COURSE_AUTO_DL_SPECIFIC_TIME"

    fun syncSchedules(context: Context, settings: AutoDownloadSettings) {
        val workManager = WorkManager.getInstance(context)

        // Clear previous schedules first
        workManager.cancelUniqueWork(UNIQUE_INTERVAL_WORK)
        workManager.cancelAllWorkByTag(TAG_SPECIFIC_TIME)

        if (!settings.enabled || !CourseFileManager.isSaveToDiskEnabled(context)) return

        val constraints = buildConstraints(settings.wifiOnly)

        // 1. Periodic interval schedule
        if (settings.intervalEnabled && settings.intervalHours >= 1) {
            val intervalReq = PeriodicWorkRequestBuilder<CourseAutoDownloadWorker>(
                settings.intervalHours.toLong(),
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                UNIQUE_INTERVAL_WORK,
                ExistingPeriodicWorkPolicy.REPLACE,
                intervalReq
            )
        }

        // 2. Specific daily time schedules
        if (settings.specificTimeEnabled) {
            settings.specificTimes.forEach { timeStr ->
                scheduleSingleTime(context, timeStr, settings.wifiOnly, forceNextDay = false)
            }
        }
    }

    fun scheduleSingleTime(
        context: Context,
        timeStr: String,
        wifiOnly: Boolean,
        forceNextDay: Boolean = false
    ) {
        val parts = timeStr.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (forceNextDay || !after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val delayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)
        val req = OneTimeWorkRequestBuilder<CourseAutoDownloadWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(buildConstraints(wifiOnly))
            .addTag(TAG_SPECIFIC_TIME)
            .setInputData(Data.Builder().putString("SPECIFIC_TIME", timeStr).build())
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "COURSE_AUTO_DL_TIME_$timeStr",
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    private fun buildConstraints(wifiOnly: Boolean): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
}