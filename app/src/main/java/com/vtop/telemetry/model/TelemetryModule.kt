package com.vtop.telemetry.model

/**
 * Logical subsystem that generated a telemetry event.
 *
 * These are NOT Android components.
 * They represent the VTOP architecture.
 */
enum class TelemetryModule {

    // Application
    APP,

    // Android lifecycle
    ACTIVITY,
    PROCESS,

    // Synchronization
    SYNC,

    // Network
    NETWORK,

    // OTA
    OTA,

    // Updates
    UPDATE,

    // Notifications
    NOTIFICATION,

    // Broadcast receivers
    BROADCAST,

    // WorkManager
    WORK,

    // AlarmManager
    ALARM,

    // Authentication
    AUTH,

    // Database / Vault
    STORAGE,

    // Rendering / UI
    UI,

    // Performance
    PERFORMANCE,

    // Internal telemetry
    TELEMETRY,

    UNKNOWN
}