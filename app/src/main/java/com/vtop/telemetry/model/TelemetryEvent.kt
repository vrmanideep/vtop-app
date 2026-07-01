package com.vtop.telemetry.model

/**
 * Represents a single telemetry event.
 *
 * One event = one line in the JSONL session file.
 */
data class TelemetryEvent(

    /**
     * Unique Event ID
     */
    val id: String,

    /**
     * Epoch millis.
     */
    val timestamp: Long,

    /**
     * Session this event belongs to.
     */
    val sessionId: String,

    /**
     * Log level.
     */
    val level: TelemetryStatus,

    /**
     * Original Android Log TAG.
     *
     * Example:
     *  GLOBAL_SYNC
     *  VTOP_CLIENT
     *  ATTENDANCE
     */
    val tag: String,

    /**
     * Actual log message.
     */
    val message: String,

    /**
     * Current thread.
     */
    val thread: String,

    /**
     * Android Process ID.
     */
    val pid: Int,

    /**
     * Optional structured metadata.
     *
     * Examples:
     *  semester = AP2025267
     *  duration = 421
     *  endpoint = /processViewCalendar
     */
    val metadata: Map<String, Any?> = emptyMap(),
    val module: TelemetryModule
)