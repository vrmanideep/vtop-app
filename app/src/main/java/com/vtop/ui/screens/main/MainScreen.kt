@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE")

package com.vtop.ui.screens.main

import com.vtop.models.SemesterOption
import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.composables.icons.lucide.*
import com.vtop.models.*
import com.vtop.ui.core.*
import com.vtop.ui.screens.portal.VtopPortalScreen
import com.vtop.ui.screens.sub.BunkSimulatorTab
import com.vtop.ui.screens.sub.FacultyScreen
import com.vtop.ui.screens.sub.loadFaculty
import com.vtop.ui.theme.AppColors
import com.vtop.ui.theme.AppThemeMode
import com.vtop.ui.theme.DockPosition
import com.vtop.ui.theme.ThemeManager
import com.vtop.utils.Vault
import com.vtop.widget.NextClassWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterialApi::class)
@SuppressLint("NewApi")
@Composable
fun MainScreen(
    initialShortcutAction: String? = null,
    timetable: TimetableModel,
    attendanceData: List<AttendanceModel>,
    examsData: List<ExamScheduleModel>,
    onSyncClick: (String, Boolean) -> Unit,
    onLogoutClick: Runnable,
    outingHandler: OutingActionHandler
) {
    HomepagePermissionHandler()

    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)

    LaunchedEffect(examsData) {
        if (examsData.isNotEmpty()) {
            AppBridge.isSemesterCompleted.value = com.vtop.utils.SemesterTransitionEngine.checkIfLastFatIsOver(examsData)
        }
    }

    LaunchedEffect(Unit) {
        if (sharedPrefs.contains("CUSTOM_ACCENT")) {
            ThemeManager.customAccent.value = Color(sharedPrefs.getInt("CUSTOM_ACCENT", 0))
        }
        if (sharedPrefs.contains("USE_DYNAMIC_COLOR")) {
            ThemeManager.useDynamicColor.value = sharedPrefs.getBoolean("USE_DYNAMIC_COLOR", true)
        }
        val savedTheme = sharedPrefs.getString("APP_THEME", null)
        if (savedTheme != null) {
            try { ThemeManager.themeMode.value = AppThemeMode.valueOf(savedTheme) } catch (_: Exception) {}
        }
    }

    var navStyle by remember {
        mutableStateOf(Vault.getNavStyle(context).let { if (it.isBlank() || !sharedPrefs.contains("NAV_STYLE_SET")) "STATIC" else it })
    }
    val reminders by remember { mutableStateOf(ReminderManager.loadReminders(context)) }

    var showOutings by remember { mutableStateOf(sharedPrefs.getBoolean("SHOW_OUTINGS", true)) }
    var mergeLabs by remember { mutableStateOf(sharedPrefs.getBoolean("MERGE_LABS", true)) }
    var mergeMarks by remember { mutableStateOf(sharedPrefs.getBoolean("MERGE_MARKS", true)) }

    val navItems = remember(showOutings) {
        val list = mutableListOf("HOME", "ATTENDANCE", "EXAMS", "MARKS")
        if (showOutings) list.add("OUTINGS")
        list.add("PROFILE")
        list
    }

    val initialPage = if (initialShortcutAction == "com.vtop.SHORTCUT_OUTINGS" && navItems.contains("OUTINGS")) {
        navItems.indexOf("OUTINGS")
    } else 0

    var currentTab by remember { mutableStateOf(navItems[initialPage]) }
    val coroutineScope = rememberCoroutineScope()

    var activeOverlay by remember { mutableStateOf<String?>(if (initialShortcutAction == "com.vtop.SHORTCUT_SIMULATOR") "SIMULATOR" else null) }

    val handleSyncAndUpdateWidget = { screen: String, forceNewSession: Boolean ->
        onSyncClick(screen, forceNewSession)
        coroutineScope.launch {
            try { NextClassWidget().updateAll(context) } catch (_: Exception) {}
        }
        Unit
    }

    BackHandler(enabled = activeOverlay != null || currentTab != "HOME") {
        if (activeOverlay != null) {
            activeOverlay = null
        } else {
            currentTab = "HOME"
        }
    }

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val isRefreshing by remember { derivedStateOf { AppBridge.syncStatus.value != "IDLE" } }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { handleSyncAndUpdateWidget(currentTab, false) }
    )

    val errorMsg = AppBridge.appError.value
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            delay(5000)
            AppBridge.appError.value = null
        }
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
            val tabPadding = when (currentTab) {
                "HOME", "EXAMS", "MARKS", "OUTINGS", "PROFILE", "ATTENDANCE"-> PaddingValues(0.dp)
                else -> PaddingValues(top = 80.dp, bottom = if (navStyle == "STATIC" && currentTab != "PROFILE") 96.dp else 20.dp)
            }

            Box(modifier = Modifier.fillMaxSize().padding(tabPadding)) {
                when (currentTab) {
                    "HOME" -> {
                        val holidaysMap = remember {
                            try {
                                val json = try {
                                    com.vtop.utils.OtaManager.getCalendarJson(context)
                                } catch (e: Exception) {
                                    context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
                                }

                                val root = org.json.JSONObject(json)
                                val selectedSemInfo = Vault.getSelectedSemester(context)
                                val selectedSemId = selectedSemInfo[0] ?: ""
                                val selectedSemName = selectedSemInfo[1] ?: ""
                                var matchedSemester: org.json.JSONObject? = null

                                // THE FIX: Fuzzy matching. Checks ID, or exact Key, or selected Name.
                                root.keys().forEach { key ->
                                    if (key != "blocked_dates" && key != "semester") {
                                        val obj = root.optJSONObject(key)
                                        val id = obj?.optString("id", key) ?: key
                                        if (id == selectedSemId || key == selectedSemId || key == selectedSemName) {
                                            matchedSemester = obj
                                        }
                                    }
                                }

                                val holidaysObj = matchedSemester?.optJSONObject("holidays")
                                buildMap<String, String> {
                                    holidaysObj?.keys()?.forEach { date ->
                                        put(date, holidaysObj.optString(date))
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d("HOLIDAY_ERR", e.message ?: "unknown")
                                emptyMap()
                            }
                        }

                        Timetable(
                            timetable = timetable,
                            attendanceData = attendanceData,
                            examsData = examsData,
                            holidays = holidaysMap
                        )
                    }
                    "ATTENDANCE" -> {
                        Attendance(attendanceData = attendanceData, onLaunchSimulator = { activeOverlay = "SIMULATOR" })
                    }
                    "EXAMS" -> {
                        Exams(examsData)
                    }
                    "MARKS" -> {
                        Marks(
                            marksData = AppBridge.marksState.value,
                            historySummary = AppBridge.historySummaryState.value,
                            historyData = AppBridge.historyItemsState.value,
                            onHistoryLoad = {}
                        )
                    }
                    "OUTINGS" -> {
                        VtopOutingsTab(outingsData = AppBridge.outingsState.value, handler = outingHandler)
                    }
                    "PROFILE" -> {
                        val profileStateValue = AppBridge.profileState.value
                        val profileMap = remember(profileStateValue) { profileStateValue?.takeIf { it.isNotEmpty() } ?: Vault.getProfile(context) }
                        Profile(
                            onBack = { currentTab = "HOME" },
                            timetable = timetable,
                            examsData = examsData,
                            onOpenPortal = { activeOverlay = "PORTAL" },
                            currentTheme = ThemeManager.themeMode.value,
                            onThemeChange = { ThemeManager.themeMode.value = it },
                            useDynamicColor = ThemeManager.useDynamicColor.value,
                            onDynamicColorChange = { ThemeManager.useDynamicColor.value = it },
                            customAccent = ThemeManager.customAccent.value,
                            onAccentChange = { ThemeManager.customAccent.value = it },
                            currentNavStyle = navStyle,
                            onNavStyleChange = { navStyle = it },
                            mergeLabs = mergeLabs,
                            onMergeLabsChange = { mergeLabs = it },
                            mergeMarks = mergeMarks,
                            onMergeMarksChange = { mergeMarks = it },
                            showOutings = showOutings,
                            onShowOutingsChange = { showOutings = it },
                            onLogout = { onLogoutClick.run() },
                            profileData = profileMap,
                            selectedSemester = Vault.getSelectedSemester(context)[1] ?: "",
                            availableSemesters = Vault.getSemesterOptions(context),
                            onSemesterChange = { sem ->
                                Vault.saveSelectedSemester(context, sem.id, sem.name)
                                handleSyncAndUpdateWidget(currentTab, true)
                            },
                            currentRegNo = Vault.getCredentials(context)[0] ?: "",
                            currentPass = Vault.getCredentials(context)[1] ?: "",
                            onCredentialsSave = { _, _ -> },
                            reminders = reminders,
                            onDeleteReminder = {},
                            onNavigateToAnalytics = { activeOverlay = "ANALYTICS" },
                            lastSyncTime = Vault.getLastSyncTimestamp(context).toString(),
                            onSyncClick = { handleSyncAndUpdateWidget(currentTab, it) },
                            onNavigateToFaculty = { activeOverlay = "FACULTY" }
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }

        Box(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().zIndex(10f)) {
            GlobalTopBar(
                currentScreen = currentTab,
                onProfileClick = { coroutineScope.launch { currentTab = "PROFILE" } },
                onExportTimetable = {
                    coroutineScope.launch {
                        try {
                            Toast.makeText(context, "Starting export...", Toast.LENGTH_SHORT).show()
                            var client = AppBridge.activeClient
                            if (client == null) {
                                val creds = Vault.getCredentials(context)
                                client = com.vtop.network.VtopClient(context, creds[0] ?: "", creds[1] ?: "")
                                AppBridge.activeClient = client
                            }
                            val result = com.vtop.services.TTExport.exportCurrentSemesterTimetable(context, client)
                            result.onSuccess { Toast.makeText(context, "Timetable exported successfully", Toast.LENGTH_LONG).show() }
                            result.onFailure { Toast.makeText(context, it.message ?: "Export failed", Toast.LENGTH_LONG).show() }
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "Export failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }

        if (navStyle == "STATIC" && currentTab != "PROFILE") {
            Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().zIndex(10f)) {
                BottomNavigation(currentTab, navItems) { tabName -> currentTab = tabName }
            }
        }

        if (navStyle != "STATIC" && currentTab != "PROFILE") {
            Box(
                modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }.align(Alignment.BottomCenter).padding(bottom = 24.dp).zIndex(10f)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                            val hLimit = (screenWidthPx / 2f) - with(density) { 30.dp.toPx() }
                            val vTopLimit = -(screenHeightPx * 0.72f)
                            val vBottomLimit = with(density) { 20.dp.toPx() }
                            offsetX = offsetX.coerceIn(-hLimit, hLimit)
                            offsetY = offsetY.coerceIn(vTopLimit, vBottomLimit)
                        }
                    }
            ) {
                FloatingDockContainer(currentTab, navItems, offsetX, offsetY, screenWidthPx, screenHeightPx, handleSyncAndUpdateWidget) { tabName ->
                    currentTab = tabName
                }
            }
        }

        AnimatedVisibility(
            visible = activeOverlay != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize().zIndex(50f)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                when (activeOverlay) {
                    "SIMULATOR" -> {
                        val semInfo = Vault.getSelectedSemester(context)
                        val currentSemName = semInfo[1] ?: semInfo[0] ?: "Unknown Semester"
                        BunkSimulatorTab(timetable = timetable, attendanceData = attendanceData, selectedSemester = currentSemName, onBack = { activeOverlay = null })
                    }
                    "PORTAL" -> {
                        val creds = Vault.getCredentials(context)
                        val client = remember { com.vtop.network.VtopClient(context, creds[0] ?: "", creds[1] ?: "") }
                        AppBridge.activeClient = client
                        VtopPortalScreen(vtopClient = client, onClose = { activeOverlay = null })
                    }
                    "FACULTY" -> {
                        FacultyScreen(facultyList = loadFaculty(LocalContext.current))
                    }
                }
            }
        }

        val otpResolver = AppBridge.currentOtpResolver.value
        if (otpResolver != null) {
            var showCancelConfirm by remember { mutableStateOf(false) }

            if (showCancelConfirm) {
                AlertDialog(
                    onDismissRequest = { showCancelConfirm = false },
                    title = { Text("Cancel Sync?", fontWeight = FontWeight.Bold) },
                    text = { Text("Do you want to cancel the synchronization process?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCancelConfirm = false
                                otpResolver.cancel()
                                AppBridge.currentOtpResolver.value = null
                                GlobalSyncer.cancelActiveSync()
                                if (activeOverlay == "PORTAL") { activeOverlay = null }
                                coroutineScope.launch {
                                    val profileIndex = navItems.indexOf("PROFILE")
                                    if (profileIndex != -1) currentTab = "PROFILE"
                                }
                            }
                        ) { Text("Yes") }
                    },
                    dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("No") } },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.fillMaxSize().zIndex(100f)) {
                OtpForm(
                    onVerify = { otp -> otpResolver.submit(otp); AppBridge.currentOtpResolver.value = null },
                    onCancel = { showCancelConfirm = true }
                )
            }
        }
    }
}

@Composable
fun SemesterCompletedView() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Lucide.GraduationCap, contentDescription = "Semester Completed", modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            Spacer(Modifier.height(24.dp))
            Text("Semester Completed", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text("Awaiting next semester registration...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
fun GlobalTopBar(
    currentScreen: String,
    onProfileClick: () -> Unit,
    onExportTimetable: () -> Unit = {}
) {
    val context = LocalContext.current
    val syncStatus by AppBridge.syncStatus
    var subtitleText by remember { mutableStateOf("Loading...") }

    LaunchedEffect(syncStatus) {
        if (syncStatus != "IDLE") {
            subtitleText = syncStatus
        } else {
            val lastSyncMillis = Vault.getLastSyncTimestamp(context)
            subtitleText = if (lastSyncMillis == 0L) {
                "Never synced"
            } else {
                val diffMinutes = (System.currentTimeMillis() - lastSyncMillis) / 60000
                when {
                    diffMinutes <= 1L -> "Synced just now"
                    diffMinutes < 60L -> "Synced $diffMinutes mins ago"
                    diffMinutes < 1440L -> "Synced ${diffMinutes / 60L} hrs ago"
                    else -> "Synced ${diffMinutes / 1440L} days ago"
                }
            }
        }
    }

    val displayTitle = remember(currentScreen) {
        when (currentScreen.uppercase(Locale.ROOT)) {
            "HOME" -> "Timetable"
            "ATTENDANCE" -> "Attendance"
            "EXAMS" -> "Exam Schedule"
            "MARKS" -> "Marks & Grades"
            "OUTINGS" -> "Outings"
            "PROFILE" -> "Settings"
            else -> currentScreen
        }
    }

    val pulseAlpha by animateFloatAsState(
        targetValue = if (syncStatus != "IDLE") 0.55f else 1f,
        animationSpec = tween(700),
        label = "syncPulse"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = displayTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = subtitleText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (currentScreen != "PROFILE") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentScreen.uppercase(Locale.ROOT) == "HOME") {
                        IconButton(
                            onClick = onExportTimetable,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Lucide.ArrowDownToLine,
                                contentDescription = "Export Timetable",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onProfileClick,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Lucide.User,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigation(currentTab: String, availableTabs: List<String>, onSelect: (String) -> Unit) {
    val allTabs = listOf(
        Triple("HOME", "Home", Lucide.House),
        Triple("ATTENDANCE", "Attendance", Lucide.CircleCheck),
        Triple("EXAMS", "Exams", Lucide.CalendarDays),
        Triple("MARKS", "Marks", Lucide.ChartNoAxesColumnIncreasing),
        Triple("OUTINGS", "Outings", Lucide.ArrowUpRight)
    )

    val visibleTabs = allTabs.filter { availableTabs.contains(it.first) }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).clip(RoundedCornerShape(24.dp)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            visibleTabs.forEach { item ->
                val (screenId, label, icon) = item
                val isSelected = currentTab.equals(screenId, true)
                val tint by animateColorAsState(targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f), label = "navTint")
                val scale by animateFloatAsState(targetValue = if (isSelected) 1.15f else 1f, label = "navScale")

                Column(
                    modifier = Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(screenId) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp).graphicsLayer(scaleX = scale, scaleY = scale))
                    Spacer(Modifier.height(4.dp))
                    Text(text = label, color = tint, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun FloatingDockContainer(currentScreen: String, items: List<String>, offsetX: Float, offsetY: Float, screenWidthPx: Float, screenHeightPx: Float, onSyncClick: (String, Boolean) -> Unit, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val position = remember(offsetX, offsetY) {
        when {
            offsetX < -(screenWidthPx * 0.35f) -> DockPosition.LEFT
            offsetX > (screenWidthPx * 0.35f) -> DockPosition.RIGHT
            offsetY < -(screenHeightPx * 0.7f) -> DockPosition.TOP
            else -> DockPosition.BOTTOM
        }
    }
    val transformOrigin = when (position) {
        DockPosition.LEFT -> TransformOrigin(0f, 0.5f)
        DockPosition.RIGHT -> TransformOrigin(1f, 0.5f)
        DockPosition.TOP -> TransformOrigin(0.5f, 0f)
        else -> TransformOrigin(0.5f, 1f)
    }

    Layout(
        content = {
            val rotation = when (position) { DockPosition.LEFT -> -90f; DockPosition.RIGHT -> 90f; else -> 0f }
            val isVertical = position == DockPosition.LEFT || position == DockPosition.RIGHT

            Box(modifier = Modifier.size(width = if (isVertical) 44.dp else 140.dp, height = if (isVertical) 140.dp else 44.dp), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.requiredSize(width = 140.dp, height = 44.dp).graphicsLayer { rotationZ = rotation }.clickable { expanded = !expanded },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, AppColors.glassBorder)
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = currentScreen.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                        Icon(imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(start = 4.dp))
                    }
                }
            }

            Box {
                AnimatedVisibility(visible = expanded, enter = fadeIn() + scaleIn(transformOrigin = transformOrigin, animationSpec = tween(200)), exit = fadeOut() + scaleOut(transformOrigin = transformOrigin, animationSpec = tween(150))) {
                    Card(modifier = Modifier.width(200.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)), border = BorderStroke(1.dp, AppColors.glassBorder)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            items.filter { it != "PROFILE" }.forEach { item ->
                                val isSelected = currentScreen.equals(item, ignoreCase = true)
                                Text(
                                    text = item,
                                    modifier = Modifier.fillMaxWidth().clickable { onSelect(item); expanded = false }.padding(14.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                                if (items.last() != item) HorizontalDivider(color = AppColors.glassBorder.copy(alpha = 0.2f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { onSyncClick(currentScreen, false); expanded = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.glassBg)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text("Sync", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val handlePlaceable = measurables[0].measure(constraints)
        val menuPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(handlePlaceable.width, handlePlaceable.height) {
            handlePlaceable.place(0, 0)
            val spacing = 12.dp.roundToPx()
            when (position) {
                DockPosition.TOP -> menuPlaceable.place(x = (handlePlaceable.width - menuPlaceable.width) / 2, y = handlePlaceable.height + spacing)
                DockPosition.BOTTOM -> menuPlaceable.place(x = (handlePlaceable.width - menuPlaceable.width) / 2, y = -menuPlaceable.height - spacing)
                DockPosition.LEFT -> menuPlaceable.place(x = handlePlaceable.width + spacing, y = (handlePlaceable.height - menuPlaceable.height) / 2)
                DockPosition.RIGHT -> menuPlaceable.place(x = -menuPlaceable.width - spacing, y = (handlePlaceable.height - menuPlaceable.height) / 2)
            }
        }
    }
}

@Composable
fun HomepagePermissionHandler() {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentStep = 1 }
    val alarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { currentStep = 2 }

    LaunchedEffect(currentStep) {
        when (currentStep) {
            0 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    if (status != PackageManager.PERMISSION_GRANTED) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        currentStep = 1
                    }
                } else {
                    currentStep = 1
                }
            }
            1 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply { data = Uri.parse("package:${context.packageName}") }
                        alarmLauncher.launch(intent)
                    } else {
                        currentStep = 2
                    }
                } else {
                    currentStep = 2
                }
            }
        }
    }
}