@file:Suppress("SpellCheckingInspection", "DEPRECATION")

package com.vtop.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.vtop.core.*
import com.vtop.models.TimetableModel
import com.vtop.network.VtopClient
import com.vtop.sync.*
import com.vtop.ui.screens.auth.GoogleSignInDialog
import com.vtop.ui.screens.main.*
import com.vtop.ui.screens.sub.*
import com.vtop.ui.theme.*
import com.vtop.utils.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
        private lateinit var sharedPrefs: SharedPreferences
        private val isDataLoaded = mutableStateOf(false)
        private val updateTriggerFlow = MutableStateFlow(false)

    override fun onResume() {
        super.onResume()
        AppState.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        AppState.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        if (intent.getBooleanExtra("SHOW_UPDATE", false) || intent.action == "SHOW_UPDATE") {
            updateTriggerFlow.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPrefs = getSharedPreferences("VTOP_PREFS", MODE_PRIVATE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        FirebaseApp.initializeApp(this)
        val vaultPrefs = getSharedPreferences("VTOP_VAULT", MODE_PRIVATE)

        val currentAppVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }

        val savedAppVersion = sharedPrefs.getString("SAVED_APP_VERSION", "0.0.0") ?: "0.0.0"

        if (savedAppVersion != currentAppVersion) {
            vaultPrefs.edit {
                remove("OFFLINE_SEM_OPTIONS")
                remove("OFFLINE_CAL_SEM_OPTIONS")
                vaultPrefs.all.keys.forEach { key ->
                    if (key.startsWith("OFFLINE_ACADEMIC_CALENDAR_")) remove(key)
                }
            }
            sharedPrefs.edit { putString("SAVED_APP_VERSION", currentAppVersion) } // FIX: KTX Extension
            intent.putExtra("TRIGGER_INITIAL_SYNC", true)
        }

        val savedThemeString = sharedPrefs.getString("APP_THEME", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        ThemeManager.themeMode.value = try { AppThemeMode.valueOf(savedThemeString) } catch (_: IllegalArgumentException) { AppThemeMode.DARK }

        ThemeManager.useDynamicColor.value = sharedPrefs.getBoolean("USE_DYNAMIC_COLOR", true)
        val defaultAccentInt = VtopPrimaryBlue.toArgb()
        ThemeManager.customAccent.value = Color(sharedPrefs.getInt("CUSTOM_ACCENT", defaultAccentInt))

        NotificationHelper.createNotificationChannel(this)

        if (intent?.getBooleanExtra("SHOW_UPDATE", false) == true || intent?.action == "SHOW_UPDATE") {
            updateTriggerFlow.value = true
        }

        val autoSyncInterval = try { sharedPrefs.getInt("AUTO_SYNC_INTERVAL", 8) } catch (_: ClassCastException) {
            val legacy = sharedPrefs.getLong("AUTO_SYNC_INTERVAL", 8L).toInt()
            sharedPrefs.edit { putInt("AUTO_SYNC_INTERVAL", legacy) } // FIX: KTX Extension
            legacy
        }

        if (autoSyncInterval > 0) {
            val syncRequest = PeriodicWorkRequestBuilder<VtopSyncWorker>( // FIX: Removed redundant qualifier
                autoSyncInterval.toLong(), TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork("VTOP_BACKGROUND_SYNC", ExistingPeriodicWorkPolicy.KEEP, syncRequest)
        } else {
            WorkManager.getInstance(this).cancelUniqueWork("VTOP_BACKGROUND_SYNC")
        }

        val testAttendanceWorker = OneTimeWorkRequestBuilder<VtopSyncWorker>().build() // FIX: Removed redundant qualifier
        WorkManager.getInstance(this).enqueue(testAttendanceWorker)

        lifecycleScope.launch(Dispatchers.IO) {
            // Silently wipe any leftover outpass PDFs from the cache
            cacheDir.listFiles { file -> file.extension == "pdf" }?.forEach { it.delete() }

            AppRepositories.loadAll(this@MainActivity)
            withContext(Dispatchers.Main) {
                isDataLoaded.value = true
            }
        }

        setContent {
            LaunchedEffect(Unit) {
                EventBus.events.collect { event ->
                    when (event) {
                        is AppEvent.SyncStatusChanged -> AppState.syncStatus.value = event.status
                        is AppEvent.ToastMessage -> Toast.makeText(this@MainActivity, event.message, if (event.isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
                        is AppEvent.SyncError -> AppState.appError.value = event.exception.message
                        is AppEvent.AuthOtpRequested -> {
                            val resolver = event.resolver as VtopClient.OtpResolver
                            if (AppState.isAppInForeground) {
                                AppState.currentOtpResolver.value = resolver
                            } else {
                                val deferredOtp = CompletableDeferred<String?>()
                                AppState.pendingOtpDeferred = deferredOtp
                                NotificationHelper.showOtpNotification(this@MainActivity)
                                val userOtp = withTimeoutOrNull(180.seconds) { deferredOtp.await() }
                                if (userOtp != null) resolver.submit(userOtp)
                                else {
                                    resolver.cancel()
                                    AppState.pendingOtpDeferred = null
                                    NotificationHelper.dismissNotification(this@MainActivity, NotificationHelper.OTP_NOTIFICATION_ID)
                                    SyncManager.cancelActiveSync()
                                }
                            }
                        }
                        is AppEvent.SyncCompleted -> { }
                        is AppEvent.CalendarUpdated -> { }
                    }
                }
            }

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

            AppShortcuts.setupDynamicShortcuts(this)
            val shortcutAction = intent?.action

            var showOtaGooglePrompt by remember { mutableStateOf(!Vault.hasPromptedGoogleSignIn(this@MainActivity) && Vault.getGoogleEmail(this@MainActivity).isEmpty()) }
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var isDownloadingUpdate by remember { mutableStateOf(false) }
            val triggerUpdate by updateTriggerFlow.collectAsState()

            LaunchedEffect(triggerUpdate) {
                if (triggerUpdate) {
                    try {
                        val info = UpdateManager.checkForUpdates()
                        if (info.isUpdateAvailable) updateInfo = info
                    } catch (_: Exception) { }
                    updateTriggerFlow.value = false
                }
            }

            LaunchedEffect(Unit) {
                try {
                    val info = UpdateManager.checkForUpdates()
                    if (info.isUpdateAvailable) updateInfo = info
                } catch (_: Exception) { }
            }

            val triggerInitialSync = remember { intent.getBooleanExtra("TRIGGER_INITIAL_SYNC", false) }

            LaunchedEffect(triggerInitialSync) {
                if (triggerInitialSync && !SyncManager.isSyncing.value) {
                    intent.putExtra("TRIGGER_INITIAL_SYNC", false)
                    SyncManager.performSync(this@MainActivity, "PROFILE", false)
                }
            }

            AppTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Crossfade(targetState = isDataLoaded.value, animationSpec = tween(500), label = "DataLoadTransition") { loaded ->
                        if (loaded) {
                            val navController = rememberNavController()

                            NavHost(navController = navController, startDestination = "main") {
                                composable(
                                    route = "main",
                                    enterTransition = { EnterTransition.None },
                                    exitTransition = { ExitTransition.None },
                                    popEnterTransition = { EnterTransition.None },
                                    popExitTransition = { ExitTransition.None }
                                ) {
                                    MainScreen(
                                        navController = navController,
                                        initialShortcutAction = shortcutAction,
                                        onSyncClick = { activeTab, forceNewSession ->
                                            lifecycleScope.launch { SyncManager.performSync(this@MainActivity, activeTab, forceNewSession) }
                                        },
                                        onLogoutClick = {
                                            sharedPrefs.edit { putBoolean("IS_EXPLICITLY_LOGGED_OUT", true) }
                                            Vault.clearAll(this@MainActivity)
                                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                                            finish()
                                        },
                                        outingHandler = object : OutingActionHandler {
                                            override fun onFetchGeneralFormData(callback: FetchCallback) {
                                                val regNo = Vault.getRegNo(this@MainActivity).takeIf { it.isNotBlank() } ?: (Vault.getCredentials(this@MainActivity)[0] ?: "Unknown")
                                                val dummyData = mapOf("name" to "Student", "regNo" to regNo, "appNo" to "N/A", "gender" to "N/A", "block" to "-", "room" to "-")
                                                callback.onResult(dummyData)
                                            }

                                            override fun onFetchWeekendFormData(callback: FetchCallback) {
                                                val regNo = Vault.getRegNo(this@MainActivity).takeIf { it.isNotBlank() } ?: (Vault.getCredentials(this@MainActivity)[0] ?: "Unknown")
                                                val dummyData = mapOf("name" to "Student", "regNo" to regNo, "appNo" to "N/A", "gender" to "N/A", "block" to "-", "room" to "-", "parentContact" to "0000000000")
                                                callback.onResult(dummyData)
                                            }

                                            override fun onViewPass(id: String, isWeekend: Boolean, onReady: (File?) -> Unit) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val regNo = Vault.getRegNo(this@MainActivity)
                                                        val client = SessionManager.getSyncClient()
                                                            ?: throw Exception("Session expired. Please pull down to sync again.")

                                                        client.authorizedId = regNo
                                                        val tempFile = File(cacheDir, "outpass_$id.pdf")
                                                        val success = client.downloadAndCacheOutpass(id, isWeekend, regNo, tempFile)

                                                        if (success && tempFile.exists()) {
                                                            withContext(Dispatchers.Main) { onReady(tempFile) }
                                                        } else {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(this@MainActivity, "Failed to fetch outpass", Toast.LENGTH_SHORT).show()
                                                                onReady(null)
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                            onReady(null)
                                                        }
                                                    }
                                                }
                                            }

                                            override fun onWeekendSubmit(place: String, purpose: String, date: String, time: String, contact: String, callback: FetchCallback) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val regNo = Vault.getRegNo(this@MainActivity)
                                                        val client = SessionManager.getSyncClient()
                                                            ?: throw Exception("Session expired. Please pull down to sync again.")

                                                        client.authorizedId = regNo
                                                        val responseMsg = client.submitWeekendOuting(place, purpose, date, time, contact)

                                                        withContext(Dispatchers.Main) {
                                                            if (responseMsg == "SUCCESS") {
                                                                callback.onResult(mapOf("success" to "Weekend Request Submitted!"))
                                                                lifecycleScope.launch { SyncManager.performSync(this@MainActivity) }
                                                            } else if (responseMsg.startsWith("ERROR:")) {
                                                                callback.onResult(mapOf("error" to responseMsg.substringAfter("ERROR:")))
                                                            } else {
                                                                callback.onResult(mapOf("error" to "Submission Failed from VTOP"))
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) { callback.onResult(mapOf("error" to (e.message ?: "Unknown Error"))) }
                                                    }
                                                }
                                            }

                                            override fun onGeneralSubmit(place: String, purpose: String, fromDate: String, toDate: String, fromTime: String, toTime: String, callback: FetchCallback) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val regNo = Vault.getRegNo(this@MainActivity)
                                                        val client = SessionManager.getSyncClient()
                                                            ?: throw Exception("Session expired. Please pull down to sync again.")

                                                        client.authorizedId = regNo
                                                        val responseMsg = client.submitGeneralOuting(place, purpose, fromDate, toDate, fromTime, toTime)

                                                        withContext(Dispatchers.Main) {
                                                            if (responseMsg == "SUCCESS") {
                                                                callback.onResult(mapOf("success" to "General Leave Submitted!"))
                                                                lifecycleScope.launch { SyncManager.performSync(this@MainActivity) }
                                                            } else if (responseMsg.startsWith("ERROR:")) {
                                                                callback.onResult(mapOf("error" to responseMsg.substringAfter("ERROR:")))
                                                            } else {
                                                                callback.onResult(mapOf("error" to "Submission Failed from VTOP"))
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) { callback.onResult(mapOf("error" to (e.message ?: "Unknown Error"))) }
                                                    }
                                                }
                                            }

                                            override fun onDelete(id: String, isWeekend: Boolean) {
                                                lifecycleScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val regNo = Vault.getRegNo(this@MainActivity)
                                                        val client = SessionManager.getSyncClient()
                                                            ?: throw Exception("Session expired. Please pull down to sync again.")

                                                        client.authorizedId = regNo
                                                        val success = client.deleteOuting(id, isWeekend)

                                                        withContext(Dispatchers.Main) {
                                                            if (success) {
                                                                Toast.makeText(this@MainActivity, "Leave Cancelled!", Toast.LENGTH_SHORT).show()
                                                                lifecycleScope.launch { SyncManager.performSync(this@MainActivity) }
                                                            } else {
                                                                Toast.makeText(this@MainActivity, "Failed to cancel request.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                composable("simulator") {
                                    val semInfo = Vault.getSelectedSemester(this@MainActivity)
                                    val currentSemName = semInfo[1] ?: semInfo[0] ?: "Unknown Semester"
                                    val timetable by TimetableRepository.timetable.collectAsState()
                                    val attendanceData by AttendanceRepository.attendance.collectAsState()

                                    BunkSimulatorTab(
                                        timetable = timetable ?: TimetableModel(),
                                        attendanceData = attendanceData,
                                        selectedSemester = currentSemName,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        } else {
                            VtopSplashScreen()
                        }
                    }

                    if (showOtaGooglePrompt && isDataLoaded.value) {
                        GoogleSignInDialog(
                            onDismiss = { showOtaGooglePrompt = false },
                            onSuccess = { showOtaGooglePrompt = false }
                        )
                    }

                    updateInfo?.let { info ->
                        AlertDialog(
                            onDismissRequest = { updateInfo = null },
                            title = {
                                Column {
                                    Text("Update Available", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                    if (info.releaseTitle.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(text = info.releaseTitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(text = "Version ${info.latestVersion} is ready to download. Do you want to install it now?", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                    if (info.features.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("✨ Features", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                                            info.features.forEach { feature ->
                                                Row(verticalAlignment = Alignment.Top) {
                                                    Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                                                    Text(feature, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                    if (info.fixes.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("🛠 Fixes", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), letterSpacing = 0.5.sp)
                                            info.fixes.forEach { fix ->
                                                Row(verticalAlignment = Alignment.Top) {
                                                    Text("•", color = Color(0xFF10B981), modifier = Modifier.padding(end = 8.dp))
                                                    Text(fix, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        isDownloadingUpdate = true
                                        UpdateManager.downloadAndInstallUpdate(context = this@MainActivity, downloadUrl = info.downloadUrl, version = info.latestVersion)
                                        updateInfo = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(if (isDownloadingUpdate) "Downloading..." else "Update Now", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VtopSplashScreen() {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(2.dp, AppColors.glassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {Text(text = "V", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)}
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "VTOP", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}