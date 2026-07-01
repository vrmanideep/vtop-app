package com.vtop.telemetry.writer

import com.google.gson.Gson
import com.vtop.telemetry.model.TelemetryEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Handles buffered writing of telemetry events.
 *
 * Events are accumulated in memory and periodically flushed to disk
 * as JSON Lines (.jsonl).
 */
object TelemetryWriter {

    private val gson = Gson()

    private val buffer =
        ConcurrentLinkedQueue<TelemetryEvent>()

    private val scope =
        CoroutineScope(
            Dispatchers.IO +
                    SupervisorJob()
        )

    @Volatile
    private var initialized = false

    private lateinit var outputFile: File

    /**
     * Initializes the writer.
     *
     * Starts the periodic flush task.
     */
    fun init(file: File) {

        if (initialized) return

        synchronized(this) {

            if (initialized) return

            outputFile = file

            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }

            initialized = true

            startFlushLoop()
        }
    }

    /**
     * Adds an event to the in-memory queue.
     */
    fun enqueue(
        event: TelemetryEvent
    ) {

        if (!initialized) return

        buffer.add(event)
    }

    /**
     * Background flush every 3 seconds.
     */
    private fun startFlushLoop() {

        scope.launch {

            while (isActive) {

                delay(3000)

                flush()
            }
        }
    }

    /**
     * Immediately writes all queued events to disk.
     */
    fun flush() {

        if (!initialized) return

        if (buffer.isEmpty())
            return

        try {

            val builder = StringBuilder()

            while (true) {

                val event =
                    buffer.poll()
                        ?: break

                builder.append(
                    gson.toJson(event)
                )

                builder.append('\n')
            }

            outputFile.appendText(
                builder.toString()
            )

        } catch (_: Exception) {
            // Never crash because telemetry failed.
        }
    }

    /**
     * Stops background writer.
     *
     * Should be called when app exits.
     */
    fun shutdown() {

        flush()

        scope.cancel()
    }

    /**
     * Number of queued events.
     */
    fun pendingEvents(): Int {

        return buffer.size
    }

    /**
     * Current telemetry file.
     */
    fun getFile(): File {

        return outputFile
    }

    /**
     * Current telemetry file size.
     */
    fun fileSize(): Long {

        return if (::outputFile.isInitialized)
            outputFile.length()
        else
            0L
    }
}