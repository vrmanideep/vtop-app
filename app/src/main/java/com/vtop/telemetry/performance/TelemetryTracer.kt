package com.vtop.telemetry.performance

import android.os.SystemClock
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

object TelemetryTracer {

    inline fun <T> trace(

        operation: String,

        module: TelemetryModule,

        block: () -> T

    ): T {

        val trace = TelemetryTrace(

            operation = operation,

            module = module,

            startTime = SystemClock.elapsedRealtime()
        )

        Telemetry.log(

            level = TelemetryStatus.DEBUG,

            tag = "TRACE",

            message = "$operation started",

            module = module
        )

        try {

            val result = block()

            trace.endTime = SystemClock.elapsedRealtime()

            Telemetry.log(

                level = TelemetryStatus.INFO,

                tag = "TRACE",

                message = "$operation completed",

                module = module,

                metadata = mapOf(

                    "durationMs" to trace.duration
                )
            )

            return result

        } catch (e: Exception) {

            trace.success = false

            trace.exception = e.javaClass.simpleName

            trace.endTime = SystemClock.elapsedRealtime()

            Telemetry.log(

                level = TelemetryStatus.ERROR,

                tag = "TRACE",

                message = "$operation failed",

                module = module,

                metadata = mapOf(

                    "durationMs" to trace.duration,

                    "exception" to e.javaClass.simpleName
                )
            )

            throw e
        }
    }
}