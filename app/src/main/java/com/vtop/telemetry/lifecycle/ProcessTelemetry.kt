package com.vtop.telemetry.lifecycle

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

/**
 * Collects process-wide lifecycle telemetry.
 */
object ProcessTelemetry :
    DefaultLifecycleObserver,
    ComponentCallbacks2 {

    fun init(
        application: Application
    ) {

        ProcessLifecycleOwner
            .get()
            .lifecycle
            .addObserver(this)

        application.registerComponentCallbacks(this)
    }

    override fun onStart(
        owner: LifecycleOwner
    ) {

        Telemetry.log(

            level = TelemetryStatus.INFO,

            tag = "PROCESS",

            message = "Application entered foreground",

            module = TelemetryModule.PROCESS,

            metadata = mapOf(

                "event" to "FOREGROUND"
            )
        )
    }

    override fun onStop(
        owner: LifecycleOwner
    ) {

        Telemetry.log(

            level = TelemetryStatus.INFO,

            tag = "PROCESS",

            message = "Application entered background",

            module = TelemetryModule.PROCESS,

            metadata = mapOf(

                "event" to "BACKGROUND"
            )
        )
    }

    @Deprecated("Android no longer recommends this callback.")
    override fun onLowMemory() {
        Telemetry.log(

            level = TelemetryStatus.WARNING,

            tag = "PROCESS",

            message = "System reported LOW_MEMORY",

            module = TelemetryModule.PROCESS,

            metadata = mapOf(

                "event" to "LOW_MEMORY"
            )
        )
    }

    override fun onTrimMemory(
        level: Int
    ) {

        Telemetry.log(

            level = TelemetryStatus.INFO,

            tag = "PROCESS",

            message = "onTrimMemory($level)",

            module = TelemetryModule.PROCESS,

            metadata = mapOf(

                "event" to "TRIM_MEMORY",

                "level" to level
            )
        )
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        // ignored
    }
}