package com.vtop.telemetry.viewer

import com.vtop.telemetry.model.TelemetryEvent
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

/**
 * Statistics for one telemetry session.
 */
data class TelemetryStatistics(

    val totalEvents: Int,

    val debugCount: Int,

    val infoCount: Int,

    val successCount: Int,

    val warningCount: Int,

    val errorCount: Int,

    val moduleCounts: Map<TelemetryModule, Int>,

    val firstTimestamp: Long?,

    val lastTimestamp: Long?
) {

    val durationMillis: Long
        get() =
            if (firstTimestamp != null && lastTimestamp != null)
                lastTimestamp - firstTimestamp
            else
                0L

    companion object {

        fun from(
            events: List<TelemetryEvent>
        ): TelemetryStatistics {

            val moduleMap =
                mutableMapOf<TelemetryModule, Int>()

            events.forEach {

                moduleMap[it.module] =
                    (moduleMap[it.module] ?: 0) + 1
            }

            return TelemetryStatistics(

                totalEvents = events.size,

                debugCount =
                    events.count {
                        it.level == TelemetryStatus.DEBUG
                    },

                infoCount =
                    events.count {
                        it.level == TelemetryStatus.INFO
                    },

                successCount =
                    events.count {
                        it.level == TelemetryStatus.SUCCESS
                    },

                warningCount =
                    events.count {
                        it.level == TelemetryStatus.WARNING
                    },

                errorCount =
                    events.count {
                        it.level == TelemetryStatus.ERROR
                    },

                moduleCounts = moduleMap,

                firstTimestamp =
                    events.minOfOrNull {
                        it.timestamp
                    },

                lastTimestamp =
                    events.maxOfOrNull {
                        it.timestamp
                    }
            )
        }
    }
}