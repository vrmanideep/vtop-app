@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE")

package com.vtop.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.vtop.network.VtopClient
import com.vtop.logic.*
import com.vtop.ui.core.AppBridge
import com.vtop.ui.core.LoginBridge
import com.vtop.ui.screens.auth.LoginScreen
import com.vtop.ui.theme.AppTheme
import com.vtop.ui.theme.AppThemeMode
import com.vtop.ui.theme.AuthActionCallback
import com.vtop.ui.theme.AuthState
import com.vtop.utils.Vault
import com.vtop.widget.NextClassWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LoginActivity : ComponentActivity() {

    // THIS IS THE FIX: We store the active session here after the first login succeeds
    private var activeVtopClient: VtopClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. SESSION CHECK
        val sharedPrefs = getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
        val isExplicitlyLoggedOut = sharedPrefs.getBoolean("IS_EXPLICITLY_LOGGED_OUT", false)

        val credentials = Vault.getCredentials(this)
        val savedReg = credentials[0]
        val savedPass = credentials[1]

        if (!isExplicitlyLoggedOut && !savedReg.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val savedThemeString = sharedPrefs.getString("APP_THEME", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val savedTheme = AppThemeMode.valueOf(savedThemeString)

        LoginBridge.currentState.value = AuthState.FORM

        setContent {
            AppTheme(themeMode = savedTheme) {
                LoginScreen(
                    savedReg = savedReg,
                    savedPass = savedPass,
                    callback = object : AuthActionCallback {

                        override fun onLoginSubmit(regNo: String, pass: String) {
                            Vault.saveCredentials(this@LoginActivity, regNo, pass)
                            sharedPrefs.edit().putBoolean("IS_EXPLICITLY_LOGGED_OUT", false).apply()

                            LoginBridge.currentState.value = AuthState.LOADING_SEMESTERS

                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val client = VtopClient(this@LoginActivity, regNo, pass)
                                    client.reinitializeSession(this@LoginActivity)

                                    var loginSuccess = false
                                    var attempts = 0
                                    val maxAttempts = 3

                                    // --- CAPTCHA AUTO-RETRY LOOP ---
                                    while (!loginSuccess && attempts < maxAttempts) {
                                        attempts++
                                        if (attempts > 1) {
                                            withContext(Dispatchers.Main) {
                                                LoginBridge.loginError.value = "Retrying login ($attempts/$maxAttempts)..."
                                            }
                                            delay(1000) // Brief pause before retry
                                        }

                                        loginSuccess = client.autoLogin(this@LoginActivity, object : VtopClient.LoginListener {
                                            override fun onStatusUpdate(message: String) {
                                                Log.d("VTOP_LOGIN", message)
                                            }

                                            override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                                lifecycleScope.launch(Dispatchers.Main) {
                                                    AppBridge.currentOtpResolver.value = resolver
                                                }
                                            }
                                        })
                                    }

                                    if (loginSuccess) {
                                        // SAVE THE ACTIVE CLIENT SO WE DON'T LOSE THE SESSION COOKIES
                                        activeVtopClient = client

                                        val semestersList = client.fetchSemesters()
                                        val firstSemName = semestersList.firstOrNull()?.get("name") ?: ""
                                        val currentIndex = getActiveSemesterIndex(this@LoginActivity, firstSemName)

                                        val processedSemesters = semestersList.mapIndexed { index, map ->
                                            map.toMutableMap().apply {
                                                this["isCurrent"] = (index == currentIndex).toString()
                                            }
                                        }

                                        withContext(Dispatchers.Main) {
                                            LoginBridge.loginError.value = null // Clear any retry messages
                                            LoginBridge.fetchedSemesters.value = processedSemesters
                                            LoginBridge.currentState.value = AuthState.SELECT_SEMESTER
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            LoginBridge.loginError.value = "Invalid Credentials or VTOP is down."
                                            LoginBridge.currentState.value = AuthState.FORM
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        LoginBridge.loginError.value = "Network error: ${e.message}"
                                        LoginBridge.currentState.value = AuthState.FORM
                                    }
                                }
                            }
                        }

                        override fun onSemesterSelect(semId: String, semName: String) {
                            LoginBridge.currentState.value = AuthState.DOWNLOADING_DATA

                            lifecycleScope.launch(Dispatchers.IO) {
                                Vault.saveSelectedSemester(this@LoginActivity, semId, semName)

                                val currentCreds = Vault.getCredentials(this@LoginActivity)
                                val reg = currentCreds[0] ?: ""

                                // GRAB THE SAVED CLIENT INSTEAD OF CREATING A NEW ONE
                                val client = activeVtopClient
                                if (client == null) {
                                    Log.e("LOGIN_SYNC", "Critical Error: Session client was lost between screens.")
                                    withContext(Dispatchers.Main) {
                                        LoginBridge.loginError.value = "Session lost. Please try logging in again."
                                        LoginBridge.currentState.value = AuthState.FORM
                                    }
                                    return@launch
                                }

                                try {
                                    val profileHtml = client.fetchProfileRawHtml(null)
                                    val timetableHtml = client.fetchTimetableRawHtml(semId, null)
                                    val attendanceHtml = client.fetchAttendanceRawHtml(semId, null)
                                    val examsHtml = client.fetchExamScheduleRawHtml(semId, null)
                                    val marksHtml = client.fetchMarksRawHtml(semId, null)
                                    val gradesHtml = client.fetchGradesRawHtml(semId, null)
                                    val historyHtml = client.fetchHistoryRawHtml(null)
                                    val generalOutingHtml = client.fetchGeneralOutingRawHtml(reg, null)
                                    val weekendOutingHtml = client.fetchWeekendOutingRawHtml(reg, null)

                                    val timetableData = try { TimetableParser.parse(timetableHtml) } catch (e: Exception) { Log.e("LOGIN_SYNC", "Timetable parse failed", e); com.vtop.models.TimetableModel() }
                                    val examsData = try { ExamScheduleParser.parse(examsHtml) } catch (e: Exception) { Log.e("LOGIN_SYNC", "Exams parse failed", e); emptyList() }
                                    val attendanceData = try { AttendanceParser.parseSummary(attendanceHtml) } catch (e: Exception) { Log.e("LOGIN_SYNC", "Attendance parse failed", e); emptyList() }
                                    val marksData = try { MarksParser.parseMarks(marksHtml) } catch (e: Exception) { Log.e("LOGIN_SYNC", "Marks parse failed", e); emptyList() }
                                    val gradesData = try { MarksParser.parseGrades(gradesHtml) } catch (e: Exception) { Log.e("LOGIN_SYNC", "Grades parse failed", e); emptyList() }
                                    val historyPair = try { MarksParser.parseHistory(historyHtml) } catch (e: Exception) { Log.e("LOGIN_SYNC", "History parse failed", e); Pair(com.vtop.models.CGPASummary("", "", ""), emptyList()) }
                                    val allOutings = try { OutingParser.parseGeneral(generalOutingHtml ?: "") + OutingParser.parseWeekend(weekendOutingHtml ?: "") } catch (e: Exception) { Log.e("LOGIN_SYNC", "Outings parse failed", e); emptyList() }
                                    val profileData = try { ProfileParser.parse(profileHtml) } catch (e: Exception) { Log.e("LOGIN_SYNC", "Profile parse failed", e); emptyMap() }

                                    for (course in attendanceData) {
                                        try {
                                            val cId = course.courseId ?: continue
                                            val cType = course.courseType ?: continue
                                            val detailHtml = client.fetchAttendanceDetailRawHtml(semId, cId, cType, reg, null)
                                            AttendanceParser.parseDetailAndUpdate(detailHtml, course)
                                        } catch (e: Exception) {
                                            Log.e("LOGIN_SYNC", "Detail parse failed for course", e)
                                        }
                                    }

                                    // 1. SAVE TO VAULT
                                    Vault.saveTimetable(this@LoginActivity, timetableData)
                                    Vault.saveAttendance(this@LoginActivity, attendanceData)
                                    Vault.saveExamSchedule(this@LoginActivity, examsData)
                                    Vault.saveMarks(this@LoginActivity, marksData)
                                    Vault.saveGrades(this@LoginActivity, gradesData)
                                    Vault.saveHistory(this@LoginActivity, historyPair.second)
                                    Vault.saveCGPASummary(this@LoginActivity, historyPair.first)
                                    Vault.saveOutings(this@LoginActivity, allOutings)
                                    Vault.saveProfile(this@LoginActivity, profileData)
                                    Vault.saveLastSyncTime(this@LoginActivity)

                                    // 2. LIVE UPDATE THE APP BRIDGE SO MAIN SCREEN SEES IT IMMEDIATELY
                                    withContext(Dispatchers.Main) {
                                        AppBridge.timetableState.value = timetableData
                                        AppBridge.attendanceState.value = attendanceData
                                        AppBridge.examsState.value = examsData
                                        AppBridge.marksState.value = marksData
                                        AppBridge.gradesState.value = gradesData
                                        AppBridge.historyItemsState.value = historyPair.second
                                        AppBridge.historySummaryState.value = historyPair.first
                                        AppBridge.outingsState.value = allOutings
                                        AppBridge.profileState.value = profileData
                                    }

                                    try {
                                        NextClassWidget().updateAll(this@LoginActivity)
                                    } catch (e: Exception) {
                                        Log.e("WIDGET_UPDATE", "Failed to update widget: ${e.message}")
                                    }

                                } catch (e: Exception) {
                                    Log.e("LOGIN_SYNC", "Parsing loop failed catastrophically: ${e.message}", e)
                                }

                                withContext(Dispatchers.Main) {
                                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                    finish()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun getActiveSemesterIndex(context: Context, topSemesterName: String): Int {
        if (topSemesterName.isEmpty()) return 0
        try {
            val jsonString = context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            if (!jsonObject.has("blocked_dates")) return 0

            val blocked = jsonObject.getJSONObject("blocked_dates")
            val keys = blocked.keys()

            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            val today = Calendar.getInstance()
            val currentYear = today.get(Calendar.YEAR)
            val todayDate = sdf.parse(sdf.format(today.time)) ?: return 0

            while (keys.hasNext()) {
                val dateKey = keys.next()
                val eventName = blocked.getString(dateKey)
                val normalizedEvent = eventName.replace("Commencement of ", "", ignoreCase = true).trim()

                if (topSemesterName.contains(normalizedEvent, ignoreCase = true) || eventName.contains(topSemesterName, ignoreCase = true)) {
                    val targetDate = sdf.parse("$dateKey-$currentYear")
                    if (targetDate != null && targetDate.after(todayDate)) {
                        return 1
                    }
                }
            }
        } catch (_: Exception) { }
        return 0
    }
}