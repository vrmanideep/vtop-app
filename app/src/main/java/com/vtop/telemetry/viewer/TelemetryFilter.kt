package com.vtop.telemetry.viewer

import com.vtop.telemetry.model.TelemetryEvent
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

/**
 * Filtering options for the telemetry viewer.
 */
data class TelemetryFilter(

    val module: TelemetryModule? = null,

    val level: TelemetryStatus? = null,

    val tag: String? = null,

    val thread: String? = null,

    val pid: Int? = null
) {

    fun apply(
        events: List<TelemetryEvent>
    ): List<TelemetryEvent> {

        return events.filter { event ->

            (module == null || event.module == module) &&

                    (level == null || event.level == level) &&

                    (tag.isNullOrBlank() ||
                            event.tag.contains(
                                tag,
                                ignoreCase = true
                            )) &&

                    (thread.isNullOrBlank() ||
                            event.thread.contains(
                                thread,
                                ignoreCase = true
                            )) &&

                    (pid == null || event.pid == pid)
        }
    }

    companion object {

        val NONE = TelemetryFilter()
    }
}