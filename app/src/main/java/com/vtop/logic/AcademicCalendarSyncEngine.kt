package com.vtop.logic

import android.content.Context
import android.util.Log
import com.vtop.models.AcademicCalendarEvent
import com.vtop.network.AdaptiveNetworkHelper
import com.vtop.network.VtopClient
import com.vtop.utils.Vault
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object AcademicCalendarSyncEngine {
    private const val TAG = "CalendarSyncEngine"

    /**
     * Executes an optimized sync of the Academic Calendar.
     * Skips network requests for months that have already passed if they exist in the local cache.
     *
     * @param context Application context
     * @param client Active VTOP Client
     * @param semId Semester ID to fetch (e.g., "AP2024251")
     * @param forceFullSync If true, ignores the cache and fetches all months
     * @param onProgress Callback to report (completedSteps, totalSteps) to the UI
     */
    suspend fun sync(
        context: Context,
        client: VtopClient,
        semId: String,
        forceFullSync: Boolean = false,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<AcademicCalendarEvent> {

        Log.i(TAG, "Starting Calendar Sync for $semId (Force: $forceFullSync)")

        // 1. Fetch available months from VTOP
        val monthsHtml = AdaptiveNetworkHelper.executeWithBackoff {
            client.fetchCalendarMonthsRawHtml(semId, "ALL")
        }
        val availableDates = AcademicCalendarParser.parseMonths(monthsHtml)

        if (availableDates.isEmpty()) {
            Log.w(TAG, "No valid calendar dates found.")
            return emptyList()
        }

        // 2. Load cached events to preserve past months
        val cachedEvents = Vault.getAcademicCalendar(context, semId)
        val currentYearMonth = YearMonth.now()
        val monthFormatter = java.time.format.DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM-yyyy")
            .toFormatter(Locale.ENGLISH)

        val eventsToKeep = mutableListOf<AcademicCalendarEvent>()
        val monthsToFetch = mutableListOf<String>()

        // 3. Smart-Cache Routing: Determine which months actually need network requests
        for (dateStr in availableDates) { // dateStr is formatted as "MMM-yyyy"
            if (forceFullSync) {
                monthsToFetch.add(dateStr)
                continue
            }

            try {
                val targetMonth = YearMonth.parse(dateStr, monthFormatter)
                val isPastMonth = targetMonth.isBefore(currentYearMonth)

                // VTOP events are stored as "dd-MMM-yyyy". We check if it ends with "MMM-yyyy".
                val monthEventsInCache = cachedEvents.filter {
                    it.date.endsWith(dateStr, ignoreCase = true)
                }

                if (isPastMonth && monthEventsInCache.isNotEmpty()) {
                    Log.d(TAG, "CACHE HIT: Skipping network fetch for past month $dateStr")
                    eventsToKeep.addAll(monthEventsInCache)
                } else {
                    monthsToFetch.add(dateStr)
                }
            } catch (e: DateTimeParseException) {
                Log.w(TAG, "Failed to parse month string: $dateStr. Defaulting to network fetch.")
                monthsToFetch.add(dateStr)
            }
        }

        Log.i(TAG, "Optimization Result: Fetching ${monthsToFetch.size} months out of ${availableDates.size} total.")

        // 4. Fetch necessary months with WAF backoff
        val fetchedEvents = mutableListOf<AcademicCalendarEvent>()
        var completedSteps = availableDates.size - monthsToFetch.size

        // Report initial progress for skipped cache-hits
        onProgress?.invoke(completedSteps, availableDates.size)

        for (dateStr in availableDates) {
            if (monthsToFetch.contains(dateStr)) {
                val html = AdaptiveNetworkHelper.executeWithBackoff {
                    client.fetchCalendarRawHtml(semId, dateStr, "ALL")
                }
                if (!html.isNullOrBlank()) {
                    fetchedEvents.addAll(CalendarParser.parseCalendarHtml(html))
                }
                completedSteps++
                onProgress?.invoke(completedSteps, availableDates.size)
            }
        }

        // 5. Merge, Sort, and Save
        val mergedEvents = (eventsToKeep + fetchedEvents).distinctBy { it.date + it.particulars }

        // Sort chronologically using java.time
        val dateFormatter = java.time.format.DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d-MMM-yyyy")
            .toFormatter(Locale.ENGLISH)

        val sortedEvents = mergedEvents.sortedBy { event ->
            try {
                // Same bulletproof regex for the engine cache sorter
                val safeDate = event.date.trim()
                    .uppercase(Locale.ENGLISH)
                    .replace(Regex("\\s+"), "")
                    .replace(Regex("-([A-Z]{3})[A-Z]*-"), "-$1-")

                java.time.LocalDate.parse(safeDate, dateFormatter).toEpochDay()
            } catch (e: Exception) {
                0L
            }
        }

        if (sortedEvents.isNotEmpty()) {
            Vault.saveAcademicCalendar(context, semId, sortedEvents)
        }

        Log.i(TAG, "Calendar Sync Complete. Saved ${sortedEvents.size} total events.")
        return sortedEvents
    }
}