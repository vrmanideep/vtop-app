package com.vtop.telemetry.lifecycle

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

/**
 * Collects Activity lifecycle telemetry.
 */
class ActivityTelemetry : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) = record(activity, "CREATED")

    override fun onActivityStarted(
        activity: Activity
    ) = record(activity, "STARTED")

    override fun onActivityResumed(
        activity: Activity
    ) = record(activity, "RESUMED")

    override fun onActivityPaused(
        activity: Activity
    ) = record(activity, "PAUSED")

    override fun onActivityStopped(
        activity: Activity
    ) = record(activity, "STOPPED")

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) = record(activity, "SAVE_INSTANCE_STATE")

    override fun onActivityDestroyed(
        activity: Activity
    ) = record(activity, "DESTROYED")

    private fun record(
        activity: Activity,
        event: String
    ) {

        Telemetry.log(

            level = TelemetryStatus.INFO,

            tag = "ACTIVITY",

            message = "${activity.javaClass.simpleName} $event",

            module = TelemetryModule.ACTIVITY,

            metadata = mapOf(

                "activity" to activity.javaClass.simpleName,

                "event" to event
            )
        )
    }
}