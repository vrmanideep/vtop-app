package com.vtop.telemetry.viewer

import com.vtop.telemetry.model.TelemetryEvent

/**
 * Full-text search for telemetry events.
 */
object TelemetrySearch {

    fun search(
        events: List<TelemetryEvent>,
        query: String
    ): List<TelemetryEvent> {

        val q = query.trim()

        if (q.isEmpty())
            return events

        val lower = q.lowercase()

        return events.filter { event ->

            event.message.lowercase().contains(lower) ||

                    event.tag.lowercase().contains(lower) ||

                    event.module.name.lowercase().contains(lower) ||

                    event.level.name.lowercase().contains(lower) ||

                    event.thread.lowercase().contains(lower) ||

                    event.pid.toString().contains(lower) ||

                    event.metadata.any { (key, value) ->

                        key.lowercase().contains(lower) ||

                                value?.toString()
                                    ?.lowercase()
                                    ?.contains(lower) == true
                    }
        }
    }

    fun filterAndSearch(
        events: List<TelemetryEvent>,
        filter: TelemetryFilter,
        query: String
    ): List<TelemetryEvent> {

        return search(
            filter.apply(events),
            query
        )
    }
}