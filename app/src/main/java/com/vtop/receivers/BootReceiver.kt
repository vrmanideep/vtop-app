package com.vtop.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vtop.sync.ExamSeatScheduler
import com.vtop.utils.Vault
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Double-check that this is actually a reboot broadcast
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Telemetry.log(
                level = TelemetryStatus.INFO,
                tag = "BOOT_RECEIVER",
                message = "BOOT_COMPLETED received",
                module = TelemetryModule.BROADCAST
            )
            Log.d("BOOT_RECEIVER", "Device rebooted. Rebuilding exam queue...")

            // Read the exams we saved to disk during the last sync
            val savedExams = Vault.getExamSchedule(context)

            if (savedExams.isNotEmpty()) {
                // Feed them back into the scheduler to recreate the background tasks
                Telemetry.log(
                    level = TelemetryStatus.INFO,
                    tag = "BOOT_RECEIVER",
                    message = "Restoring exam queue",
                    module = TelemetryModule.BROADCAST,
                    metadata = mapOf(
                        "savedExams" to savedExams.size
                    )
                )
                ExamSeatScheduler.buildExamQueue(context, savedExams)
                Log.d("BOOT_RECEIVER", "Successfully rescheduled ${savedExams.size} exams.")
            } else {
                Telemetry.log(
                    level = TelemetryStatus.INFO,
                    tag = "BOOT_RECEIVER",
                    message = "No saved exams",
                    module = TelemetryModule.BROADCAST
                )
                Log.d("BOOT_RECEIVER", "No saved exams found. Nothing to schedule.")
            }
        }
    }
}