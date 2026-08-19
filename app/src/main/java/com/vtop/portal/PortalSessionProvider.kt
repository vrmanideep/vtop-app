package com.vtop.portal

import android.content.Context
import android.util.Log
import com.vtop.core.SessionManager
import com.vtop.core.SessionType
import com.vtop.network.VtopClient
import com.vtop.logic.GmailOtpExtractor
import com.vtop.utils.Vault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object PortalSessionProvider {
    private const val TAG = "PORTAL_SESSION"
    private val loginMutex = Mutex()

    private fun getOrCreatePortalClient(context: Context): VtopClient {
        SessionManager.getPortalClient()?.let { return it }
        Log.i(TAG, "Creating Portal session")
        val (client, _) = SessionManager.createClient(context, SessionType.PORTAL)
        return client
    }

    suspend fun getOrCreateSession(
        context: Context,
        isParallel: Boolean,
        fallbackClient: VtopClient,
        onStatusUpdate: suspend (String) -> Unit,
        onOtpRequested: suspend (VtopClient.OtpResolver) -> Unit
    ): Result<VtopClient> = withContext(Dispatchers.IO) { // <-- MOVED ENTIRE FLOW TO IO THREAD
        loginMutex.withLock {
            try {
                val authenticatingClient = if (isParallel) {
                    Log.i(TAG, "Using Portal session")
                    getOrCreatePortalClient(context)
                } else {
                    Log.i(TAG, "Using Sync session")
                    fallbackClient
                }

                var loginSuccess = false
                var attempts = 0
                val maxRetries = 3

                while (attempts < maxRetries && !loginSuccess) {
                    Log.i(TAG, "Login attempt ${attempts + 1}/$maxRetries")

                    try {
                        loginSuccess = authenticatingClient.autoLogin(
                            context,
                            object : VtopClient.LoginListener {
                                override fun onStatusUpdate(message: String) {
                                    // Safely bounce status updates back to the UI thread
                                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                                        onStatusUpdate(message)
                                    }
                                }

                                override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val otpRequestedTime = System.currentTimeMillis()
                                        val savedEmail = Vault.getGoogleEmail(context)

                                        if (savedEmail.isNotBlank()) {
                                            withContext(Dispatchers.Main) { onStatusUpdate("Fetching OTP from Gmail...") }

                                            var extractedOtp: String? = null
                                            // Polling loop: Check every 3 seconds, up to 6 times (18s total)
                                            for (i in 1..6) {
                                                kotlinx.coroutines.delay(3000)
                                                extractedOtp = GmailOtpExtractor.getLatestVtopOtp(context, savedEmail, otpRequestedTime)
                                                if (extractedOtp != null) break
                                            }

                                            if (extractedOtp != null) {
                                                withContext(Dispatchers.Main) { onStatusUpdate("Verifying OTP...") }
                                                resolver.submit(extractedOtp)
                                                return@launch
                                            }
                                        }

                                        withContext(Dispatchers.Main) { onStatusUpdate("Awaiting manual OTP...") }
                                        onOtpRequested(resolver)
                                    }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e(TAG, "Login attempt failed", e)
                        loginSuccess = false
                    }

                    if (!loginSuccess) {
                        attempts++
                        if (attempts < maxRetries) {
                            Log.i(TAG, "Retrying Portal login (${attempts + 1}/$maxRetries)")
                            authenticatingClient.reinitializeSession(context)
                        }
                    }
                }

                if (loginSuccess) {
                    Log.i(TAG, "Login successful")
                    if (isParallel) {
                        Log.i(TAG, "Registering Portal session")
                        SessionManager.setPortalClient(authenticatingClient)
                    }
                    return@withContext Result.success(authenticatingClient)
                } else {
                    Log.w(TAG, "Portal login failed after all retries")
                    return@withContext Result.failure(Exception("Failed to bypass Captcha after 3 attempts or OTP cancelled. Please retry."))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Portal login exception", e)
                return@withContext Result.failure(e)
            }
        }
    }
}