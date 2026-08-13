package com.vtop.network

import android.util.Log
import kotlinx.coroutines.delay
import java.io.IOException

object AdaptiveNetworkHelper {
    private const val TAG = "AdaptiveNetwork"
    private const val BASE_DELAY_MS = 250L
    private const val MAX_DELAY_MS = 3000L
    private const val MAX_RETRIES = 3

    /**
     * Executes a network block with exponential backoff on failure.
     * Prevents WAF rate-limiting from crashing the sync loops.
     */
    suspend fun <T> executeWithBackoff(block: suspend () -> T): T {
        var currentDelay = BASE_DELAY_MS
        var attempt = 0

        while (true) {
            try {
                val result = block()
                // If successful, pause slightly to respect server limits before next call
                delay(BASE_DELAY_MS)
                return result
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_RETRIES || e !is IOException) {
                    Log.e(TAG, "Network request failed permanently after $attempt attempts.", e)
                    throw e
                }

                Log.w(TAG, "Rate limit or network error hit. Backing off for ${currentDelay}ms. Attempt $attempt of $MAX_RETRIES")
                delay(currentDelay)

                // Exponential backoff
                currentDelay = (currentDelay * 2).coerceAtMost(MAX_DELAY_MS)
            }
        }
    }
}