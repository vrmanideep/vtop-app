package com.vtop.logic

import android.accounts.Account
import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object GmailOtpExtractor {

    private const val TAG = "GMAIL_EXTRACTOR"

    suspend fun getLatestVtopOtp(context: Context, emailAddress: String, otpRequestedTime: Long): String? = withContext(Dispatchers.IO) {
        try {
            val account = Account(emailAddress, "com.google")
            val scope = "oauth2:https://www.googleapis.com/auth/gmail.readonly"
            val accessToken = GoogleAuthUtil.getToken(context, account, scope)

            // We only care about emails received slightly before or after VTOP requested it.
            // 5-second buffer accounts for minor clock drift between the device and Google's servers.
            val validTimestampThreshold = otpRequestedTime - 5_000
            Log.d(TAG, "OTP Requested Time: $otpRequestedTime | Acceptable Threshold: > $validTimestampThreshold")

            // Increased to 10 attempts, 3 seconds apart (30 seconds total max wait)
            for (attempt in 1..10) {
                Log.d(TAG, "Attempt $attempt to fetch new OTP...")

                val searchUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=from:noreply.sdc@vitap.ac.in subject:OTP&maxResults=1")
                val searchConn = searchUrl.openConnection() as HttpURLConnection
                searchConn.setRequestProperty("Authorization", "Bearer $accessToken")
                searchConn.requestMethod = "GET"

                if (searchConn.responseCode == 200) {
                    val searchReader = BufferedReader(InputStreamReader(searchConn.inputStream))
                    val searchResponse = searchReader.readText()
                    searchReader.close()

                    val searchJson = JSONObject(searchResponse)

                    if (searchJson.has("messages")) {
                        val messagesArray = searchJson.getJSONArray("messages")
                        if (messagesArray.length() > 0) {
                            val messageId = messagesArray.getJSONObject(0).getString("id")

                            val msgUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=full")
                            val msgConn = msgUrl.openConnection() as HttpURLConnection
                            msgConn.setRequestProperty("Authorization", "Bearer $accessToken")
                            msgConn.requestMethod = "GET"

                            if (msgConn.responseCode == 200) {
                                val msgReader = BufferedReader(InputStreamReader(msgConn.inputStream))
                                val msgResponse = msgReader.readText()
                                msgReader.close()

                                val msgJson = JSONObject(msgResponse)

                                // Grab the exact millisecond timestamp the email arrived at Google
                                val internalDateStr = msgJson.optString("internalDate", "0")
                                val internalDate = internalDateStr.toLongOrNull() ?: 0L

                                Log.d(TAG, "Found email with internalDate: $internalDate")

                                // TIMESTMAP VALIDATION CHECK
                                if (internalDate < validTimestampThreshold) {
                                    Log.d(TAG, "Email rejected: Too old (Diff: ${validTimestampThreshold - internalDate}ms). Waiting for new arrival...")
                                } else {
                                    Log.d(TAG, "Email accepted: Timestamp is valid!")
                                    val payload = msgJson.getJSONObject("payload")
                                    val base64Body = extractBodySafely(payload)

                                    if (base64Body != null) {
                                        val decodedBytes = Base64.decode(base64Body, Base64.URL_SAFE)
                                        val emailText = String(decodedBytes, Charsets.UTF_8)

                                        val pattern = Pattern.compile("\\b\\d{6}\\b")
                                        val matcher = pattern.matcher(emailText)

                                        if (matcher.find()) {
                                            val otp = matcher.group(0)
                                            Log.d(TAG, "Successfully extracted fresh OTP: $otp")
                                            return@withContext otp
                                        } else {
                                            Log.e(TAG, "Regex failed to find a 6-digit OTP in the email body.")
                                        }
                                    } else {
                                        Log.e(TAG, "Failed to safely extract base64 body from email payload.")
                                    }
                                }
                            } else {
                                Log.e(TAG, "Failed to fetch message details. HTTP Code: ${msgConn.responseCode}")
                            }
                        } else {
                            Log.d(TAG, "Search query returned 0 messages.")
                        }
                    } else {
                        Log.d(TAG, "JSON response does not contain 'messages' array.")
                    }
                } else {
                    Log.e(TAG, "Failed to search inbox. HTTP Code: ${searchConn.responseCode}")
                }

                // Wait 3 seconds before next attempt
                if (attempt < 10) delay(3000)
            }

            Log.w(TAG, "Failed to find a fresh OTP after 30 seconds.")
            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "Critical error during Gmail OTP extraction", e)
            return@withContext null
        }
    }

    private fun extractBodySafely(payload: JSONObject): String? {
        if (payload.has("body") && payload.getJSONObject("body").has("data")) {
            return payload.getJSONObject("body").getString("data")
        }

        if (payload.has("parts")) {
            val parts = payload.getJSONArray("parts")
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val mimeType = part.optString("mimeType", "")

                if (mimeType == "text/plain" || mimeType == "text/html") {
                    if (part.has("body") && part.getJSONObject("body").has("data")) {
                        return part.getJSONObject("body").getString("data")
                    }
                }

                if (part.has("parts")) {
                    val nestedBody = extractBodySafely(part)
                    if (nestedBody != null) return nestedBody
                }
            }
        }
        return null
    }
}