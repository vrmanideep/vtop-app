package com.vtop.telemetry.viewer

import java.io.File

/**
 * Represents one telemetry session stored on disk.
 */
data class TelemetrySessionInfo(

    /**
     * SESSION-20260628-152819-C4A6
     */
    val sessionId: String,

    /**
     * JSONL file.
     */
    val file: File,

    /**
     * File size in bytes.
     */
    val size: Long,

    /**
     * Last modified timestamp.
     */
    val lastModified: Long,

    /**
     * Number of events contained in the session.
     */
    val eventCount: Int
) {

    /**
     * Human-readable size.
     */
    val readableSize: String
        get() {

            val kb = size / 1024.0

            if (kb < 1024)
                return String.format("%.1f KB", kb)

            return String.format("%.2f MB", kb / 1024.0)
        }

    /**
     * Sort newest first.
     */
    companion object {

        val NEWEST_FIRST =
            compareByDescending<TelemetrySessionInfo> {

                it.lastModified
            }
    }
}