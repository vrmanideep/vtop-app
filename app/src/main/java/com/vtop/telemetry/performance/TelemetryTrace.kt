package com.vtop.telemetry.performance

import com.vtop.telemetry.model.TelemetryModule

data class TelemetryTrace(

    val operation: String,

    val module: TelemetryModule,

    val startTime: Long,

    var endTime: Long = 0L,

    var success: Boolean = true,

    var exception: String? = null
) {

    val duration: Long
        get() = endTime - startTime
}