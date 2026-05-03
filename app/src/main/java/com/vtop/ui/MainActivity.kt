package com.vtop.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.vtop.models.TimetableModel
import com.vtop.network.VtopClient
import com.vtop.ui.core.AppBridge
import com.vtop.ui.core.GlobalSyncer
import com.vtop.ui.screens.main.FetchCallback
import com.vtop.ui.screens.main.MainScreen
import com.vtop.ui.screens.main.OutingActionHandler
import com.vtop.ui.theme.*
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.vtop.utils.NotificationHelper
import com.vtop.ui.core.VtopSyncWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Makes the status bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Load your data from the Vault
        AppBridge.timetableState.value = Vault.getTimetable(this)
        AppBridge.attendanceState.value = Vault.getAttendance(this) ?: emptyList()
        AppBridge.examsState.value = Vault.getExamSchedule(this) ?: emptyList()
        AppBridge.outingsState.value = Vault.getOutings(this) ?: emptyList()
        AppBridge.marksState.value = Vault.getMarks(this) ?: emptyList()
        AppBridge.gradesState.value = Vault.getGrades(this) ?: emptyList()
        AppBridge.historySummaryState.value = Vault.getCGPASummary(this)
        AppBridge.historyItemsState.value = Vault.getHistory(this) ?: emptyList()

        val sharedPrefs = getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)

        // 1. Load Theme Mode (With safety catch for old deprecated themes)
        val savedThemeString = sharedPrefs.getString("APP_THEME", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        ThemeManager.themeMode.value = try {
            AppThemeMode.valueOf(savedThemeString)
        } catch (e: IllegalArgumentException) {
            // If it finds "AMOLED" or any other removed theme, default to DARK
            AppThemeMode.DARK
        }

        // 2. Load Material You toggle (Defaults to true)
        ThemeManager.useDynamicColor.value = sharedPrefs.getBoolean("USE_DYNAMIC_COLOR", true)

        // 3. Load Custom Accent Color
        val defaultAccentInt = VtopPrimaryBlue.toArgb()
        val savedAccentInt = sharedPrefs.getInt("CUSTOM_ACCENT", defaultAccentInt)
        ThemeManager.customAccent.value = androidx.compose.ui.graphics.Color(savedAccentInt)

        NotificationHelper.createNotificationChannel(this)


        val syncRequest = PeriodicWorkRequestBuilder<VtopSyncWorker>(4, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "VTOP_BACKGROUND_SYNC",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        setContent {
            val themeMode = ThemeManager.themeMode.value
            val isDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // THE FIX: Use LaunchedEffect so this only fires once when the theme changes, not every frame
            val view = LocalView.current
            val currentWindow = this.window
            if (!view.isInEditMode) {
                androidx.compose.runtime.LaunchedEffect(isDark) {
                    val insetsController = WindowCompat.getInsetsController(currentWindow, view)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }
            // Register the dynamic shortcuts with the OS
            com.vtop.utils.AppShortcuts.setupDynamicShortcuts(this)

            // Capture the intent if the app was launched via a shortcut
            val shortcutAction = intent?.action

            AppTheme(themeMode = themeMode) {
                MainScreen(
                    initialShortcutAction = shortcutAction,
                    timetable = AppBridge.timetableState.value ?: TimetableModel(),
                    attendanceData = AppBridge.attendanceState.value,
                    examsData = AppBridge.examsState.value,
                    onSyncClick = { activeTab -> GlobalSyncer.performSync(this, activeTab) },
                    onLogoutClick = {
                        sharedPrefs.edit { putBoolean("IS_EXPLICITLY_LOGGED_OUT", true) }
                        Vault.clearAll(this)
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    },
                    outingHandler = object : OutingActionHandler {

                        @Suppress("SpellCheckingInspection")
                        override fun onFetchGeneralFormData(callback: FetchCallback) {
                            val creds = Vault.getCredentials(this@MainActivity)
                            val regNo = creds[0] ?: "Unknown"

                            val dummyData = mapOf(
                                "name" to "Student",
                                "regNo" to regNo,
                                "appNo" to "N/A",
                                "gender" to "N/A",
                                "block" to "-",
                                "room" to "-"
                            )
                            callback.onResult(dummyData)
                        }

                        @Suppress("SpellCheckingInspection")
                        override fun onFetchWeekendFormData(callback: FetchCallback) {
                            val creds = Vault.getCredentials(this@MainActivity)
                            val regNo = creds[0] ?: "Unknown"

                            val dummyData = mapOf(
                                "name" to "Student",
                                "regNo" to regNo,
                                "appNo" to "N/A",
                                "gender" to "N/A",
                                "block" to "-",
                                "room" to "-",
                                "parentContact" to "0000000000"
                            )
                            callback.onResult(dummyData)
                        }

                        @Suppress("SpellCheckingInspection")
                        override fun onViewPass(id: String, isWeekend: Boolean, onReady: (File?) -> Unit) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val creds = Vault.getCredentials(this@MainActivity)
                                    val regNo = creds[0]!!
                                    val client = VtopClient(this@MainActivity, regNo, creds[1]!!)

                                    val destinationFile = File(cacheDir, "outpass_$id.pdf")

                                    val success = client.downloadAndCacheOutpass(id, isWeekend, regNo, destinationFile)

                                    withContext(Dispatchers.Main) {
                                        if (success && destinationFile.exists()) {
                                            onReady(destinationFile)
                                        } else {
                                            Toast.makeText(this@MainActivity, "Failed to download PDF", Toast.LENGTH_SHORT).show()
                                            onReady(null)
                                        }
                                    }
                                } catch (_: Exception) {
                                    withContext(Dispatchers.Main) { onReady(null) }
                                }
                            }
                        }

                        override fun onWeekendSubmit(place: String, purpose: String, date: String, time: String, contact: String) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val creds = Vault.getCredentials(this@MainActivity)
                                    val client = VtopClient(this@MainActivity, creds[0]!!, creds[1]!!)

                                    val success = client.submitWeekendOuting(place, purpose, date, time, contact)

                                    withContext(Dispatchers.Main) {
                                        val msg = if (success) "Weekend Request Submitted!" else "Submission Failed"
                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                        if (success) GlobalSyncer.performSync(this@MainActivity)
                                    }
                                } catch (_: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "Error during submission", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        override fun onGeneralSubmit(place: String, purpose: String, fromDate: String, toDate: String, fromTime: String, toTime: String) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val creds = Vault.getCredentials(this@MainActivity)
                                    val client = VtopClient(this@MainActivity, creds[0]!!, creds[1]!!)

                                    val success = client.submitGeneralOuting(place, purpose, fromDate, toDate, fromTime, toTime)

                                    withContext(Dispatchers.Main) {
                                        val msg = if (success) "General Leave Submitted!" else "Submission Failed"
                                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                                        if (success) GlobalSyncer.performSync(this@MainActivity)
                                    }
                                } catch (_: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "Error during submission", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                        override fun onDelete(id: String, isWeekend: Boolean) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    val creds = Vault.getCredentials(this@MainActivity)
                                    val client = VtopClient(this@MainActivity, creds[0]!!, creds[1]!!)

                                    val success = client.deleteOuting(id, isWeekend)

                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            Toast.makeText(this@MainActivity, "Leave Cancelled!", Toast.LENGTH_SHORT).show()
                                            GlobalSyncer.performSync(this@MainActivity)
                                        } else {
                                            Toast.makeText(this@MainActivity, "Failed to cancel request.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }

                    }
                )
            }
        }
    }
}