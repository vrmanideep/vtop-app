package com.vtop.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object WidgetUpdater {

    // Safely fire and forget without GlobalScope warnings
    fun updateWidgetNow(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                NextClassWidget().updateAll(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startImmortalWidgetSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiredNetworkType(NetworkType.CONNECTED) // Don't waste battery waking up if there's no internet
            .build()

        val workRequest = PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            // If the VTOP server drops the connection, retry in 1 minute instead of waiting 15 mins
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                1,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "VTOP_WIDGET_SYNC",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}