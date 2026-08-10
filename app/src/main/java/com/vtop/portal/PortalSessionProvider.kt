package com.vtop.portal

import android.content.Context
import android.util.Log
import com.vtop.core.SessionManager
import com.vtop.core.SessionType
import com.vtop.network.VtopClient
import com.vtop.logic.GmailOtpExtractor
import com.vtop.utils.Vault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    ): Result<VtopClient> {
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
                                override fun onStatusUpdate(message: String) {}

                                override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                    Log.i(TAG, "OTP requested")

                                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                        val otpRequestedTime = System.currentTimeMillis()
                                        val googleEmail = Vault.getGoogleEmail(context)
                                        var autoExtractedOtp: String? = null

                                        if (googleEmail.isNotBlank()) {
                                            onStatusUpdate("Reading OTP from Gmail...")
                                            try {
                                                delay(3000)
                                                autoExtractedOtp = GmailOtpExtractor.getLatestVtopOtp(
                                                    context, googleEmail, otpRequestedTime
                                                )
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }

                                        if (!autoExtractedOtp.isNullOrBlank()) {
                                            Log.i(TAG, "OTP auto-filled")
                                            onStatusUpdate("OTP Auto-filled! Resuming...")
                                            resolver.submit(autoExtractedOtp)
                                        } else {
                                            Log.i(TAG, "Waiting for manual OTP")
                                            onOtpRequested(resolver)
                                        }
                                    }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
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
                    return Result.success(authenticatingClient)
                } else {
                    Log.w(TAG, "Portal login failed after all retries")
                    return Result.failure(Exception("Failed to bypass Captcha after 3 attempts or OTP cancelled. Please retry."))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Portal login exception", e)
                return Result.failure(e)
            }
        }
    }
}