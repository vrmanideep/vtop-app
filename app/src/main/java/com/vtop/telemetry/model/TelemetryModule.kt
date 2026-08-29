package com.vtop.telemetry.model

/**
 * Logical subsystem that generated a telemetry event.
 *
 * These are NOT Android components.
 * They represent the VTOP app architecture.
 */
enum class TelemetryModule {
    APP, ACTIVITY, BROADCAST, PROCESS, SYNC, NETWORK, OTA, UPDATE,
    NOTIFICATION, GMAIL_EXTRACTOR, WORK, ALARM, AUTH, UI,
    PARSER, PERFORMACE, TELEMETRY, WKND_OUTING_ROWS, UNKNOWN,
    SESSION, CALENDAR_SYNC, PORTAL, FCM, EXAM_QUEUE
}