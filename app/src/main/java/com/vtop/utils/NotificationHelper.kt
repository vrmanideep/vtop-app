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
import androidx.core.app.RemoteInput
import com.vtop.ui.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "vtop_alerts_channel"
    private const val CHANNEL_NAME = "VTOP Academic Alerts"

    // The fixed ID for the interactive OTP notification
    const val OTP_NOTIFICATION_ID = 888

    // 1. Create the Channel (Required for Android 8.0+)
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH // Makes it pop up on screen
            ).apply {
                description = "Alerts for class cancellations, attendance risks, and OTPs"
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with your app's icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Dismisses when tapped

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    // 3. Fire the Interactive OTP Notification
    fun showOtpNotification(context: Context) {
        // Create the RemoteInput (The text box that appears in the notification)
        val remoteInput = RemoteInput.Builder("KEY_OTP_REPLY")
            .setLabel("Enter VTOP OTP")
            .build()

        // Create the Intent that fires when the user hits "Send" on the keyboard
        val replyIntent = Intent(context, OtpReceiver::class.java)

        // FLAG_MUTABLE is strictly required here so Android can inject the typed text into the intent
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            replyIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Attach the text input to an Action button
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Enter OTP",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // Build and show the Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with your app's icon
            .setContentTitle("VTOP Sync Paused")
            .setContentText("VTOP requires an OTP to continue syncing your data.")
            .setColor(0xFF4ADE80.toInt()) // Standard green accent
            .addAction(replyAction)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 250, 250, 250)) // High priority vibration to wake the user
            .setAutoCancel(false) // Don't let them dismiss it just by tapping the body
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(OTP_NOTIFICATION_ID, notification)
    }

    // 4. Safely dismiss a specific notification (Used when the 3-minute timer expires)
    fun dismissNotification(context: Context, id: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(id)
    }
}

object BatteryUtils {

    fun requestBatteryExemption(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            // Check if we are already whitelisted
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                // If not, launch the Android system prompt asking the user to "Allow" it
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