package com.vtop.core

import androidx.compose.runtime.mutableStateOf
import com.vtop.network.VtopClient
import kotlinx.coroutines.CompletableDeferred

object AppState {
    var isAppInForeground = false
    var pendingOtpDeferred: CompletableDeferred<String?>? = null
    var currentOtpResolver = mutableStateOf<VtopClient.OtpResolver?>(null)
    var appError = mutableStateOf<String?>(null)
    var syncStatus = mutableStateOf("IDLE")

    fun showError(message: String) { appError.value = message }
}