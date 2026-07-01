package com.vtop.telemetry.collectors

import android.content.Context
import android.os.Process
import android.util.Log
import com.vtop.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Reads this application's Logcat output and forwards it
 * into the Telemetry pipeline.
 *
 * Existing Log.d/i/w/e calls require NO modification.
 */
object LogcatTelemetryReader {

    private const val TAG = "TelemetryReader"

    private val scope =
        CoroutineScope(
            Dispatchers.IO +
                    SupervisorJob()
        )

    @Volatile
    private var running = false

    /**
     * threadtime format:
     *
     * 06-26 18:42:11.183  4123  4155 D GLOBAL_SYNC: Calendar Sync Started
     */
    private val regex = Regex(
        """^(\d\d-\d\d)\s+(\d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEAF])\s+(.+?):\s+(.*)$"""
    )

    /**
     * Starts listening to Logcat.
     */
    fun start(context: Context) {
        Log.d("LOGCAT_READER", "Started")

        if (running) return

        running = true

        scope.launch {

            try {

                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        "/system/bin/logcat",
                        "-v",
                        "threadtime"
                    )

                )


                val reader = BufferedReader(
                    InputStreamReader(process.inputStream)
                )

                while (running) {
                    val line = reader.readLine() ?: break
                    parseLine(line)
                }

                Log.e("LOGCAT_READER", "Logcat process ended")

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Failed to attach Logcat: ${e.message}"
                )
            }
        }
    }

    /**
     * Stops listening.
     */
    fun stop() {

        running = false

        scope.cancel()
    }

    /**
     * Parses one Logcat line.
     */
    private fun parseLine(
        line: String
    ) {

        val match =
            regex.find(line)
                ?: return

        val groups =
            match.groupValues

        try {

            val timestamp =
                parseTimestamp(
                    groups[1],
                    groups[2]
                )

            val pid = groups[3].toInt()
            if (pid != Process.myPid()) {
                return
            }

            val tid =
                groups[4].toInt()

            val priority =
                groups[5].first()

            val tag =
                groups[6].trim()

            if (!TelemetryModuleClassifier.isKnownTag(tag)) {
                return
            }

            val message =
                groups[7]

            val module = TelemetryModuleClassifier.classify(tag)

            val raw = RawLogEntry(
                timestamp = timestamp,
                priority = priority,
                pid = pid,
                tid = tid,
                tag = tag,
                module = module,
                message = message
            )

            Telemetry.receive(raw)

        } catch (_: Exception) {

            // Ignore malformed lines.
        }
    }

    /**
     * Converts:
     *
     * 06-26
     * 18:42:11.183
     *
     * into epoch millis.
     */
    private fun parseTimestamp(
        date: String,
        time: String
    ): Long {

        return try {

            val year =
                Calendar.getInstance()
                    .get(Calendar.YEAR)

            val formatter =
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.US
                )

            formatter.parse(
                "$year-$date $time"
            )!!.time

        } catch (_: Exception) {

            System.currentTimeMillis()
        }
    }
}