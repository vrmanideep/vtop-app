@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE", "UNUSED_PARAMETER")

package com.vtop.sync

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.vtop.widget.NextClassWidget
import com.vtop.core.*
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SyncManager {
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private const val MAX_RETRY = 3
    private const val TAG = "SYNC_MANAGER"
    @Volatile private var activeSyncJob: kotlinx.coroutines.Job? = null

    fun cancelActiveSync() {
        Log.i(TAG, "User explicitly requested sync cancellation.")
        activeSyncJob?.cancel()
        _isSyncing.value = false
        EventBus.tryEmit(AppEvent.SyncStatusChanged("IDLE"))
    }

    suspend fun performSync(context: Context, priorityTab: String? = null, forceNewSession: Boolean = false, targetSemId: String? = null) {
        if (_isSyncing.value) {
            Log.w(TAG, "performSync ignored: already syncing")
            return
        }

        activeSyncJob = syncScope.launch {
            try {
                _isSyncing.value = true
                EventBus.emit(AppEvent.SyncStatusChanged("Logging in..."))

                val existingClient = SessionManager.getSyncClient()
                val client: VtopClient
                val username: String

                if (existingClient != null) {
                    client = existingClient
                    username = client.username ?: ""
                } else {
                    val (newClient, credentials) = SessionManager.createClient(context, SessionType.SYNC)
                    SessionManager.setSyncClient(newClient)
                    client = newClient
                    username = credentials.first ?: ""
                }

                if (forceNewSession) {
                    client.reinitializeSession(context)
                }

                var loginSuccess = false
                var attempts = 0

                TelemetryTracer.trace("Login", TelemetryModule.AUTH) {
                    while (attempts < MAX_RETRY && !loginSuccess) {
                        try {
                            loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                                override fun onStatusUpdate(message: String) { }
                                override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                    syncScope.launch(Dispatchers.IO) {
                                        val otpRequestedTime = System.currentTimeMillis()
                                        val savedEmail = Vault.getGoogleEmail(context)

                                        if (savedEmail.isNotBlank()) {
                                            EventBus.emit(AppEvent.SyncStatusChanged("Fetching OTP from Gmail..."))
                                            val extractedOtp = GmailOtpExtractor.getLatestVtopOtp(context, savedEmail, otpRequestedTime)
                                            if (extractedOtp != null) {
                                                EventBus.emit(AppEvent.SyncStatusChanged("Verifying OTP..."))
                                                resolver.submit(extractedOtp)
                                                return@launch
                                            }
                                        }

                                        EventBus.emit(AppEvent.SyncStatusChanged("Awaiting manual OTP..."))
                                        EventBus.emit(AppEvent.AuthOtpRequested(resolver))
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
                            loginSuccess = false
                        }

                        if (!loginSuccess) {
                            attempts++
                            if (attempts < MAX_RETRY) {
                                EventBus.emit(AppEvent.ToastMessage("Login failed. Retrying..."))
                                client.reinitializeSession(context)
                            }
                        }
                    }
                }

                if (!loginSuccess) {
                    SessionManager.invalidateSync()
                    throw Exception("Failed to login after $MAX_RETRY attempts.")
                }

                var authorizedId = Vault.getRegNo(context)
                val validRegNoRegex = Regex("""\b\d{2}[a-zA-Z]{3}\d{4}\b""")

                TelemetryTracer.trace("Registration Number Discovery", TelemetryModule.AUTH) {
                    if (authorizedId.isBlank() || authorizedId == "-" || !validRegNoRegex.matches(authorizedId)) {
                        EventBus.emit(AppEvent.SyncStatusChanged("Establishing Session..."))
                        val contentHtml = client.fetchContentPageRawHtml()
                        val scrapedId = SessionManager.extractAuthorizedIdFromContent(contentHtml)

                        if (!scrapedId.isNullOrBlank() && validRegNoRegex.matches(scrapedId)) {
                            authorizedId = scrapedId
                            Vault.saveRegNo(context, authorizedId)
                        } else {
                            authorizedId = username
                        }
                    }
                }

                client.setAuthorizedId(authorizedId)

                val semInfo = Vault.getSelectedSemester(context)
                val semId = targetSemId ?: semInfo[0] ?: ""
                val showOutings = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE).getBoolean("SHOW_OUTINGS", true)

                suspend fun updateStatus(msg: String) {
                    EventBus.emit(AppEvent.SyncStatusChanged(msg))
                }

                val priority = priorityTab?.uppercase()

                TelemetryTracer.trace("Priority Fetch", TelemetryModule.SYNC) {
                    when (priority) {
                        "HOME" -> { updateStatus("Syncing Timetable..."); syncTimetable(context, client, semId) }
                        "ATTENDANCE" -> {
                            updateStatus("Syncing Attendance...")
                            AttendanceSyncEngine.sync(context, client, semId, authorizedId, AttendanceSyncMode.OPTIMIZED, "ATT_OPT")
                        }
                        "EXAMS" -> { updateStatus("Syncing Exams..."); syncExams(context, client, semId) }
                        "MARKS" -> { updateStatus("Syncing Marks & Grades..."); syncMarks(context, client, semId) }
                        "OUTINGS" -> { if (showOutings) { updateStatus("Syncing Outings..."); syncOutings(context, client, authorizedId) } }
                        "PROFILE" -> {
                            updateStatus("Syncing Profile...")
                            val profileHtml = client.fetchProfileRawHtml(null)
                            ProfileRepository.update(context, ProfileParser.parse(profileHtml))
                        }
                        "CALENDAR" -> { updateStatus("Syncing Calendar..."); syncCalendar(context, client, semId) }
                    }
                }

                TelemetryTracer.trace("Background Sync", TelemetryModule.SYNC) {
                    if (priority != "HOME") { updateStatus("Syncing Timetable..."); syncTimetable(context, client, semId) }
                    if (priority != "ATTENDANCE") {
                        updateStatus("Syncing Attendance...")
                        AttendanceSyncEngine.sync(context, client, semId, authorizedId, AttendanceSyncMode.OPTIMIZED, "ATT_OPT")
                    }
                    if (priority != "EXAMS") { updateStatus("Syncing Exams..."); syncExams(context, client, semId) }
                    if (priority != "MARKS") { updateStatus("Syncing Marks & Grades..."); syncMarks(context, client, semId) }
                    if (priority != "OUTINGS" && showOutings) { updateStatus("Syncing Outings..."); syncOutings(context, client, authorizedId) }
                    if (priority != "PROFILE") {
                        updateStatus("Syncing Profile...")
                        val profileHtml = client.fetchProfileRawHtml(null)
                        ProfileRepository.update(context, ProfileParser.parse(profileHtml))
                    }
                    if (priority != "CALENDAR") {
                        val existingCalendar = Vault.getAcademicCalendar(context, semId)
                        if (existingCalendar.isEmpty()) {
                            updateStatus("Fetching Academic Calendar...")
                            syncCalendar(context, client, semId)
                        }
                    }
                }


                updateStatus("Finishing up...")
                Vault.saveLastSyncTime(context)
                try { NextClassWidget().updateAll(context) } catch (e: Exception) { Log.e(TAG, "Widget update failed") }

                EventBus.emit(AppEvent.SyncCompleted)
                EventBus.emit(AppEvent.ToastMessage("Sync Complete!"))

            } catch (e: Exception) {
                SessionManager.invalidateSync()
                EventBus.emit(AppEvent.SyncError(e))
                EventBus.emit(AppEvent.ToastMessage("Sync Error: ${e.message}", isLong = true))
            } finally {
                _isSyncing.value = false
                EventBus.tryEmit(AppEvent.SyncStatusChanged("IDLE"))
            }
        }
    }

    private suspend fun syncTimetable(context: Context, client: VtopClient, semId: String) {
        TelemetryTracer.trace("Timetable", TelemetryModule.SYNC) {
            val html = client.fetchTimetableRawHtml(semId, null)
            val data = TimetableParser.parse(html)
            TimetableRepository.update(context, data)
        }
    }

    private suspend fun syncExams(context: Context, client: VtopClient, semId: String) {
        TelemetryTracer.trace("Exam Schedule", TelemetryModule.SYNC) {
            val html = client.fetchExamScheduleRawHtml(semId, null)
            val data = ExamScheduleParser.parse(html)
            ExamsRepository.update(context, data)
            ExamSeatScheduler.buildExamQueue(context, data)
        }
    }

    private suspend fun syncMarks(context: Context, client: VtopClient, semId: String) {
        TelemetryTracer.trace("Marks & Grades", TelemetryModule.SYNC) {
            val marksHtml = client.fetchMarksRawHtml(semId, null)
            val gradesHtml = client.fetchGradesRawHtml(semId, null)
            val historyHtml = client.fetchHistoryRawHtml(null)
            val fetchedSemesters = client.fetchSemesters()

            val mappedOptions = fetchedSemesters.map { map ->
                com.vtop.models.SemesterOption(id = map["id"] ?: "", name = map["name"] ?: "")
            }
            Vault.saveSemesterOptions(context, mappedOptions)

            val marksData = MarksParser.parseMarks(marksHtml)
            val gradesData = MarksParser.parseGrades(gradesHtml)
            val historyPair = MarksParser.parseHistory(historyHtml)

            MarksRepository.update(context, marksData)
            GradesRepository.updateGrades(context, gradesData)
            GradesRepository.updateHistory(context, historyPair.first, historyPair.second)
        }
    }

    private suspend fun syncOutings(context: Context, client: VtopClient, authorizedId: String) {
        TelemetryTracer.trace("Outings", TelemetryModule.SYNC) {
            val genHtml = client.fetchGeneralOutingRawHtml(authorizedId, null)
            val weekHtml = client.fetchWeekendOutingRawHtml(authorizedId, null)
            val allOutings = OutingParser.parseGeneral(genHtml ?: "") + OutingParser.parseWeekend(weekHtml ?: "")
            OutingsRepository.update(context, allOutings)
        }
    }

    private suspend fun syncCalendar(context: Context, client: VtopClient, semId: String) {
        TelemetryTracer.trace("Calendar", TelemetryModule.SYNC) {
            val semestersHtml = client.fetchCalendarSemestersRawHtml()
            val fetchedSems = AcademicCalendarParser.parseSemesters(semestersHtml)
            if (fetchedSems.isNotEmpty()) {
                Vault.saveCalendarSemesterOptions(context, fetchedSems)
            }

            val monthsHtml = client.fetchCalendarMonthsRawHtml(semId, "ALL")
            val availableDates = AcademicCalendarParser.parseMonths(monthsHtml)

            if (availableDates.isNotEmpty()) {
                val allEvents = mutableListOf<com.vtop.models.AcademicCalendarEvent>()
                availableDates.forEachIndexed { index, dateStr ->
                    EventBus.emit(AppEvent.SyncStatusChanged("Syncing Calendar (Month ${index + 1} of ${availableDates.size})..."))
                    val html = client.fetchCalendarRawHtml(semId, dateStr, "ALL")
                    if (!html.isNullOrBlank()) {
                        val monthlyEvents = CalendarParser.parseCalendarHtml(html)
                        allEvents.addAll(monthlyEvents)
                    }
                    kotlinx.coroutines.delay(250L) // WAF breather
                }
                if (allEvents.isNotEmpty()) {
                    CalendarRepository.update(context, semId, allEvents)
                }
            }
        }
    }
}