package com.vtop.telemetry.collectors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus

/**
 * Collects Android system broadcasts.
 *
 * API 24+
 */
object BroadcastTelemetryCollector {

    private var registered = false

    fun init(context: Context) {
        Log.d("BROADCAST_TEST", "init() called")


        if (registered) return

        registered = true

        val filter = IntentFilter().apply {

            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)

            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)

            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)

            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)

            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)

            addAction(Intent.ACTION_LOCALE_CHANGED)

            addAction(Intent.ACTION_SHUTDOWN)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                addAction(Intent.ACTION_DEVICE_STORAGE_OK)
            }
        }

        context.registerReceiver(
            receiver,
            filter
        )
        Log.d("BROADCAST_TEST", "receiver registered")
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(
            context: Context,
            intent: Intent
        ) {

            val action = intent.action ?: return

            Telemetry.log(

                level = TelemetryStatus.INFO,

                tag = "BROADCAST",

                message = action,

                module = TelemetryModule.BROADCAST,

                metadata = buildMetadata(intent)
            )
        }
    }

    private fun buildMetadata(
        intent: Intent
    ): Map<String, Any?> {

        val extras = mutableMapOf<String, Any?>()

        intent.extras?.keySet()?.forEach { key ->

            try {
                extras[key] = intent.extras?.get(key)
            } catch (_: Exception) {
            }
        }

        return mapOf(

            "action" to intent.action,

            "extras" to extras
        )
    }
}