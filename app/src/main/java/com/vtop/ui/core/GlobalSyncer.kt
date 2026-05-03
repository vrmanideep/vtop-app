package com.vtop.ui.core

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.glance.appwidget.updateAll
import com.vtop.widget.NextClassWidget
import androidx.compose.runtime.mutableStateOf
import com.vtop.network.VtopClient
import com.vtop.utils.Vault
import com.vtop.logic.*
import com.vtop.widget.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object GlobalSyncer {
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val isSyncing = mutableStateOf(false)
    private const val MAX_RETRY = 3
    private const val TAG = "GLOBAL_SYNC"
    @Volatile private var activeSyncJob: kotlinx.coroutines.Job? = null

    fun performSync(context: Context, priorityTab: String? = null) {
        // Prevent double-tapping the sync button
        if (isSyncing.value) {
            Log.w(TAG, "performSync ignored: already syncing")
            return
        }

        activeSyncJob = syncScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    isSyncing.value = true
                    AppBridge.syncStatus.value = "LOGGING_IN"
                }

                val creds = Vault.getCredentials(context)
                val regNo = creds[0] ?: throw Exception("No Registration Number")
                val pass = creds[1] ?: throw Exception("No Password")

                val semInfo = Vault.getSelectedSemester(context)
                val semId = semInfo[0] ?: ""

                val client = VtopClient(context, regNo, pass)

                // ==========================================
                // TASK 10: SMART CAPTCHA RETRY LOGIC
                // ==========================================
                var loginSuccess = false
                var attempts = 0

                while (attempts < MAX_RETRY && !loginSuccess) {
                    Log.d(TAG, "Login Attempt ${attempts + 1} of $MAX_RETRY")

                    loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                        override fun onStatusUpdate(message: String) {
                            Log.d(TAG, "Status update: $message")
                        }
                        override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                            syncScope.launch(Dispatchers.Main) { AppBridge.currentOtpResolver.value = resolver }
                        }
                    })

                    if (!loginSuccess) {
                        attempts++
                        Log.e(TAG, "Login or Captcha failed. Retrying... ($attempts/$MAX_RETRY)")
                        client.reinitializeSession(context) // Wipes old cookies for a fresh try
                    }
                }

                if (!loginSuccess) {
                    throw Exception("Failed to bypass Captcha after $MAX_RETRY attempts. VTOP might be blocking requests.")
                }

                withContext(Dispatchers.Main) { AppBridge.syncStatus.value = "SYNCING" }

                // ==========================================
                // TASK 1: PRIORITY TAB SYNCING
                // ==========================================
                val priority = priorityTab?.uppercase()
                Log.d(TAG, "Executing Priority Fetch for: $priority")

                // 1. Fetch, Parse, and Update UI for the Active Tab IMMEDIATELY
                when (priority) {
                    "HOME" -> syncTimetable(context, client, semId)
                    "ATTENDANCE" -> syncAttendance(context, client, semId, regNo)
                    "EXAMS" -> syncExams(context, client, semId)
                    "MARKS" -> syncMarks(context, client, semId)
                    "OUTINGS" -> syncOutings(context, client, regNo)
                }

                // 2. Fetch the remaining tabs in the background
                Log.d(TAG, "Priority fetch complete. Fetching remaining data in background...")
                if (priority != "HOME") syncTimetable(context, client, semId)
                if (priority != "ATTENDANCE") syncAttendance(context, client, semId, regNo)
                if (priority != "EXAMS") syncExams(context, client, semId)
                if (priority != "MARKS") syncMarks(context, client, semId)
                if (priority != "OUTINGS") syncOutings(context, client, regNo)

                // 3. Profile is rarely updated, fetch last
                val profileHtml = client.fetchProfileRawHtml(null)
                val profileData = ProfileParser.parse(profileHtml)
                Vault.saveProfile(context, profileData)
                withContext(Dispatchers.Main) { AppBridge.profileState.value = profileData }

                Vault.saveLastSyncTime(context)
                try {
                    NextClassWidget().updateAll(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update widget after sync: ${e.message}")
                }
                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Complete!", Toast.LENGTH_SHORT).show() }

            } catch (e: Exception) {
                Log.e(TAG, "Sync Error", e)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Error: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                withContext(Dispatchers.Main) {
                    AppBridge.syncStatus.value = "IDLE"
                    isSyncing.value = false
                }
            }
        }
    }

    // --- MODULAR SYNC FUNCTIONS ---

    private suspend fun syncTimetable(context: Context, client: VtopClient, semId: String) {
        val html = client.fetchTimetableRawHtml(semId, null)
        val data = TimetableParser.parse(html)
        Vault.saveTimetable(context, data)
        withContext(Dispatchers.Main) { AppBridge.timetableState.value = data }
    }

    private suspend fun syncAttendance(context: Context, client: VtopClient, semId: String, regNo: String) {
        val html = client.fetchAttendanceRawHtml(semId, null)
        val data = AttendanceParser.parseSummary(html)
        for (course in data) {
            val cId = course.courseId ?: continue
            val cType = course.courseType ?: continue
            val detailHtml = client.fetchAttendanceDetailRawHtml(semId, cId, cType, regNo, null)
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
        // 1. Fetch the raw Map from your client
        val fetchedSemesters = client.fetchSemesters()

        // 2. Convert it into your data model
        val mappedOptions = fetchedSemesters.map { map ->
            com.vtop.models.SemesterOption(
                id = map["id"] ?: "",
                name = map["name"] ?: ""
            )
        }

        // 3. Save it to the Vault so the Profile UI can see it!
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

    private suspend fun syncOutings(context: Context, client: VtopClient, regNo: String) {
        val genHtml = client.fetchGeneralOutingRawHtml(regNo, null)
        val weekHtml = client.fetchWeekendOutingRawHtml(regNo, null)
        val allOutings = OutingParser.parseGeneral(genHtml ?: "") + OutingParser.parseWeekend(weekHtml ?: "")
        Vault.saveOutings(context, allOutings)
        withContext(Dispatchers.Main) { AppBridge.outingsState.value = allOutings }
    }
}