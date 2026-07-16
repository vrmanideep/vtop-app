package com.vtop.telemetry

import android.content.Context
import android.os.Process
import com.vtop.telemetry.collectors.RawLogEntry
import com.vtop.telemetry.collectors.TelemetryModuleClassifier
import com.vtop.telemetry.model.TelemetryEvent
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus
import com.vtop.telemetry.pipeline.TelemetryParser
import com.vtop.telemetry.session.TelemetrySession
import com.vtop.telemetry.writer.TelemetryWriter
import java.io.File
import java.util.UUID

object Telemetry {

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    lateinit var session: TelemetrySession
        private set

    lateinit var sessionFile: File
        private set

    private val prefs by lazy {
        appContext.getSharedPreferences(
            "TELEMETRY",
            Context.MODE_PRIVATE
        )
    }

    fun isEnabled(): Boolean =
        prefs.getBoolean("ENABLED", false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean("ENABLED", enabled)
            .apply()

        // Start session immediately if turned on during runtime
        if (enabled && !initialized) {
            startSession()
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext

        if (initialized) return

        // Start session at boot only if enabled
        if (isEnabled()) {
            startSession()
        }
    }

    // Extracted session initialization logic
    private fun startSession() {
        synchronized(this) {
            if (initialized) return

            session = TelemetrySession.newSession()

            val dir = File(appContext.filesDir, "telemetry")
            if (!dir.exists()) dir.mkdirs()

            sessionFile = File(dir, "${session.id}.jsonl")
            TelemetryWriter.init(sessionFile)

            initialized = true
        }
    }

    fun submit(event: TelemetryEvent) {
        if (!initialized || !isEnabled())
            return

        TelemetryWriter.enqueue(event)
    }

    fun log(
        level: TelemetryStatus,
        tag: String,
        message: String,
        module: TelemetryModule = TelemetryModule.UNKNOWN,
        metadata: Map<String, Any?> = emptyMap()
    ) {
        if (!initialized || !isEnabled())
            return

        submit(
            TelemetryEvent(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                sessionId = session.id,
                level = level,
                module = module,
                tag = tag,
                message = message,
                thread = Thread.currentThread().name,
                pid = Process.myPid(),
                metadata = metadata
            )
        )
    }

    fun receive(raw: RawLogEntry) {
        if (!initialized || !isEnabled())
            return

        val classified = raw.copy(
            module = TelemetryModuleClassifier.classify(raw.tag)
        )

        val event = TelemetryParser.parse(
            session,
            classified
        )

        TelemetryWriter.enqueue(event)
    }

    fun flush() {
        if (!initialized || !isEnabled())
            return

        TelemetryWriter.flush()
    }

    fun shutdown() {
        if (!initialized)
            return

        TelemetryWriter.shutdown()
    }

    fun currentSession() = session

    fun sessionFile() = sessionFile

    fun pendingEvents() = TelemetryWriter.pendingEvents()

    fun fileSize() = TelemetryWriter.fileSize()
}