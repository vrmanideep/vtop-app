package com.vtop.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.vtop.models.TimetableModel
import com.vtop.network.VtopClient
import com.vtop.ui.core.AppBridge
import com.vtop.ui.core.GlobalSyncer
import com.vtop.ui.core.VtopSyncWorker
import com.vtop.ui.screens.main.FetchCallback
import com.vtop.ui.screens.main.MainScreen
import com.vtop.ui.screens.main.OutingActionHandler
import com.vtop.ui.theme.*
import com.vtop.utils.NotificationHelper
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    // Track whether the background loading is finished
    private val isDataLoaded = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Makes the status bar transparent
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 1. Load Theme Mode (With safety catch for old deprecated themes)
        val sharedPrefs = getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
        val savedThemeString = sharedPrefs.getString("APP_THEME", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        ThemeManager.themeMode.value = try {
            AppThemeMode.valueOf(savedThemeString)
        } catch (e: IllegalArgumentException) {
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

        // Asynchronous Data Loading
        lifecycleScope.launch(Dispatchers.IO) {
            val timetable = Vault.getTimetable(this@MainActivity)
            val attendance = Vault.getAttendance(this@MainActivity) ?: emptyList()
            val exams = Vault.getExamSchedule(this@MainActivity) ?: emptyList()
            val outings = Vault.getOutings(this@MainActivity) ?: emptyList()
            val marks = Vault.getMarks(this@MainActivity) ?: emptyList()
            val grades = Vault.getGrades(this@MainActivity) ?: emptyList()
            val historySummary = Vault.getCGPASummary(this@MainActivity)
            val historyItems = Vault.getHistory(this@MainActivity) ?: emptyList()

            withContext(Dispatchers.Main) {
                AppBridge.timetableState.value = timetable
                AppBridge.attendanceState.value = attendance
                AppBridge.examsState.value = exams
                AppBridge.outingsState.value = outings
                AppBridge.marksState.value = marks
                AppBridge.gradesState.value = grades
                AppBridge.historySummaryState.value = historySummary
                AppBridge.historyItemsState.value = historyItems

                // Trigger the UI to transition from Splash to Dashboard
                isDataLoaded.value = true
            }
        }

        setContent {
            val themeMode = ThemeManager.themeMode.value
            val isDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            val view = LocalView.current
            val currentWindow = this.window
            if (!view.isInEditMode) {
                LaunchedEffect(isDark) {
                    val insetsController = WindowCompat.getInsetsController(currentWindow, view)
                    insetsController.isAppearanceLightStatusBars = !isDark
                    insetsController.isAppearanceLightNavigationBars = !isDark
                }
            }

            com.vtop.utils.AppShortcuts.setupDynamicShortcuts(this)
            val shortcutAction = intent?.action

            AppTheme(themeMode = themeMode) {
                // Smoothly crossfade between the Splash Screen and the Main Dashboard
                Crossfade(
                    targetState = isDataLoaded.value,
                    animationSpec = tween(500), // Half-second smooth fade
                    label = "DataLoadTransition"
                ) { loaded ->
                    if (loaded) {
                        MainScreen(
                            initialShortcutAction = shortcutAction,
                            timetable = AppBridge.timetableState.value ?: TimetableModel(),
                            attendanceData = AppBridge.attendanceState.value,
                            examsData = AppBridge.examsState.value,
                            onSyncClick = { activeTab, forceNewSession ->
                                GlobalSyncer.performSync(this@MainActivity, activeTab, forceNewSession)
                            },
                            onLogoutClick = {
                                sharedPrefs.edit { putBoolean("IS_EXPLICITLY_LOGGED_OUT", true) }
                                Vault.clearAll(this@MainActivity)
                                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
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
                    } else {
                        // Display our custom loading screen instead of an empty dashboard
                        VtopSplashScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun VtopSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.dp, AppColors.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "VTOP",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}