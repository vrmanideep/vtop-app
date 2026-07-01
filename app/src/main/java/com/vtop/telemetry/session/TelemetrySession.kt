package com.vtop.telemetry.session

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Represents a single application session.
 *
 * A new session is created every time the application starts.
 */
data class TelemetrySession(

    /**
     * Session ID.
     *
     * Example:
     * SESSION-20260626-221514-A8F3
     */
    val id: String,

    /**
     * Session start time.
     */
    val startedAt: Long
) {

    companion object {

        /**
         * Creates a brand new telemetry session.
         */
        fun newSession(): TelemetrySession {

            val timestamp = System.currentTimeMillis()

            val formatter = SimpleDateFormat(
                "yyyyMMdd-HHmmss",
                Locale.US
            )

            val random = UUID.randomUUID()
                .toString()
                .substring(0, 4)
                .uppercase(Locale.US)

            val sessionId =
                "SESSION-${formatter.format(Date(timestamp))}-$random"

            return TelemetrySession(
                id = sessionId,
                startedAt = timestamp
            )
        }
    }

    /**
     * Returns session uptime.
     */
    fun uptimeMillis(): Long {
        return System.currentTimeMillis() - startedAt
    }

    /**
     * Human-readable uptime.
     */
    fun uptimeString(): String {

        val seconds = uptimeMillis() / 1000

        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60

        return "%02d:%02d:%02d".format(
            hrs,
            mins,
            secs
        )
    }

    override fun toString(): String {
        return id
    }
}