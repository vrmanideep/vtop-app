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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
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
import com.mikepenz.markdown.m3.Markdown
import com.vtop.models.TimetableModel
import com.vtop.network.VtopClient
import com.vtop.ui.core.AppBridge
import com.vtop.ui.core.GlobalSyncer
import com.vtop.ui.core.VtopSyncWorker
import com.vtop.ui.screens.main.FetchCallback
import com.vtop.ui.screens.main.MainScreen
import com.vtop.ui.screens.main.OutingActionHandler
import com.vtop.ui.screens.auth.GoogleSignInDialog

// Sub-screens imports
import com.vtop.ui.screens.sub.AcademicCalendarScreen
import com.vtop.ui.screens.sub.BunkSimulatorTab
import com.vtop.ui.screens.sub.FacultyScreen

import com.vtop.ui.theme.*
import com.vtop.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val isDataLoaded = mutableStateOf(false)
    private val updateTriggerFlow = MutableStateFlow(false)

    override fun onResume() {
        super.onResume()
        AppBridge.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        AppBridge.isAppInForeground = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Detect FCM Click while app is in background/foreground
        if (intent.getBooleanExtra("SHOW_UPDATE", false) || intent.action == "SHOW_UPDATE") {
            updateTriggerFlow.value = true
        }
    }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            com.google.firebase.FirebaseApp.initializeApp(this)

            WindowCompat.setDecorFitsSystemWindows(window, false)

            val sharedPrefs = getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            val savedThemeString = sharedPrefs.getString("APP_THEME", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
            ThemeManager.themeMode.value = try {
                AppThemeMode.valueOf(savedThemeString)
            } catch (e: IllegalArgumentException) {
                AppThemeMode.DARK
            }

            ThemeManager.useDynamicColor.value = sharedPrefs.getBoolean("USE_DYNAMIC_COLOR", true)
            val defaultAccentInt = VtopPrimaryBlue.toArgb()
            val savedAccentInt = sharedPrefs.getInt("CUSTOM_ACCENT", defaultAccentInt)
            ThemeManager.customAccent.value = Color(savedAccentInt)

            NotificationHelper.createNotificationChannel(this)

            // Silent OTA Check in the background
            lifecycleScope.launch(Dispatchers.IO) {
                OtaManager.checkForOtaUpdates(this@MainActivity)
            }

            // Detect FCM Click on fresh app launch
            if (intent?.getBooleanExtra("SHOW_UPDATE", false) == true || intent?.action == "SHOW_UPDATE") {
                updateTriggerFlow.value = true
            }

            // --- DYNAMIC BACKGROUND SYNC SCHEDULING ---
            val autoSyncInterval =
                try {
                    sharedPrefs.getInt(
                        "AUTO_SYNC_INTERVAL",
                        8
                    )
                } catch (_: ClassCastException) {

                    val legacy =
                        sharedPrefs.getLong(
                            "AUTO_SYNC_INTERVAL",
                            8L
                        ).toInt()

                    sharedPrefs.edit()
                        .putInt(
                            "AUTO_SYNC_INTERVAL",
                            legacy
                        )
                        .apply()

                    legacy
                }
            if (autoSyncInterval > 0) {
                val syncRequest = PeriodicWorkRequestBuilder<VtopSyncWorker>(autoSyncInterval.toLong(), TimeUnit.HOURS).build()
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "VTOP_BACKGROUND_SYNC",
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
            } else {
                WorkManager.getInstance(this).cancelUniqueWork("VTOP_BACKGROUND_SYNC")
            }

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

                AppShortcuts.setupDynamicShortcuts(this)
                val shortcutAction = intent?.action

                var showOtaGooglePrompt by remember { mutableStateOf(!Vault.hasPromptedGoogleSignIn(this@MainActivity) && Vault.getGoogleEmail(this@MainActivity).isEmpty()) }

                var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                val triggerUpdate by updateTriggerFlow.collectAsState()

                // Triggered by FCM intent
                LaunchedEffect(triggerUpdate) {
                    if (triggerUpdate) {
                        try {
                            val info = UpdateManager.checkForUpdates()
                            if (info.isUpdateAvailable) {
                                updateInfo = info
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        updateTriggerFlow.value = false // Reset trigger
                    }
                }

                // Normal Startup Check
                LaunchedEffect(Unit) {
                    try {
                        val info = UpdateManager.checkForUpdates()
                        if (info.isUpdateAvailable) {
                            updateInfo = info
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val triggerInitialSync = remember { intent.getBooleanExtra("TRIGGER_INITIAL_SYNC", false) }

                LaunchedEffect(triggerInitialSync) {
                    if (triggerInitialSync && !GlobalSyncer.isSyncing.value) {
                        intent.putExtra("TRIGGER_INITIAL_SYNC", false)
                        GlobalSyncer.performSync(this@MainActivity, "PROFILE", false)
                    }
                }

                AppTheme(themeMode = themeMode) {
                    Crossfade(
                        targetState = isDataLoaded.value,
                        animationSpec = tween(500),
                        label = "DataLoadTransition"
                    ) { loaded ->
                        if (loaded) {
                            MainScreen(
                                initialShortcutAction = shortcutAction,
                                timetable = AppBridge.timetableState.value ?: TimetableModel(),
                                attendanceData = AppBridge.attendanceState.value,
                                examsData = AppBridge.examsState.value,
                                onSyncClick = { activeTab, forceNewSession ->
                                    lifecycleScope.launch {
                                        GlobalSyncer.performSync(this@MainActivity, activeTab, forceNewSession)
                                    }
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

                                    override fun onViewPass(
                                        id: String,
                                        isWeekend: Boolean,
                                        onReady: (File?) -> Unit
                                    ) {

                                        lifecycleScope.launch(Dispatchers.IO) {

                                            try {

                                                val creds =
                                                    Vault.getCredentials(
                                                        this@MainActivity
                                                    )

                                                val regNo =
                                                    Vault.getRegNo(
                                                        this@MainActivity
                                                    )

                                                val client =
                                                    VtopClient(
                                                        this@MainActivity,
                                                        creds[0]!!,
                                                        creds[1]!!
                                                    )

                                                client.setAuthorizedId(regNo)

                                                val tempFile =
                                                    File(
                                                        cacheDir,
                                                        "outpass_$id.pdf"
                                                    )

                                                val success =
                                                    client.downloadAndCacheOutpass(
                                                        id,
                                                        isWeekend,
                                                        regNo,
                                                        tempFile
                                                    )

                                                if (
                                                    success &&
                                                    tempFile.exists()
                                                ) {

                                                    val resolver =
                                                        contentResolver

                                                    val fileName =
                                                        "outpass_$id.pdf"

                                                    val values =
                                                        android.content.ContentValues().apply {

                                                            put(
                                                                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                                                                fileName
                                                            )

                                                            put(
                                                                android.provider.MediaStore.MediaColumns.MIME_TYPE,
                                                                "application/pdf"
                                                            )

                                                            put(
                                                                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                                                                android.os.Environment.DIRECTORY_DOWNLOADS
                                                            )
                                                        }

                                                    val collection =
                                                        if (android.os.Build.VERSION.SDK_INT >= 29) {

                                                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI

                                                        } else {

                                                            android.provider.MediaStore.Files.getContentUri(
                                                                "external"
                                                            )
                                                        }

                                                    val uri =
                                                        resolver.insert(
                                                            collection,
                                                            values
                                                        )

                                                    if (uri == null) {

                                                        throw Exception(
                                                            "Failed creating MediaStore entry"
                                                        )
                                                    }

                                                    resolver.openOutputStream(uri)?.use { output ->

                                                        tempFile.inputStream().use { input ->

                                                            input.copyTo(output)
                                                        }
                                                    }

                                                    withContext(Dispatchers.Main) {

                                                        NotificationHelper
                                                            .showDownloadNotificationFromUri(
                                                                context = this@MainActivity,
                                                                uri = uri,
                                                                fileName = fileName,
                                                                title = "Outpass Downloaded",
                                                                description = "Tap to open $fileName"
                                                            )

                                                        Toast.makeText(
                                                            this@MainActivity,
                                                            "Outpass saved to Downloads",
                                                            Toast.LENGTH_SHORT
                                                        ).show()

                                                        onReady(tempFile)
                                                    }

                                                } else {

                                                    withContext(Dispatchers.Main) {

                                                        Toast.makeText(
                                                            this@MainActivity,
                                                            "Failed to download outpass",
                                                            Toast.LENGTH_SHORT
                                                        ).show()

                                                        onReady(null)
                                                    }
                                                }

                                            } catch (e: Exception) {

                                                withContext(Dispatchers.Main) {

                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Error: ${e.message}",
                                                        Toast.LENGTH_LONG
                                                    ).show()

                                                    onReady(null)
                                                }
                                            }
                                        }
                                    }

                                    override fun onWeekendSubmit(place: String, purpose: String, date: String, time: String, contact: String) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                val creds = Vault.getCredentials(this@MainActivity)
                                                val regNo = Vault.getRegNo(this@MainActivity)

                                                val client = VtopClient(this@MainActivity, creds[0]!!, creds[1]!!)
                                                client.setAuthorizedId(regNo)

                                                val success = client.submitWeekendOuting(place, purpose, date, time, contact)

                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(this@MainActivity, if (success) "Weekend Request Submitted!" else "Submission Failed", Toast.LENGTH_LONG).show()
                                                    if (success) { lifecycleScope.launch { GlobalSyncer.performSync(this@MainActivity) } }
                                                }
                                            } catch (_: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error during submission", Toast.LENGTH_SHORT).show() } }
                                        }
                                    }

                                    override fun onGeneralSubmit(place: String, purpose: String, fromDate: String, toDate: String, fromTime: String, toTime: String) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                val creds = Vault.getCredentials(this@MainActivity)
                                                val regNo = Vault.getRegNo(this@MainActivity)

                                                val client = VtopClient(this@MainActivity, creds[0]!!, creds[1]!!)
                                                client.setAuthorizedId(regNo)

                                                val success = client.submitGeneralOuting(place, purpose, fromDate, toDate, fromTime, toTime)

                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(this@MainActivity, if (success) "General Leave Submitted!" else "Submission Failed", Toast.LENGTH_LONG).show()
                                                    if (success) { lifecycleScope.launch { GlobalSyncer.performSync(this@MainActivity) } }
                                                }
                                            } catch (_: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error during submission", Toast.LENGTH_SHORT).show() } }
                                        }
                                    }

                                    override fun onDelete(id: String, isWeekend: Boolean) {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                val creds = Vault.getCredentials(this@MainActivity)
                                                val regNo = Vault.getRegNo(this@MainActivity)

                                                val client = VtopClient(this@MainActivity, creds[0]!!, creds[1]!!)
                                                client.setAuthorizedId(regNo)

                                                val success = client.deleteOuting(id, isWeekend)

                                                withContext(Dispatchers.Main) {
                                                    if (success) { Toast.makeText(this@MainActivity, "Leave Cancelled!", Toast.LENGTH_SHORT).show(); lifecycleScope.launch { GlobalSyncer.performSync(this@MainActivity) } }
                                                    else { Toast.makeText(this@MainActivity, "Failed to cancel request.", Toast.LENGTH_SHORT).show() }
                                                }
                                            } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
                                        }
                                    }
                                }
                            )
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

                    if (updateInfo != null) {
                        AlertDialog(
                            onDismissRequest = { updateInfo = null },
                            title = {
                                Column {
                                    Text("Update Available", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                    if (!updateInfo?.releaseTitle.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); Text(text = updateInfo!!.releaseTitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                                }
                            },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(text = "Version ${updateInfo?.latestVersion} is ready to download. Do you want to install it now?", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                    if (!updateInfo?.releaseNotes.isNullOrBlank()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                        Text(text = "Release Notes:", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        // Replaces \n literals coming from Firebase with true line breaks!
                                        Markdown(content = updateInfo!!.releaseNotes.replace("\\n", "\n"), modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            },
                            confirmButton = { Button(onClick = { isDownloadingUpdate = true; UpdateManager.downloadAndInstallUpdate(context = this@MainActivity, downloadUrl = updateInfo!!.downloadUrl, version = updateInfo!!.latestVersion); updateInfo = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(if (isDownloadingUpdate) "Downloading..." else "Update Now", fontWeight = FontWeight.Bold) } },
                            dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }

@Composable
fun VtopSplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(2.dp, AppColors.glassBorder, CircleShape), contentAlignment = Alignment.Center) { Text(text = "V", color = MaterialTheme.colorScheme.primary, fontSize = 36.sp, fontWeight = FontWeight.Black) }
            Spacer(modifier = Modifier.height(24.dp))
            Text(text = "VTOP", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Spacer(modifier = Modifier.height(32.dp))
            UiverseLoader()
        }
    }
}

@Composable
fun UiverseLoader() {

    val barWidth = remember {
        androidx.compose.animation.core.Animatable(16f)
    }

    val barOffset = remember {
        androidx.compose.animation.core.Animatable(0f)
    }

    val textOffset = remember {
        androidx.compose.animation.core.Animatable(0f)
    }

    val letterSpacing = remember {
        androidx.compose.animation.core.Animatable(1f)
    }

    // =====================================================
    // MAIN LOOP
    // =====================================================

    LaunchedEffect(Unit) {

        while (true) {

            // =============================================
            // PHASE 1
            // =============================================

            launch {

                barWidth.animateTo(
                    targetValue = 80f,
                    animationSpec = tween(1400)
                )
            }

            launch {

                textOffset.animateTo(
                    targetValue = 26f,
                    animationSpec = tween(1400)
                )
            }

            launch {

                letterSpacing.animateTo(
                    targetValue = 2f,
                    animationSpec = tween(1400)
                )
            }

            kotlinx.coroutines.delay(1400)

            // =============================================
            // PHASE 2
            // =============================================

            launch {

                barWidth.animateTo(
                    targetValue = 16f,
                    animationSpec = tween(1400)
                )
            }

            launch {

                barOffset.animateTo(
                    targetValue = 64f,
                    animationSpec = tween(1400)
                )
            }

            launch {

                textOffset.animateTo(
                    targetValue = 32f,
                    animationSpec = tween(1400)
                )
            }

            launch {

                letterSpacing.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(1400)
                )
            }

            kotlinx.coroutines.delay(1400)

            // =============================================
            // RESET
            // =============================================

            launch {

                barWidth.animateTo(
                    targetValue = 80f,
                    animationSpec = tween(350)
                )
            }

            launch {

                barOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(350)
                )
            }

            launch {

                textOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(350)
                )
            }

            launch {

                letterSpacing.animateTo(
                    targetValue = 2f,
                    animationSpec = tween(350)
                )
            }

            kotlinx.coroutines.delay(350)

            // =============================================
            // FINAL RESET
            // =============================================

            launch {

                barWidth.animateTo(
                    targetValue = 16f,
                    animationSpec = tween(350)
                )
            }

            launch {

                letterSpacing.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(350)
                )
            }

            kotlinx.coroutines.delay(350)
        }
    }

    Box(
        modifier = Modifier
            .width(80.dp)
            .height(50.dp)
    ) {

        // =================================================
        // TEXT
        // =================================================

        Text(

            text = "loading",

            color = MaterialTheme.colorScheme.primary,

            fontSize = 12.sp,

            letterSpacing = letterSpacing.value.sp,

            modifier = Modifier.offset(
                x = textOffset.value.dp
            )
        )

        // =================================================
        // MAIN BAR
        // =================================================

        Box(

            modifier = Modifier

                .offset(
                    x = barOffset.value.dp,
                    y = 30.dp
                )

                .width(barWidth.value.dp)

                .height(16.dp)

                .clip(RoundedCornerShape(50.dp))

                .background(MaterialTheme.colorScheme.primary)
        ) {

            Box(

                modifier = Modifier

                    .fillMaxHeight()

                    .fillMaxWidth(0.8f)

                    .clip(RoundedCornerShape(50.dp))

                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
            )
        }
    }
}