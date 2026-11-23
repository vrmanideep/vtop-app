package com.vtop.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vtop.core.SessionManager
import com.vtop.utils.CourseAutoDownloadConfig
import com.vtop.utils.CourseFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CourseAutoDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = CourseAutoDownloadConfig.load(context)
        val triggeredTime = inputData.getString("SPECIFIC_TIME")

        if (!settings.enabled || !CourseFileManager.isSaveToDiskEnabled(context)) {
            return@withContext Result.success()
        }

        // Re-schedule next day's run if this was a specific-time trigger
        if (!triggeredTime.isNullOrBlank() && settings.specificTimeEnabled && triggeredTime in settings.specificTimes) {
            CourseAutoDownloadScheduler.scheduleSingleTime(context, triggeredTime, settings.wifiOnly, forceNextDay = true)
        }

        val client = SessionManager.getSyncClient() ?: return@withContext Result.retry()
        val ok = CourseAutoDownloadEngine.runBlockingForWorker(context, client)
        if (ok) Result.success() else Result.retry()
    }
}