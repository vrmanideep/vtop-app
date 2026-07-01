package com.vtop.telemetry.model

/**
 * Represents the severity / outcome of a telemetry event.
 *
 * This is independent of Android's Log priorities.
 * Logcat levels are mapped into these values.
 */
enum class TelemetryStatus {

    /**
     * Verbose / Debug information.
     */
    DEBUG,

    /**
     * General information.
     */
    INFO,

    /**
     * Warning.
     */
    WARNING,

    /**
     * Error occurred.
     */
    ERROR,

    /**
     * Operation completed successfully.
     */
    SUCCESS,

    /**
     * Operation failed.
     */
    FAILURE
}