package com.vtop.telemetry.pipeline

import com.vtop.telemetry.model.TelemetryEvent
import com.vtop.telemetry.session.TelemetrySession
import com.vtop.telemetry.model.TelemetryStatus
import com.vtop.telemetry.collectors.RawLogEntry
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts RawLogEntry -> TelemetryEvent.
 *
 * This is the ONLY place where raw Logcat entries become
 * structured telemetry.
 */
object TelemetryParser {

    /**
     * Sequential event IDs.
     *
     * Every session starts from EVT-000001.
     */
    private val counter = AtomicLong(1)

    /**
     * Resets event numbering.
     *
     * Called whenever a new telemetry session begins.
     */
    fun reset() {
        counter.set(1)
    }

    /**
     * Parses a RawLogEntry into a TelemetryEvent.
     */
    fun parse(
        session: TelemetrySession,
        raw: RawLogEntry
    ): TelemetryEvent {

        return TelemetryEvent(

            id = nextEventId(),

            timestamp = raw.timestamp,

            sessionId = session.id,

            level = when (raw.priority) {

                'V' -> TelemetryStatus.DEBUG

                'D' -> TelemetryStatus.DEBUG

                'I' -> TelemetryStatus.INFO

                'W' -> TelemetryStatus.WARNING

                'E' -> TelemetryStatus.ERROR

                'A' -> TelemetryStatus.FAILURE

                else -> TelemetryStatus.INFO
            },
            module = raw.module,

            tag = raw.tag,

            message = raw.message,

            thread = raw.tid.toString(),

            pid = raw.pid,

            metadata = buildMetadata(raw)
        )
    }

    /**
     * Generates:
     *
     * EVT-000001
     * EVT-000002
     * ...
     */
    private fun nextEventId(): String {

        return "EVT-%06d".format(
            counter.getAndIncrement()
        )
    }

    /**
     * Creates structured metadata from
     * the raw Logcat entry.
     *
     * Later we can enrich this with:
     * - regex extraction
     * - endpoint detection
     * - durations
     * - semester IDs
     * etc.
     */
    private fun buildMetadata(
        raw: RawLogEntry
    ): Map<String, Any?> {

        return mutableMapOf<String, Any?>().apply {

            put("priority", raw.priority.toString())

            put("pid", raw.pid)

            put("tid", raw.tid)

            put("tag", raw.tag)
        }
    }
}