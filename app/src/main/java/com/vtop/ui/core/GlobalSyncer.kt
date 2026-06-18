    package com.vtop.ui.core

    import android.content.Context
    import android.util.Log
    import android.widget.Toast
    import androidx.glance.appwidget.updateAll
    import com.vtop.widget.NextClassWidget
    import androidx.compose.runtime.mutableStateOf
    import com.vtop.network.VtopClient
    import com.vtop.network.VtopException
    import com.vtop.utils.*
    import com.vtop.logic.*
    import kotlinx.coroutines.CancellationException
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.SupervisorJob
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.async
    import kotlinx.coroutines.awaitAll
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

        private fun extractAuthorizedIdFromContent(html: String?): String? {
            if (html.isNullOrBlank()) return null

            val regNoPattern = Regex("""\b\d{2}[a-zA-Z]{3}\d{4}\b""")
            val match = regNoPattern.find(html)
            if (match != null) return match.value.uppercase()

            val jsPattern = Regex("""(?:let|var)\s+id\s*=\s*['"]([^'"]+)['"]""")
            val jsMatch = jsPattern.find(html)
            if (jsMatch != null) return jsMatch.groupValues[1].uppercase()

            return null
        }

        // ... inside GlobalSyncer.kt ...

        suspend fun performSync(context: Context, priorityTab: String? = null, forceNewSession: Boolean = false) {
            if (isSyncing.value) {
                Log.w(TAG, "performSync ignored: already syncing")
                return
            }

            activeSyncJob = syncScope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        isSyncing.value = true
                        // UPDATED: Standardized casing
                        AppBridge.syncStatus.value = "Logging in..."

                    }

                    val creds = Vault.getCredentials(context)
                    val username = creds[0] // Strictly used to log in
                    val password = creds[1]

                    val client = VtopClient(context, username, password)
                    android.util.Log.d(
                        "TT_EXPORT",
                        "GlobalSyncer created client: $client"
                    )

                    AppBridge.activeClient = client

                    android.util.Log.d(
                        "TT_EXPORT",
                        "AppBridge.activeClient assigned"
                    )

                    if (forceNewSession) {
                        Log.i(TAG, "Force Refresh Requested: Wiping existing session cookies.")
                        client.reinitializeSession(context)
                    }

                    var loginSuccess = false
                    var attempts = 0

                    Log.d(TAG, "[SYNC STEP 1] Starting Login Process...")

                    while (attempts < MAX_RETRY && !loginSuccess) {
                        Log.d(TAG, "Login Attempt ${attempts + 1} of $MAX_RETRY")

                        try {
                            loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                                override fun onStatusUpdate(message: String) {
                                    Log.d(TAG, "Status update: $message")
                                }

                                override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                    syncScope.launch(Dispatchers.IO) {
                                        val savedEmail = Vault.getGoogleEmail(context)

                                        if (savedEmail.isNotBlank()) {
                                            // UPDATED: Show Gmail Interception Status
                                            withContext(Dispatchers.Main) { AppBridge.syncStatus.value = "Fetching OTP from Gmail..." }
                                            val extractedOtp = GmailOtpExtractor.getLatestVtopOtp(context, savedEmail)
                                            if (extractedOtp != null) {
                                                Log.d(TAG, "Silently intercepted OTP: $extractedOtp")
                                                // UPDATED: Show Verification Phase
                                                withContext(Dispatchers.Main) { AppBridge.syncStatus.value = "Verifying OTP..." }
                                                resolver.submit(extractedOtp)
                                                return@launch
                                            }
                                        }

                                        // UPDATED: Fallback manual OTP wait state
                                        withContext(Dispatchers.Main) { AppBridge.syncStatus.value = "Awaiting manual OTP..." }

                                        if (AppBridge.isAppInForeground) {
                                            withContext(Dispatchers.Main) { AppBridge.currentOtpResolver.value = resolver }
                                        } else {
                                            val deferredOtp = kotlinx.coroutines.CompletableDeferred<String?>()
                                            AppBridge.pendingOtpDeferred = deferredOtp
                                            NotificationHelper.showOtpNotification(context)

                                            val userOtp = withTimeoutOrNull(180_000L) { deferredOtp.await() }

                                            if (userOtp != null) resolver.submit(userOtp)
                                            else {
                                                resolver.cancel()
                                                AppBridge.pendingOtpDeferred = null
                                                NotificationHelper.dismissNotification(context, NotificationHelper.OTP_NOTIFICATION_ID)
                                                cancelActiveSync()
                                            }
                                        }
                                    }
                                }
                            })
                        } catch (e: VtopException.InvalidCredentials) {
                            throw e
                        } catch (e: VtopException.AuthenticationFailed) {
                            throw e
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Attempt ${attempts + 1} failed: ${e.message}")
                            loginSuccess = false
                        } finally {
                            withContext(Dispatchers.Main) { AppBridge.currentOtpResolver.value = null }
                            AppBridge.pendingOtpDeferred = null
                        }

                        if (!loginSuccess) {
                            attempts++
                            if (attempts < MAX_RETRY) {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Login failed. Retrying...", Toast.LENGTH_SHORT).show() }
                                client.reinitializeSession(context)
                            }
                        }
                    }

                    if (!loginSuccess) {
                        throw Exception("Failed to login after $MAX_RETRY attempts. VTOP might be blocking requests.")
                    }

                    Log.d(TAG, "[SYNC STEP 2] Login Successful. Moving to ID Validation.")

                    var authorizedId = Vault.getRegNo(context)
                    val validRegNoRegex = Regex("""\b\d{2}[a-zA-Z]{3}\d{4}\b""")

                    if (authorizedId.isBlank() || authorizedId == "-" || !validRegNoRegex.matches(authorizedId)) {
                        Log.w(TAG, "[SYNC STEP 3] Valid ID not found in Vault (Current: '$authorizedId'). Forcing /content scrape.")
                        withContext(Dispatchers.Main) { AppBridge.syncStatus.value = "Establishing Session..." }

                        Log.d(TAG, "[SYNC STEP 4] Fetching /content page...")
                        val contentHtml = client.fetchContentPageRawHtml()
                        Log.d(TAG, "[SYNC STEP 5] /content page fetched. Length: ${contentHtml?.length ?: 0}")

                        val scrapedId = extractAuthorizedIdFromContent(contentHtml)

                        if (!scrapedId.isNullOrBlank() && validRegNoRegex.matches(scrapedId)) {
                            authorizedId = scrapedId
                            Vault.saveRegNo(context, authorizedId)
                            Log.d(TAG, "[SYNC STEP 6] Successfully scraped and locked Registration Number: $authorizedId")
                        } else {
                            Log.e(TAG, "[SYNC STEP 6] Failed to scrape valid Registration Number. Falling back to username.")
                            authorizedId = username
                        }
                    } else {
                        Log.d(TAG, "[SYNC STEP 3] Valid Registration Number already locked in Vault: $authorizedId")
                    }

                    Log.d(TAG, "[SYNC STEP 6.5] Injecting Authorized ID into Client: $authorizedId")
                    client.setAuthorizedId(authorizedId)

                    val semInfo = Vault.getSelectedSemester(context)
                    val semId = semInfo[0] ?: ""

                    // --- UPDATED: Helper function for UI updates ---
                    suspend fun updateStatus(msg: String) {
                        withContext(Dispatchers.Main) { AppBridge.syncStatus.value = msg }
                    }

                    val priority = priorityTab?.uppercase()
                    Log.d(TAG, "[SYNC STEP 7] Executing Priority Fetch for: $priority using ID: $authorizedId")

                    // --- UPDATED: Live UI updates during Priority Fetch ---
                    when (priority) {
                        "HOME" -> { updateStatus("Syncing Timetable..."); syncTimetable(context, client, semId) }
                        "ATTENDANCE" -> { updateStatus("Syncing Attendance..."); syncAttendance(context, client, semId, authorizedId) }
                        "EXAMS" -> { updateStatus("Syncing Exams..."); syncExams(context, client, semId) }
                        "MARKS" -> { updateStatus("Syncing Marks & Grades..."); syncMarks(context, client, semId) }
                        "OUTINGS" -> { updateStatus("Syncing Outings..."); syncOutings(context, client, authorizedId) }
                        "PROFILE" -> {
                            updateStatus("Syncing Profile...")
                            Log.d(TAG, "[SYNC STEP 7.1] Fetching Profile...")
                            val profileHtml = client.fetchProfileRawHtml(null)
                            val profileData = ProfileParser.parse(profileHtml)
                            Vault.saveProfile(context, profileData)
                            withContext(Dispatchers.Main) { AppBridge.profileState.value = profileData }
                        }
                    }

                    Log.d(TAG, "[SYNC STEP 8] Priority fetch complete. Fetching remaining data in background...")

                    // --- UPDATED: Live UI updates during Background Fetches ---
                    if (priority != "HOME") { updateStatus("Syncing Timetable..."); syncTimetable(context, client, semId) }
                    if (priority != "ATTENDANCE") { updateStatus("Syncing Attendance..."); syncAttendance(context, client, semId, authorizedId) }
                    if (priority != "EXAMS") { updateStatus("Syncing Exams..."); syncExams(context, client, semId) }
                    if (priority != "MARKS") { updateStatus("Syncing Marks & Grades..."); syncMarks(context, client, semId) }
                    if (priority != "OUTINGS") { updateStatus("Syncing Outings..."); syncOutings(context, client, authorizedId) }
                    if (priority != "PROFILE") {
                        updateStatus("Syncing Profile...")
                        val profileHtml = client.fetchProfileRawHtml(null)
                        val profileData = ProfileParser.parse(profileHtml)
                        Vault.saveProfile(context, profileData)
                        withContext(Dispatchers.Main) { AppBridge.profileState.value = profileData }
                    }
                    updateStatus("Syncing Academic Calendar...")
                    syncCalendar(context, client, semId)
                    updateStatus("Finishing up...")

                    Log.d(TAG, "[SYNC STEP 9] All data fetched. Updating widgets & saving timestamps.")
                    Vault.saveLastSyncTime(context)
                    try { NextClassWidget().updateAll(context) } catch (e: Exception) { Log.e(TAG, "Widget update failed") }

                    withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Complete!", Toast.LENGTH_SHORT).show() }

                } catch (e: Exception) {
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
            val html = client.fetchTimetableRawHtml(semId, null)
            val data = TimetableParser.parse(html)
            Vault.saveTimetable(context, data)
            withContext(Dispatchers.Main) { AppBridge.timetableState.value = data }
        }

        private suspend fun syncAttendance(context: Context, client: VtopClient, semId: String, authorizedId: String) {
            val html = client.fetchAttendanceRawHtml(semId, null)
            val data = AttendanceParser.parseSummary(html)
            for (course in data) {
                val cId = course.courseId ?: continue
                val cType = course.courseType ?: continue
                val detailHtml = client.fetchAttendanceDetailRawHtml(semId, cId, cType, authorizedId, null)
                AttendanceParser.parseDetailAndUpdate(detailHtml, course)
            }
            Vault.saveAttendance(context, data)
            withContext(Dispatchers.Main) { AppBridge.attendanceState.value = data }
        }

        private suspend fun syncExams(context: Context, client: VtopClient, semId: String) {
            val html = client.fetchExamScheduleRawHtml(semId, null)
            val data = ExamScheduleParser.parse(html)
            Vault.saveExamSchedule(context, data)
            com.vtop.utils.ExamSeatScheduler.buildExamQueue(context, data)
            withContext(Dispatchers.Main) { AppBridge.examsState.value = data }
        }

        private suspend fun syncMarks(context: Context, client: VtopClient, semId: String) {
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

        private suspend fun syncOutings(context: Context, client: VtopClient, authorizedId: String) {
            val genHtml = client.fetchGeneralOutingRawHtml(authorizedId, null)
            val weekHtml =
                client.fetchWeekendOutingRawHtml(
                    authorizedId,
                    null
                )

            Log.d(
                "WKND_HTML",
                weekHtml?.take(2000) ?: "NULL"
            )

            val weekendParsed =
                OutingParser.parseWeekend(
                    weekHtml ?: ""
                )

            Log.d(
                "WKND_COUNT",
                weekendParsed.size.toString()
            )
            val allOutings = OutingParser.parseGeneral(genHtml ?: "") + OutingParser.parseWeekend(weekHtml ?: "")
            Vault.saveOutings(context, allOutings)
            withContext(Dispatchers.Main) { AppBridge.outingsState.value = allOutings }
        }
        private suspend fun syncCalendar(
            context: Context,
            client: VtopClient,
            semId: String
        ) {

            try {

                Log.d(
                    TAG,
                    "========== CALENDAR SYNC START =========="
                )

                // --- NEW: Fetch and cache the dedicated Calendar Semester list ---
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