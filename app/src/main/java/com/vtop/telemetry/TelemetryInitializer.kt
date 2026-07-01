package com.vtop.telemetry

import android.app.Application
import com.vtop.telemetry.collectors.*
import com.vtop.telemetry.lifecycle.ActivityTelemetry
import com.vtop.telemetry.lifecycle.ProcessTelemetry

class TelemetryInitializer : Application() {

    override fun onCreate() {
        super.onCreate()

        Telemetry.init(this)

        registerCollectors()
    }

    private fun registerCollectors() {

        LogcatTelemetryReader.start(this)

        ConnectivityCollector.init(this)

        BroadcastTelemetryCollector.init(this)

        registerActivityLifecycleCallbacks(
            ActivityTelemetry()
        )

        ProcessTelemetry.init(this)
    }
}