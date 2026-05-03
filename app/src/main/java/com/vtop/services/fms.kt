package com.vtop.services

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.vtop.utils.NotificationHelper

class FMS : FirebaseMessagingService() {

    private val TAG = "FCM_SERVICE"

    // 1. Fires when Firebase generates a new unique token for this specific phone.
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Print to Logcat so you can copy/paste it into the Firebase Console for testing
        Log.d(TAG, "New Token Generated: $token")

        // Save it locally just in case you want to display it in a "Dev Settings" UI later
        val sharedPrefs = getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("FCM_TOKEN", token).apply()
    }

    // 2. Fires when a message is received WHILE the app is in the foreground.
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check for hidden data payloads (if you send custom key-value pairs from the console)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }

        // Extract the text (Fallback to data payload if it's a silent push)
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "VTOP Update"
        val message = remoteMessage.notification?.body ?: remoteMessage.data["body"]

        // Force the local notification to display so the user sees it immediately
        if (!message.isNullOrEmpty()) {
            NotificationHelper.showNotification(
                context = this,
                title = title,
                message = message,
                notificationId = remoteMessage.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
            )
        }
    }
}