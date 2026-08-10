package com.vtop.utils

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.vtop.core.AppState

class OtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val otpText = remoteInput?.getCharSequence("KEY_OTP_REPLY")?.toString()?.trim()

        if (!otpText.isNullOrBlank()) {
            AppState.pendingOtpDeferred?.complete(otpText)
            AppState.pendingOtpDeferred = null

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NotificationHelper.OTP_NOTIFICATION_ID)
        }
    }
}