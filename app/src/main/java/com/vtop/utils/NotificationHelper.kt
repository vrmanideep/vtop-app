package com.vtop.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.FileProvider
import com.vtop.receivers.OtpReceiver
import com.vtop.ui.MainActivity
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus
import java.io.File

object NotificationHelper {
    // --- ORIGINAL CHANNELS & IDs ---
    private const val CHANNEL_ID = "vtop_alerts_channel"
    private const val CHANNEL_NAME = "VTOP Academic Alerts"
    const val OTP_NOTIFICATION_ID = 888

    // --- NEW CHANNELS & IDs ---
    private const val CHANNEL_DOWNLOADS = "DOWNLOADS_CHANNEL"
    private const val CHANNEL_EXAMS = "EXAMS_CHANNEL"
    const val EXAM_NOTIF_ID = 1001

    private fun telemetry(
        event: String,
        title: String,
        id: Int
    ) {
        Telemetry.log(
            level = TelemetryStatus.INFO,
            tag = "NOTIFICATION",
            message = event,
            module = TelemetryModule.NOTIFICATION,
            metadata = mapOf(
                "title" to title,
                "notificationId" to id
            )
        )
    }

    // 1. Create All Channels (Required for Android 8.0+)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Original Alerts Channel
            val alertChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts for class cancellations, attendance risks, and OTPs"
            }

            // New Downloads Channel
            val downloadChannel = NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifications for downloaded files and outpasses"
            }

            // New Exams Channel
            val examChannel = NotificationChannel(CHANNEL_EXAMS, "Exam Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Sticky notifications for exam seating"
            }

            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(downloadChannel)
            manager.createNotificationChannel(examChannel)
        }
    }

    // 2. Fire a Standard Notification
    fun showNotification(context: Context, title: String, message: String, notificationId: Int = 1) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with app's icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        telemetry(
            "STANDARD_POSTED",
            title,
            notificationId
        )
        notificationManager.notify(notificationId, builder.build())
    }

    // 3. Fire the Interactive OTP Notification
    fun showOtpNotification(context: Context) {
        val remoteInput = RemoteInput.Builder("KEY_OTP_REPLY")
            .setLabel("Enter VTOP OTP")
            .build()

        val replyIntent = Intent(context, OtpReceiver::class.java)

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            replyIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Enter OTP",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with your app's icon
            .setContentTitle("VTOP Sync Paused")
            .setContentText("VTOP requires an OTP to continue syncing your data.")
            .setColor(0xFF4ADE80.toInt())
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setAutoCancel(false)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        telemetry(
            "OTP_POSTED",
            "OTP",
            OTP_NOTIFICATION_ID
        )
        manager.notify(OTP_NOTIFICATION_ID, notification)
    }

    // 4. Safely dismiss a specific notification
    fun dismissNotification(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        Telemetry.log(
            level = TelemetryStatus.INFO,
            tag = "NOTIFICATION",
            message = "Notification dismissed",
            module = TelemetryModule.NOTIFICATION,
            metadata = mapOf(
                "notificationId" to id
            )
        )
        manager.cancel(id)
    }

    // 5. Fire Download Notification (Click to open file securely)
    fun showDownloadNotification(context: Context, file: File, title: String, description: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/pdf")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            file.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(file.name.hashCode(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    // Paste this inside NotificationHelper object
    fun showDownloadNotificationFromUri(context: Context, uri: Uri, fileName: String, title: String, description: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            // Crucial: Grants the PDF viewer permission to read this specific URI
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            uri.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(description)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            telemetry(
                "DOWNLOAD_POSTED",
                title,
                fileName.hashCode()
            )
            NotificationManagerCompat.from(context).notify(fileName.hashCode(), notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // 6. Fire Exam Seat Notification (Sticky until +30 mins)
    fun showExamSeatNotification(context: Context, title: String, message: String, examStartTimeMillis: Long) {
        val clearTimeMillis = examStartTimeMillis + (30 * 60 * 1000) // +30 mins
        val timeUntilClear = clearTimeMillis - System.currentTimeMillis()

        val builder = NotificationCompat.Builder(context, CHANNEL_EXAMS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (timeUntilClear > 0) {
            builder.setOngoing(true)
            builder.setTimeoutAfter(timeUntilClear)
        } else {
            builder.setOngoing(false)
        }

        try {
            telemetry(
                "EXAM_POSTED",
                title,
                EXAM_NOTIF_ID
            )
            NotificationManagerCompat.from(context).notify(EXAM_NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

object BatteryUtils {

    fun requestBatteryExemption(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    fun isExempt(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }
}