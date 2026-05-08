package com.vtop.utils

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.vtop.ui.core.AppBridge

class OtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 1. Extract the text from the notification input
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val otpText = remoteInput?.getCharSequence("KEY_OTP_REPLY")?.toString()?.trim()

        if (!otpText.isNullOrBlank()) {
            // 2. Fulfill the deferred OTP! This instantly resumes the paused Worker/Syncer.
            AppBridge.pendingOtpDeferred?.complete(otpText)
            AppBridge.pendingOtpDeferred = null

            // 3. Dismiss the interactive notification
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NotificationHelper.OTP_NOTIFICATION_ID)
        }
    }
}