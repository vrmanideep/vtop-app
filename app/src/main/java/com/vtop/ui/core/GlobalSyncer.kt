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
import com.vtop.utils.NotificationHelper
import com.vtop.utils.Vault
import com.vtop.logic.*
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

    // Instantly aborts the coroutine and resets the UI state
    fun cancelActiveSync() {
        Log.i(TAG, "User explicitly requested sync cancellation.")
        activeSyncJob?.cancel()
        isSyncing.value = false
        AppBridge.syncStatus.value = "IDLE"
    }

    fun performSync(context: Context, priorityTab: String? = null, forceNewSession: Boolean = false) {
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

                if (forceNewSession) {
                    Log.i(TAG, "Force Refresh Requested: Wiping existing session cookies.")
                    client.reinitializeSession(context)
                }

                var loginSuccess = false
                var attempts = 0

                while (attempts < MAX_RETRY && !loginSuccess) {
                    Log.d(TAG, "Login Attempt ${attempts + 1} of $MAX_RETRY")

                    try {
                        loginSuccess = client.autoLogin(context, object : VtopClient.LoginListener {
                            override fun onStatusUpdate(message: String) {
                                Log.d(TAG, "Status update: $message")
                            }

                            override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                // Launch a background coroutine to handle the suspend functions safely
                                syncScope.launch(Dispatchers.IO) {
                                    val savedEmail = Vault.getGoogleEmail(context)

                                    // 1. Attempt Silent Interception First
                                    if (savedEmail.isNotBlank()) {
                                        val extractedOtp = GmailOtpExtractor.getLatestVtopOtp(context, savedEmail)
                                        if (extractedOtp != null) {
                                            Log.d("SYNC", "Silently intercepted OTP: $extractedOtp")
                                            resolver.submit(extractedOtp)
                                            return@launch // Exit coroutine, OTP is handled
                                        }
                                    }

                                    // 2. Fallback if interception fails or isn't set up
                                    if (AppBridge.isAppInForeground) {
                                        withContext(Dispatchers.Main) {
                                            AppBridge.currentOtpResolver.value = resolver
                                        }
                                    } else {
                                        Log.w(TAG, "OTP Required but app is in background. Pushing Interactive Notification.")

                                        val deferredOtp = kotlinx.coroutines.CompletableDeferred<String?>()
                                        AppBridge.pendingOtpDeferred = deferredOtp

                                        NotificationHelper.showOtpNotification(context)

                                        val userOtp = withTimeoutOrNull(180_000L) {
                                            deferredOtp.await()
                                        }

                                        if (userOtp != null) {
                                            Log.d(TAG, "OTP received from notification. Resuming sync...")
                                            resolver.submit(userOtp)
                                        } else {
                                            Log.w(TAG, "OTP request timed out. Discarding safely.")
                                            resolver.cancel()
                                            AppBridge.pendingOtpDeferred = null

                                            NotificationHelper.dismissNotification(context, NotificationHelper.OTP_NOTIFICATION_ID)
                                            NotificationHelper.showNotification(
                                                context = context,
                                                title = "Sync Aborted",
                                                message = "VTOP OTP request timed out while you were away.",
                                                notificationId = 997
                                            )
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
                    } catch (e: VtopException.CaptchaFailed) {
                        Log.w(TAG, "Attempt ${attempts + 1} failed: Captcha incorrect")
                        loginSuccess = false
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
                                Toast.makeText(context, "Login failed. Retrying...", Toast.LENGTH_SHORT).show()
                            }
                            Log.e(TAG, "Login or Captcha failed. Retrying... ($attempts/$MAX_RETRY)")
                            client.reinitializeSession(context)
                        }
                    }
                }

                if (!loginSuccess) {
                    throw Exception("Failed to login after $MAX_RETRY attempts. VTOP might be blocking requests.")
                }

                withContext(Dispatchers.Main) { AppBridge.syncStatus.value = "SYNCING" }

                val priority = priorityTab?.uppercase()
                Log.d(TAG, "Executing Priority Fetch for: $priority")

                when (priority) {
                    "HOME" -> syncTimetable(context, client, semId)
                    "ATTENDANCE" -> syncAttendance(context, client, semId, regNo)
                    "EXAMS" -> syncExams(context, client, semId)
                    "MARKS" -> syncMarks(context, client, semId)
                    "OUTINGS" -> syncOutings(context, client, regNo)
                }

                Log.d(TAG, "Priority fetch complete. Fetching remaining data in background...")
                if (priority != "HOME") syncTimetable(context, client, semId)
                if (priority != "ATTENDANCE") syncAttendance(context, client, semId, regNo)
                if (priority != "EXAMS") syncExams(context, client, semId)
                if (priority != "MARKS") syncMarks(context, client, semId)
                if (priority != "OUTINGS") syncOutings(context, client, regNo)

                val profileHtml = client.fetchProfileRawHtml(null)
                val profileData = ProfileParser.parse(profileHtml)
                Vault.saveProfile(context, profileData)
                withContext(Dispatchers.Main) { AppBridge.profileState.value = profileData }

                Vault.saveLastSyncTime(context)
                try { NextClassWidget().updateAll(context) } catch (e: Exception) { Log.e(TAG, "Widget update failed") }

                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Complete!", Toast.LENGTH_SHORT).show() }

            } catch (e: CancellationException) {
                Log.i(TAG, "Sync aborted by CancellationException.")
                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Cancelled", Toast.LENGTH_SHORT).show() }
            } catch (e: VtopException.InvalidCredentials) {
                Log.e(TAG, "Sync Error: Invalid credentials", e)
                NotificationHelper.showNotification(context, "VTOP Sync Failed", "Your password may have changed. Please log in again.", 999)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Error: Invalid Credentials", Toast.LENGTH_LONG).show() }
            } catch (e: VtopException.AuthenticationFailed) {
                Log.e(TAG, "Sync Error: Account locked", e)
                NotificationHelper.showNotification(context, "VTOP Account Locked", "Max attempts reached. Please login in VTOP manually.", 998)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Sync Error: Account Locked", Toast.LENGTH_LONG).show() }
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

    private suspend fun syncOutings(context: Context, client: VtopClient, regNo: String) {
        val genHtml = client.fetchGeneralOutingRawHtml(regNo, null)
        val weekHtml = client.fetchWeekendOutingRawHtml(regNo, null)
        val allOutings = OutingParser.parseGeneral(genHtml ?: "") + OutingParser.parseWeekend(weekHtml ?: "")
        Vault.saveOutings(context, allOutings)
        withContext(Dispatchers.Main) { AppBridge.outingsState.value = allOutings }
    }
}