@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UseKtx", "RedundantSamConstructor")

package com.vtop.ui.core

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.glance.appwidget.updateAll
import com.vtop.widget.NextClassWidget
import androidx.compose.runtime.mutableStateOf
import com.vtop.core.SessionManager
import com.vtop.network.VtopClient
import com.vtop.network.VtopException
import com.vtop.utils.*
import com.vtop.logic.*
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.performance.TelemetryTracer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object GlobalSyncer {
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isSyncing = mutableStateOf(false)
    private const val MAX_RETRY = 3
    private const val TAG = "GLOBAL_SYNC"
    @Volatile private var activeSyncJob: kotlinx.coroutines.Job? = null

    fun cancelActiveSync() {
        Log.i(TAG, "User explicitly requested sync cancellation.")
        activeSyncJob?.cancel()
        isSyncing.value = false
        AppBridge.syncStatus.value = "IDLE"
    }

    suspend fun performSync(context: Context, priorityTab: String? = null, forceNewSession: Boolean = false) {
        if (isSyncing.value) {
            Log.w(TAG, "performSync ignored: already syncing")
            return
        }

        activeSyncJob = syncScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    isSyncing.value = true
                    AppBridge.syncStatus.value = "Logging in..."
                }

                val (client, credentials) = SessionManager.createClient(context)
                val username = credentials.first ?: ""

                SessionManager.setSyncClient(client)

                if (forceNewSession) {
                    Log.i(TAG, "Force Refresh Requested: Wiping existing session cookies.")
                    client.reinitializeSession(context)
                }

                var loginSuccess = false
                var attempts = 0

                Log.d(TAG, "[SYNC STEP 1] Starting Login Process...")
                Log.d(TAG, "Telemetry initialized and working...")

                TelemetryTracer.trace(
                    "Login",
                    TelemetryModule.AUTH
                )
                {

                    while (attempts < MAX_RETRY && !loginSuccess) {
                        Log.d(TAG, "Login Attempt ${attempts + 1} of $MAX_RETRY")

                        try {
                            loginSuccess =
                                client.autoLogin(context, object : VtopClient.LoginListener {
                                    override fun onStatusUpdate(message: String) {
                                        Log.d(TAG, "Status update: $message")
                                    }

                                    override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                        syncScope.launch(Dispatchers.IO) {
                                            // Capture the exact moment VTOP demanded the OTP
                                            val otpRequestedTime = System.currentTimeMillis()
                                            Log.d(TAG, "VTOP requested OTP. Timestamp marked at: $otpRequestedTime")

                                            val savedEmail = Vault.getGoogleEmail(context)

                                            if (savedEmail.isNotBlank()) {
                                                withContext(Dispatchers.Main) {
                                                    AppBridge.syncStatus.value = "Fetching OTP from Gmail..."
                                                }
                                                val extractedOtp =
                                                    GmailOtpExtractor.getLatestVtopOtp(
                                                        context,
                                                        savedEmail,
                                                        otpRequestedTime // <-- PASSED DOWN HERE
                                                    )
                                                if (extractedOtp != null) {
                                                    Log.d(TAG, "Silently intercepted OTP: $extractedOtp")
                                                    withContext(Dispatchers.Main) {
                                                        AppBridge.syncStatus.value = "Verifying OTP..."
                                                    }
                                                    resolver.submit(extractedOtp)
                                                    return@launch
                                                } else {
                                                    Log.w(TAG, "Failed to intercept OTP automatically. Falling back to manual entry.")
                                                }
                                            }

                                            withContext(Dispatchers.Main) {
                                                AppBridge.syncStatus.value = "Awaiting manual OTP..."
                                            }

                                            if (AppBridge.isAppInForeground) {
                                                withContext(Dispatchers.Main) {
                                                    AppBridge.currentOtpResolver.value = resolver
                                                }
                                            } else {
                                                val deferredOtp = kotlinx.coroutines.CompletableDeferred<String?>()
                                                AppBridge.pendingOtpDeferred = deferredOtp
                                                NotificationHelper.showOtpNotification(context)

                                                val userOtp = withTimeoutOrNull(180_000L) { deferredOtp.await() }

                                                if (userOtp != null) resolver.submit(userOtp)
                                                else {
                                                    resolver.cancel()
                                                    AppBridge.pendingOtpDeferred = null
                                                    NotificationHelper.dismissNotification(
                                                        context,
                                                        NotificationHelper.OTP_NOTIFICATION_ID
                                                    )
                                                    cancelActiveSync()
                                                }
                                            }
                                        }
                                    }
                                })
                        } catch (e: VtopException.InvalidCredentials) {
                            SessionManager.invalidateSync()
                            throw e
                        } catch (e: VtopException.AuthenticationFailed) {
                            SessionManager.invalidateSync()
                            throw e
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Attempt ${attempts + 1} failed: ${e.message}")
                            loginSuccess = false
                        } finally {
                            withContext(Dispatchers.Main) {
                                AppBridge.currentOtpResolver.value = null
                            }
                            AppBridge.pendingOtpDeferred = null
                        }

                        if (!loginSuccess) {
                            attempts++
                            if (attempts < MAX_RETRY) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        context,
                                        "Login failed. Retrying...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                client.reinitializeSession(context)
                            }
                        }
                    }
                }

                if (!loginSuccess) {
                    SessionManager.invalidateSync()
                    throw Exception("Failed to login after $MAX_RETRY attempts. VTOP might be blocking requests.")
                }

                Log.d(TAG, "[SYNC STEP 2] Login Successful. Moving to ID Validation.")

                var authorizedId = Vault.getRegNo(context)
                val validRegNoRegex = Regex("""\b\d{2}[a-zA-Z]{3}\d{4}\b""")
                TelemetryTracer.trace(
                    "Registration Number Discovery",
                    TelemetryModule.AUTH
                )
                {
                    if (authorizedId.isBlank() || authorizedId == "-" || !validRegNoRegex.matches(
                            authorizedId
                        )
                    ) {
                        Log.w(
                            TAG,
                            "[SYNC STEP 3] Valid ID not found in Vault (Current: '$authorizedId'). Forcing /content scrape."
                        )
                        withContext(Dispatchers.Main) {
                            AppBridge.syncStatus.value = "Establishing Session..."
                        }

                        Log.d(TAG, "[SYNC STEP 4] Fetching /content page...")
                        val contentHtml = client.fetchContentPageRawHtml()
                        Log.d(
                            TAG,
                            "[SYNC STEP 5] /content page fetched. Length: ${contentHtml?.length ?: 0}"
                        )

                        val scrapedId = SessionManager.extractAuthorizedIdFromContent(contentHtml)

                        if (!scrapedId.isNullOrBlank() && validRegNoRegex.matches(scrapedId)) {
                            authorizedId = scrapedId
                            Vault.saveRegNo(context, authorizedId)
                            Log.d(
                                TAG,
                                "[SYNC STEP 6] Successfully scraped and locked Registration Number: $authorizedId"
                            )
                        } else {
                            Log.e(
                                TAG,
                                "[SYNC STEP 6] Failed to scrape valid Registration Number. Falling back to username."
                            )
                            authorizedId = username
                        }
                    } else {
                        Log.d(
                            TAG,
                            "[SYNC STEP 3] Valid Registration Number already locked in Vault: $authorizedId"
                        )
                    }
                }

                Log.d(TAG, "[SYNC STEP 6.5] Injecting Authorized ID into Client: $authorizedId")
                client.setAuthorizedId(authorizedId)
                // TODO: The authorizedId should eventually become part of the Session object once SessionManager stores session metadata.

                val semInfo = Vault.getSelectedSemester(context)
                val semId = semInfo[0] ?: ""

                val showOutings = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE).getBoolean("SHOW_OUTINGS", true)
                suspend fun updateStatus(msg: String) {
                    withContext(Dispatchers.Main) { AppBridge.syncStatus.value = msg }
                }

                val priority = priorityTab?.uppercase()
                Log.d(TAG, "[SYNC STEP 7] Executing Priority Fetch for: $priority using ID: $authorizedId")

                // --- Live UI updates during Priority Fetch ---
                TelemetryTracer.trace(
                    "Priority Fetch",
                    TelemetryModule.SYNC
                ) {
                    when (priority) {
                        "HOME" -> { updateStatus("Syncing Timetable..."); syncTimetable(context, client, semId) }
                        "ATTENDANCE" -> {
                            updateStatus("Syncing Attendance...")
                            AttendanceSyncEngine.sync(
                                context = context,
                                client = client,
                                semId = semId,
                                authorizedId = authorizedId,
                                mode = AttendanceSyncMode.OPTIMIZED,
                                logTag = "ATT_OPT"
                            )
                        }
                        "EXAMS" -> { updateStatus("Syncing Exams..."); syncExams(context, client, semId) }
                        "MARKS" -> { updateStatus("Syncing Marks & Grades..."); syncMarks(context, client, semId) }
                        "OUTINGS" -> {
                            if (showOutings) {
                                updateStatus("Syncing Outings...")
                                syncOutings(context, client, authorizedId)
                            }
                        }
                        "PROFILE" -> {
                            updateStatus("Syncing Profile...")
                            Log.d(TAG, "[SYNC STEP 7.1] Fetching Profile...")
                            val profileHtml = client.fetchProfileRawHtml(null)
                            val profileData = ProfileParser.parse(profileHtml)
                            Vault.saveProfile(context, profileData)
                            withContext(Dispatchers.Main) { AppBridge.profileState.value = profileData }
                        }
                    }
                }

                Log.d(TAG, "[SYNC STEP 8] Priority fetch complete. Fetching remaining data in background...")

                // --- Live UI updates during Background Fetches ---
                TelemetryTracer.trace(
                    "Background Sync",
                    TelemetryModule.SYNC
                ) {
                    if (priority != "HOME") {
                        updateStatus("Syncing Timetable..."); syncTimetable(context, client, semId)
                    }
                    if (priority != "ATTENDANCE") {
                        updateStatus("Syncing Attendance...")
                        AttendanceSyncEngine.sync(
                            context = context,
                            client = client,
                            semId = semId,
                            authorizedId = authorizedId,
                            mode = AttendanceSyncMode.OPTIMIZED,
                            logTag = "ATT_OPT"
                        )
                    }
                    if (priority != "EXAMS") {
                        updateStatus("Syncing Exams..."); syncExams(context, client, semId)
                    }
                    if (priority != "MARKS") {
                        updateStatus("Syncing Marks & Grades..."); syncMarks(context, client, semId)
                    }
                    if (priority != "OUTINGS" && showOutings) {
                        updateStatus("Syncing Outings...")
                        syncOutings(
                            context,
                            client,
                            authorizedId
                        )
                    }
                    if (priority != "PROFILE") {
                        updateStatus("Syncing Profile...")
                        val profileHtml = client.fetchProfileRawHtml(null)
                        val profileData = ProfileParser.parse(profileHtml)
                        Vault.saveProfile(context, profileData)
                        withContext(Dispatchers.Main) { AppBridge.profileState.value = profileData }
                    }
                    updateStatus("Syncing Academic Calendar...")
                    syncCalendar(context, client, semId)
                }
                updateStatus("Finishing up...")

                Log.d(TAG, "[SYNC STEP 9] All data fetched. Updating widgets & saving timestamps.")
                Vault.saveLastSyncTime(context)
                try { NextClassWidget().updateAll(context) } catch (e: Exception) { Log.e(TAG, "Widget update failed") }

                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Complete!", Toast.LENGTH_SHORT).show() }

            } catch (e: Exception) {
                SessionManager.invalidateSync()
                Log.e(TAG, "Sync Error", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Error: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                withContext(Dispatchers.Main) {
                    AppBridge.syncStatus.value = "IDLE" // This triggers the UI to flip back to "Last synced: Just now"
                    isSyncing.value = false
                }
            }
        }
    }

    private suspend fun syncTimetable(context: Context, client: VtopClient, semId: String) {
        TelemetryTracer.trace(
            "Timetable",
            TelemetryModule.SYNC
        ) {
            val html = client.fetchTimetableRawHtml(semId, null)
            val data = TimetableParser.parse(html)
            Vault.saveTimetable(context, data)
            withContext(Dispatchers.Main) { AppBridge.timetableState.value = data }
        }
    }

    private suspend fun syncExams(context: Context, client: VtopClient, semId: String) {
        TelemetryTracer.trace(
            "Exam Schedule",
            TelemetryModule.SYNC
        ) {
            val html = client.fetchExamScheduleRawHtml(semId, null)
            val data = ExamScheduleParser.parse(html)
            Vault.saveExamSchedule(context, data)
            com.vtop.utils.ExamSeatScheduler.buildExamQueue(context, data)
            withContext(Dispatchers.Main) { AppBridge.examsState.value = data }
        }
    }

    private suspend fun syncMarks(context: Context, client: VtopClient, semId: String) {
        TelemetryTracer.trace(
            "Marks & Grades",
            TelemetryModule.SYNC
        ) {
            val marksHtml = client.fetchMarksRawHtml(semId, null)
            val marksData = MarksParser.parseMarks(marksHtml)
            val gradesHtml = client.fetchGradesRawHtml(semId, null)
            val gradesData = MarksParser.parseGrades(gradesHtml)
            val historyHtml = client.fetchHistoryRawHtml(null)
            val historyPair = MarksParser.parseHistory(historyHtml)
            val fetchedSemesters = client.fetchSemesters()

            val mappedOptions = fetchedSemesters.map { map ->
                com.vtop.models.SemesterOption(id = map["id"] ?: "", name = map["name"] ?: "")
            }

            Vault.saveSemesterOptions(context, mappedOptions)
            Vault.saveMarks(context, marksData)
            Vault.saveGrades(context, gradesData)
            Vault.saveHistory(context, historyPair.second)
            Vault.saveCGPASummary(context, historyPair.first)

            withContext(Dispatchers.Main) {
                AppBridge.marksState.value = marksData
                AppBridge.gradesState.value = gradesData
                AppBridge.historyItemsState.value = historyPair.second
                AppBridge.historySummaryState.value = historyPair.first
            }
        }
    }

    private suspend fun syncOutings(
        context: Context,
        client: VtopClient,
        authorizedId: String
    ) {
        TelemetryTracer.trace("Outings", TelemetryModule.SYNC) {
            Log.d("WKND_DEBUG", "syncOutings started: authorizedId=$authorizedId")

            val genHtml = client.fetchGeneralOutingRawHtml(authorizedId, null)
            Log.d("WKND_DEBUG", "General HTML length=${genHtml?.length ?: -1}")

            Log.d("WKND_DEBUG", "Calling fetchWeekendOutingRawHtml...")
            val weekHtml = client.fetchWeekendOutingRawHtml(authorizedId, null)
            Log.d("WKND_DEBUG", "Weekend HTML length=${weekHtml?.length ?: -1}")
            Log.d("WKND_DEBUG", "Weekend HTML preview=${weekHtml?.take(500)}")

            val generalOutings = OutingParser.parseGeneral(genHtml ?: "")
            Log.d("WKND_DEBUG", "General parsed=${generalOutings.size}")

            Log.d("WKND_DEBUG", "Calling parseWeekend...")
            val weekendOutings = OutingParser.parseWeekend(weekHtml ?: "")
            Log.d("WKND_DEBUG", "Weekend parsed=${weekendOutings.size}")

            val allOutings = generalOutings + weekendOutings
            Log.d(
                "WKND_DEBUG",
                "Final outings=${allOutings.size}, general=${generalOutings.size}, weekend=${weekendOutings.size}"
            )

            Vault.saveOutings(context, allOutings)
            withContext(Dispatchers.Main) {
                AppBridge.outingsState.value = allOutings
            }
        }
    }
    private suspend fun syncCalendar(
        context: Context,
        client: VtopClient,
        semId: String
    ) {
        TelemetryTracer.trace(
            "Academic Calendar",
            TelemetryModule.SYNC
        ) {
            try {

                Log.d(
                    TAG,
                    "========== CALENDAR SYNC START =========="
                )
                Log.d(TAG, "Fetching dedicated calendar semester list...")
                val calSemesters = client.fetchCalendarSemesters()
                if (calSemesters.isNotEmpty()) {
                    Vault.saveCalendarSemesterOptions(context, calSemesters)
                    Log.d(TAG, "Successfully cached ${calSemesters.size} calendar semesters.")
                } else {
                    Log.w(TAG, "Failed to extract calendar semesters.")
                }
                // -------------------------------------------------------------

                val months =
                    client.fetchCalendarMonths(
                        semId,
                        "COMB"
                    )

                if (months.isEmpty()) {

                    Log.e(
                        TAG,
                        "No calendar months found"
                    )

                    return
                }

                Log.d(
                    TAG,
                    "Calendar Months Found: ${months.size}"
                )

                val allEvents =
                    mutableListOf<com.vtop.models.AcademicCalendarEvent>()

                for (month in months) {

                    Log.d(
                        TAG,
                        "Fetching Calendar Month: $month"
                    )

                    val html =
                        client.fetchCalendarRawHtml(
                            semId,
                            month,
                            "COMB"
                        )

                    if (html == null) {

                        Log.e(
                            TAG,
                            "Failed Calendar Month: $month"
                        )

                        continue
                    }

                    try {

                        val events =
                            CalendarParser.parseCalendarHtml(
                                html
                            )

                        allEvents.addAll(events)

                        Log.d(
                            TAG,
                            "Parsed ${events.size} events from $month"
                        )

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Calendar Parse Failed: $month",
                            e
                        )
                    }
                }

                Vault.saveAcademicCalendar(
                    context,
                    semId,
                    allEvents
                )

                withContext(Dispatchers.Main) {

                    AppBridge.calendarState.value = allEvents

                    AppBridge.calendarSemesterId.value = semId
                }

                Log.d(
                    TAG,
                    "Calendar Sync Complete: ${allEvents.size} events"
                )

                Log.d(
                    TAG,
                    "=========== CALENDAR SYNC END ==========="
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Calendar Sync Error",
                    e
                )
            }
        }
    }

}