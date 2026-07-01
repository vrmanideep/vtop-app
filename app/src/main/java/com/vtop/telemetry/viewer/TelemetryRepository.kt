package com.vtop.telemetry.viewer

import android.content.Context
import com.google.gson.Gson
import com.vtop.telemetry.model.TelemetryEvent
import java.io.File

/**
 * Reads telemetry sessions stored as JSONL.
 */
class TelemetryRepository(
    private val context: Context
) {

    private val gson = Gson()

    private val telemetryDir: File
        get() = File(
            context.filesDir,
            "telemetry"
        )

    /**
     * Returns all sessions ordered newest first.
     */
    fun getSessions(): List<TelemetrySessionInfo> {

        if (!telemetryDir.exists())
            return emptyList()

        return telemetryDir
            .listFiles()
            ?.filter {

                it.isFile &&
                        it.extension == "jsonl"

            }
            ?.map { file ->

                TelemetrySessionInfo(

                    sessionId =
                        file.nameWithoutExtension,

                    file = file,

                    size = file.length(),

                    lastModified = file.lastModified(),

                    eventCount =
                        countEvents(file)
                )

            }
            ?.sortedWith(
                TelemetrySessionInfo.NEWEST_FIRST
            )
            ?: emptyList()
    }

    /**
     * Reads one session.
     */
    fun loadSession(
        session: TelemetrySessionInfo
    ): List<TelemetryEvent> {

        return loadSession(
            session.file
        )
    }

    /**
     * Reads one session.
     */
    fun loadSession(
        file: File
    ): List<TelemetryEvent> {

        if (!file.exists())
            return emptyList()

        val events =
            mutableListOf<TelemetryEvent>()

        file.useLines { lines ->

            lines.forEach { line ->

                if (line.isBlank())
                    return@forEach

                try {

                    val event =
                        gson.fromJson(
                            line,
                            TelemetryEvent::class.java
                        )

                    events += event

                } catch (_: Exception) {
                }
            }
        }

        return events.sortedBy {

            it.timestamp

        }
    }

    /**
     * Deletes one session.
     */
    fun deleteSession(
        session: TelemetrySessionInfo
    ): Boolean {

        return session.file.delete()
    }

    /**
     * Deletes all sessions.
     */
    fun clear() {

        telemetryDir
            .listFiles()
            ?.forEach {

                it.delete()
            }
    }

    /**
     * Total storage used.
     */
    fun totalSize(): Long {

        return telemetryDir
            .listFiles()
            ?.sumOf {

                it.length()

            }
            ?: 0L
    }

    /**
     * Counts JSON lines.
     */
    private fun countEvents(
        file: File
    ): Int {

        return try {

            file.useLines {

                it.count()
            }

        } catch (_: Exception) {

            0
        }
    }

    /**
     * Latest session.
     */
    fun latestSession(): TelemetrySessionInfo? {

        return getSessions()
            .firstOrNull()
    }

    /**
     * Statistics.
     */
    fun statistics(
        session: TelemetrySessionInfo
    ): TelemetryStatistics {

        return TelemetryStatistics.from(

            loadSession(session)
        )
    }
}