@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE")

package com.vtop.ui.screens.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import com.composables.icons.lucide.*
import com.vtop.models.*
import com.vtop.ui.VtopAnalyticsTab
import com.vtop.ui.core.*
import com.vtop.ui.screens.portal.VtopPortalScreen
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
    onSyncClick: (String, Boolean) -> Unit, // Just defines the type
    onLogoutClick: Runnable,
    outingHandler: OutingActionHandler
) {
    // 1. Fire the Sequential Permission Handler instantly
    HomepagePermissionHandler()

    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)

    // INSTANT EVALUATION: Check if the semester is over the second the app opens
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
        mutableStateOf(
            Vault.getNavStyle(context).let {
                if (it.isBlank() || !sharedPrefs.contains("NAV_STYLE_SET")) "STATIC" else it
            }
        )
    }

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

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { navItems.size })
    val coroutineScope = rememberCoroutineScope()

    var activeOverlay by remember {
        mutableStateOf<String?>(if (initialShortcutAction == "com.vtop.SHORTCUT_SIMULATOR") "SIMULATOR" else null)
    }

    val currentTab = navItems.getOrNull(pagerState.currentPage) ?: navItems.last()

    val handleSyncAndUpdateWidget = { screen: String, forceNewSession: Boolean ->
        onSyncClick(screen, forceNewSession)
        coroutineScope.launch {
            try { NextClassWidget().updateAll(context) } catch (_: Exception) {}
        }
        Unit
    }

    BackHandler(enabled = activeOverlay != null || pagerState.currentPage != 0) {
        if (activeOverlay != null) {
            activeOverlay = if (activeOverlay == "ANALYTICS" || activeOverlay == "PORTAL") null else null
        } else {
            coroutineScope.launch { pagerState.scrollToPage(0) }
        }
    }

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isRefreshing by remember { mutableStateOf(false) }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            handleSyncAndUpdateWidget(currentTab, false)
            coroutineScope.launch {
                delay(1500)
                isRefreshing = false
            }
        }
    )

    val errorMsg = AppBridge.appError.value
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            delay(5000)
            AppBridge.appError.value = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                GlobalTopBar(
                    currentScreen = currentTab,
                    onProfileClick = { coroutineScope.launch { pagerState.scrollToPage(navItems.indexOf("PROFILE")) } }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    userScrollEnabled = true,
                    key = { index -> navItems.getOrNull(index) ?: index }
                ) { page ->
                    val pageName = navItems.getOrNull(page) ?: return@HorizontalPager

                    when (pageName) {
                        "HOME" -> {
                            if (AppBridge.isSemesterCompleted.value) {
                                SemesterCompletedView()
                            } else if (timetable.scheduleMap.isNotEmpty() || examsData.isNotEmpty()) {
                                val holidaysMap = remember {
                                    val map = mutableMapOf<String, String>()
                                    try {
                                        val jsonString = context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
                                        val jsonObject = org.json.JSONObject(jsonString)
                                        val semesters = jsonObject.keys()

                                        while (semesters.hasNext()) {
                                            val semObj = jsonObject.getJSONObject(semesters.next())
                                            if (semObj.has("holidays")) {
                                                val hObj = semObj.getJSONObject("holidays")
                                                val hKeys = hObj.keys()
                                                while (hKeys.hasNext()) {
                                                    val dateStr = hKeys.next()
                                                    map[dateStr] = hObj.getString(dateStr)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) { e.printStackTrace() }
                                    map
                                }

                                Timetable(
                                    timetable = timetable,
                                    attendanceData = attendanceData,
                                    examsData = examsData,
                                    holidays = holidaysMap
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), Alignment.Center) {
                                    Text("No Timetable Data Found", color = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }
                        "ATTENDANCE" -> {
                            if (AppBridge.isSemesterCompleted.value) {
                                SemesterCompletedView()
                            } else {
                                Attendance(attendanceData = attendanceData, onLaunchSimulator = { activeOverlay = "SIMULATOR" })
                            }
                        }
                        "EXAMS" -> {
                            if (AppBridge.isSemesterCompleted.value) {
                                SemesterCompletedView()
                            } else {
                                Exams(exams = examsData)
                            }
                        }
                        "MARKS" -> {
                            // Marks remains visible even if semester is completed so users can see FAT results
                            Marks(
                                marksData = AppBridge.marksState.value,
                                historySummary = AppBridge.historySummaryState.value,
                                historyData = AppBridge.historyItemsState.value,
                                onHistoryLoad = { handleSyncAndUpdateWidget("MARKS", false) }
                            )
                        }
                        "OUTINGS" -> VtopOutingsTab(outingsData = AppBridge.outingsState.value, handler = outingHandler)
                        "PROFILE" -> {
                            var semInfo by remember { mutableStateOf(Vault.getSelectedSemester(context)) }
                            var creds by remember { mutableStateOf(Vault.getCredentials(context)) }
                            var actualReminders by remember { mutableStateOf(ReminderManager.loadReminders(context)) }
                            val semesterOptions = remember { Vault.getSemesterOptions(context) }
                            val lastSyncTime = remember { Vault.getLastSyncTime(context) }

                            val profileStateValue = AppBridge.profileState.value
                            val profileMap = remember(profileStateValue) {
                                profileStateValue?.takeIf { it.isNotEmpty() } ?: Vault.getProfile(context)
                            }

                            val allSemesters = remember(semesterOptions, semInfo) {
                                if (semesterOptions.isNotEmpty()) semesterOptions.map { it.name } else listOf(semInfo[1] ?: "Unknown Semester")
                            }

                            Profile(
                                onBack = { coroutineScope.launch { pagerState.scrollToPage(0) } },
                                timetable = timetable,
                                examsData = examsData,
                                onOpenPortal = { activeOverlay = "PORTAL" },
                                currentTheme = ThemeManager.themeMode.value,
                                onThemeChange = { newTheme ->
                                    ThemeManager.themeMode.value = newTheme
                                    sharedPrefs.edit { putString("APP_THEME", newTheme.name) }
                                },
                                useDynamicColor = ThemeManager.useDynamicColor.value,
                                onDynamicColorChange = { dyn ->
                                    ThemeManager.useDynamicColor.value = dyn
                                    sharedPrefs.edit { putBoolean("USE_DYNAMIC_COLOR", dyn) }
                                },
                                customAccent = ThemeManager.customAccent.value,
                                onAccentChange = { color ->
                                    ThemeManager.customAccent.value = color
                                    sharedPrefs.edit { putInt("CUSTOM_ACCENT", color.toArgb()) }
                                },
                                currentNavStyle = navStyle,
                                onNavStyleChange = { newStyle ->
                                    navStyle = newStyle
                                    sharedPrefs.edit { putBoolean("NAV_STYLE_SET", true) }
                                    Vault.saveNavStyle(context, newStyle)
                                },
                                mergeLabs = mergeLabs,
                                onMergeLabsChange = { v -> mergeLabs = v; sharedPrefs.edit { putBoolean("MERGE_LABS", v) } },
                                mergeMarks = mergeMarks,
                                onMergeMarksChange = { v -> mergeMarks = v; sharedPrefs.edit { putBoolean("MERGE_MARKS", v) } },
                                showOutings = showOutings,
                                onShowOutingsChange = { v ->
                                    showOutings = v
                                    sharedPrefs.edit { putBoolean("SHOW_OUTINGS", v) }
                                },
                                onLogout = { onLogoutClick.run() },
                                profileData = profileMap,
                                selectedSemester = semInfo[1] ?: semInfo[0] ?: "Unknown Semester",
                                availableSemesters = allSemesters,
                                onSemesterChange = { newSemName ->
                                    val selectedOption = semesterOptions.find { it.name == newSemName }
                                    if (selectedOption != null) {
                                        Vault.saveSelectedSemester(context, selectedOption.id, selectedOption.name)
                                        semInfo = Vault.getSelectedSemester(context)
                                        handleSyncAndUpdateWidget("PROFILE", false)
                                    }
                                },
                                currentRegNo = creds[0] ?: "",
                                currentPass = creds[1] ?: "",
                                onCredentialsSave = { newReg, newPass ->
                                    Vault.saveCredentials(context, newReg, newPass)
                                    creds = Vault.getCredentials(context)
                                    handleSyncAndUpdateWidget("PROFILE", true) // Force sync on new creds
                                },
                                reminders = actualReminders,
                                onDeleteReminder = { idToDelete ->
                                    val updated = actualReminders.filter { it.id != idToDelete }
                                    ReminderManager.saveReminders(context, updated)
                                    actualReminders = updated
                                    handleSyncAndUpdateWidget("NONE", false)
                                },
                                onNavigateToAnalytics = { activeOverlay = "ANALYTICS" },
                                lastSyncTime = lastSyncTime,
                                onSyncClick = { forceNewSession -> handleSyncAndUpdateWidget("PROFILE", forceNewSession) }
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )

            if (navStyle == "STATIC" && currentTab != "PROFILE") {
                Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
                    BottomNavigation(currentTab, navItems) { tabName ->
                        val targetPage = navItems.indexOf(tabName)
                        if (targetPage != -1) coroutineScope.launch { pagerState.scrollToPage(targetPage) }
                    }
                }
            }

            if (navStyle != "STATIC" && currentTab != "PROFILE") {
                Box(
                    modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }.align(Alignment.BottomCenter).padding(bottom = 60.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                val hLimit = (screenWidthPx / 2f) - with(density) { 30.dp.toPx() }
                                val vTopLimit = -(screenHeightPx - with(density) { 140.dp.toPx() })
                                val vBottomLimit = with(density) { 20.dp.toPx() }
                                offsetX = offsetX.coerceIn(-hLimit, hLimit)
                                offsetY = offsetY.coerceIn(vTopLimit, vBottomLimit)
                            }
                        }
                ) {
                    FloatingDockContainer(currentTab, navItems, offsetX, offsetY, screenWidthPx, screenHeightPx, handleSyncAndUpdateWidget) { tabName ->
                        val targetPage = navItems.indexOf(tabName)
                        if (targetPage != -1) coroutineScope.launch { pagerState.scrollToPage(targetPage) }
                    }
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
                    "SIMULATOR" -> BunkSimulatorTab(timetable = timetable, attendanceData = attendanceData, onBack = { activeOverlay = null })
                    "ANALYTICS" -> VtopAnalyticsTab(attendanceData = attendanceData, historySummary = AppBridge.historySummaryState.value, historyData = AppBridge.historyItemsState.value)
                    "PORTAL" -> {
                        val creds = Vault.getCredentials(context)
                        val client = remember { com.vtop.network.VtopClient(context, creds[0] ?: "", creds[1] ?: "") }
                        VtopPortalScreen(vtopClient = client, onClose = { activeOverlay = null })
                    }
                }
            }
        }

        val otpResolver = AppBridge.currentOtpResolver.value
        if (otpResolver != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(100f)) {
                OtpForm(
                    onVerify = { otp -> otpResolver.submit(otp); AppBridge.currentOtpResolver.value = null },
                    onCancel = { otpResolver.cancel(); AppBridge.currentOtpResolver.value = null }
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
fun GlobalTopBar(currentScreen: String, onProfileClick: () -> Unit) {
    val context = LocalContext.current
    var timeAgoText by remember { mutableStateOf("Calculating...") }
    val syncStatus by AppBridge.syncStatus

    LaunchedEffect(Unit) {
        while (true) {
            val lastSyncMillis = Vault.getLastSyncTimestamp(context)
            if (lastSyncMillis == 0L) {
                timeAgoText = "Never synced"
            } else {
                val diffSeconds = (System.currentTimeMillis() - lastSyncMillis) / 1000
                timeAgoText = when {
                    diffSeconds < 60 -> "Just now"
                    diffSeconds < 3600 -> "${diffSeconds / 60} mins ago"
                    diffSeconds < 86400 -> "${diffSeconds / 3600} hours ago"
                    else -> "${diffSeconds / 86400} days ago"
                }
            }
            delay(1000)
        }
    }

    val displayTitle = when (currentScreen.uppercase(Locale.ROOT)) {
        "HOME" -> "Timetable"
        "ATTENDANCE" -> "Attendance"
        "EXAMS" -> "Exam Schedule"
        "MARKS" -> "Marks & Grades"
        "OUTINGS" -> "Outings"
        "PROFILE" -> "Settings"
        else -> currentScreen
    }

    val subtitleText = when (syncStatus) {
        "LOGGING_IN" -> "Logging in..."
        "SYNCING" -> "Syncing data..."
        else -> "Last synced: $timeAgoText"
    }

    val alpha by animateFloatAsState(targetValue = if (syncStatus != "IDLE") 0.5f else 1f, animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse), label = "syncAlpha")

    Row(
        modifier = Modifier.fillMaxWidth().background(Color.Transparent).statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = displayTitle, color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(text = subtitleText, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        if (currentScreen != "PROFILE") {
            IconButton(onClick = onProfileClick, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
                Icon(Lucide.User, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            visibleTabs.forEach { item ->
                val (screenId, label, icon) = item
                val isSelected = currentTab.equals(screenId, true)
                val tint by animateColorAsState(targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), label = "navTint")
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
    val position = when {
        offsetX < -(screenWidthPx * 0.35f) -> DockPosition.LEFT
        offsetX > (screenWidthPx * 0.35f) -> DockPosition.RIGHT
        offsetY < -(screenHeightPx * 0.7f) -> DockPosition.TOP
        else -> DockPosition.BOTTOM
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

    // State to track which permission we are currently asking for
    var currentStep by remember { mutableIntStateOf(0) }

    // 1. Launcher for Android 13+ Notifications
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Move to the next permission regardless of whether they approved or denied
        currentStep = 1
    }

    // 2. Launcher for Battery Optimization (Takes user to settings)
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        currentStep = 2
    }

    // 3. Launcher for Exact Alarms (Takes user to settings)
    val alarmLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        currentStep = 3
    }

    // Sequentially evaluate and trigger permissions
    LaunchedEffect(currentStep) {
        when (currentStep) {
            0 -> { // Step 0: Notifications (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    if (status != PackageManager.PERMISSION_GRANTED) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        currentStep = 2 // Already granted, skip to next
                    }
                } else {
                    currentStep = 2// Not required for Android 12 and below
                }
            }
            /*
            1 -> { // Step 1: Battery Optimization
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    batteryLauncher.launch(intent)
                } else {
                    currentStep = 2 // Already ignored or unsupported, skip to next
                }
            }
            */
            2 -> { // Step 2: Exact Alarms (Android 12+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    if (!alarmManager.canScheduleExactAlarms()) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        alarmLauncher.launch(intent)
                    } else {
                        currentStep = 3
                    }
                } else {
                    currentStep = 3
                }
            }
        }
    }
}