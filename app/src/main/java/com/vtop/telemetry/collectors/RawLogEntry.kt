package com.vtop.telemetry.collectors

import com.vtop.telemetry.model.TelemetryModule

/**
 * Raw log entry captured from Logcat before it is transformed
 * into a TelemetryEvent.
 */
data class RawLogEntry(

    /**
     * Epoch timestamp in milliseconds.
     */
    val timestamp: Long,

    /**
     * Android log priority.
     *
     * V, D, I, W, E, A
     */
    val priority: Char,

    /**
     * Process ID.
     */
    val pid: Int,

    /**
     * Thread ID.
     */
    val tid: Int,

    /**
     * Android Logcat tag.
     */
    val tag: String,

    /**
     * Logical VTOP module.
     */
    val module: TelemetryModule,

    /**
     * Log message.
     */
    val message: String
)