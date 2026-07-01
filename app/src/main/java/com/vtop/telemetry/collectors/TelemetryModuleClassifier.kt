package com.vtop.telemetry.collectors

import com.vtop.telemetry.model.TelemetryModule

/**
 * Maps Logcat tags into logical telemetry modules.
 *
 * Every event passes through this classifier before entering
 * the telemetry pipeline.
 */
object TelemetryModuleClassifier {

    fun classify(tag: String): TelemetryModule {

        return when (tag.uppercase()) {

            // --------------------------
            // Synchronization
            // --------------------------
            "GLOBAL_SYNC",
            "VTOP_WORKER" ->
                TelemetryModule.SYNC

            // --------------------------
            // Authentication
            // --------------------------
            "OTP_FORM",
            "VTOP_LOGIN",
            "GMAIL_EXTRACTOR" ->
                TelemetryModule.AUTH

            // --------------------------
            // Updates
            // --------------------------
            "UPDATE_MANAGER" ->
                TelemetryModule.UPDATE

            "OTA_MANAGER" ->
                TelemetryModule.OTA

            // --------------------------
            // Parsing
            // --------------------------
            "MARKS_PARSER",
            "GRADE-HISTORY_PARSER",
            "EXAM_PARSER" ->
                TelemetryModule.TELEMETRY

            "BOOT_RECEIVER" ->
                TelemetryModule.BROADCAST

            "EXAM_QUEUE" ->
                TelemetryModule.NOTIFICATION

            "ACTIVITY",
            "LIFECYCLE" ->
                TelemetryModule.ACTIVITY

            "PROCESS" ->
                TelemetryModule.PROCESS

            "CONNECTIVITY",
            "HTTP" ->
                TelemetryModule.NETWORK

            // --------------------------
            // Firebase
            // --------------------------
            "FCM_SERVICE" ->
                TelemetryModule.NOTIFICATION

            // --------------------------
            // UI
            // --------------------------
            "HOLIDAY_ERR" ->
                TelemetryModule.UI



            // --------------------------
            // Telemetry
            // --------------------------
            "TELEMETRYREADER",
            "APP_LOGGER" ->
                TelemetryModule.TELEMETRY



            // --------------------------
            // Unknown
            // --------------------------
            else ->
                TelemetryModule.UNKNOWN
        }
    }

    fun isKnownTag(tag: String): Boolean {
        return classify(tag) != TelemetryModule.UNKNOWN
    }
}