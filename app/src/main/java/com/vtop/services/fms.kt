package com.vtop.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vtop.ui.MainActivity
import com.vtop.utils.NotificationHelper

class FMS : FirebaseMessagingService() {

    private val TAG = "FCM_SERVICE"

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        Log.d(TAG, "New Token Generated: $token")
        val sharedPrefs = getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("FCM_TOKEN", token).apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }

        val type = remoteMessage.data["type"]
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "VTOP Update"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["body"]

        if (message.isNullOrEmpty()) return

        // Route the notification based on the "type" tag
        if (type == "APP_UPDATE") {
            showUpdateNotification(title, message)
        } else {
            // Standard fallback for all other general notifications
            NotificationHelper.showNotification(
                context = this,
                title = title,
                message = message,
                notificationId = remoteMessage.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
            )
        }
    }

    private fun showUpdateNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "SHOW_UPDATE"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "vtop_updates_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "App Updates",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Note: Replace ic_popup_sync with your app's actual icon if you have one
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(9999, notificationBuilder.build())
    }
}