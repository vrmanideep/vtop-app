package com.vtop.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vtop.utils.ExamSeatScheduler
import com.vtop.utils.Vault

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Double-check that this is actually a reboot broadcast
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BOOT_RECEIVER", "Device rebooted. Rebuilding exam queue...")

            // Read the exams we saved to disk during the last sync
            val savedExams = Vault.getExamSchedule(context)

            if (savedExams.isNotEmpty()) {
                // Feed them back into the scheduler to recreate the background tasks
                ExamSeatScheduler.buildExamQueue(context, savedExams)
                Log.d("BOOT_RECEIVER", "Successfully rescheduled ${savedExams.size} exams.")
            } else {
                Log.d("BOOT_RECEIVER", "No saved exams found. Nothing to schedule.")
            }
        }
    }
}