@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE")

package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.glance.appwidget.updateAll
import com.vtop.models.*
import com.vtop.ui.VtopAnalyticsTab
import com.vtop.ui.core.*
import com.vtop.ui.theme.AppColors
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
    timetable: TimetableModel,
    attendanceData: List<AttendanceModel>,
    examsData: List<ExamScheduleModel>,
    onSyncClick: (String) -> Unit,
    onLogoutClick: Runnable,
    outingHandler: OutingActionHandler
) {
    val context = LocalContext.current
    var navStyle by remember { mutableStateOf(Vault.getNavStyle(context)) }
    var currentScreen by remember { mutableStateOf("HOME") }
    val navItems = listOf("HOME", "ATTENDANCE", "EXAMS", "MARKS", "OUTINGS")
    val coroutineScope = rememberCoroutineScope()

    // Consolidates the Sync Click and Widget Update
    val handleSyncAndUpdateWidget = { screen: String ->
        onSyncClick(screen)
        coroutineScope.launch {
            try {
                NextClassWidget().updateAll(context)
            } catch (_: Exception) {}
        }
        Unit
    }

    BackHandler(enabled = currentScreen != "HOME") {
        currentScreen = if (currentScreen == "ANALYTICS") "PROFILE" else "HOME"
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
            handleSyncAndUpdateWidget(currentScreen)
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
        // The Box fills the screen entirely, allowing lists to scroll UNDER the floating nav
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pullRefresh(pullRefreshState)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (currentScreen != "SIMULATOR") {
                    GlobalTopBar(
                        currentScreen = currentScreen,
                        onProfileClick = { currentScreen = "PROFILE" }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        currentScreen.equals("HOME", true) -> {
                            if (timetable.scheduleMap.isNotEmpty()) Timetable(timetable, attendanceData)
                            else Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No Timetable Data Found", color = MaterialTheme.colorScheme.onBackground) }
                        }
                        currentScreen.equals("ATTENDANCE", true) -> {
                            Attendance(
                                attendanceData = attendanceData,
                                onLaunchSimulator = { currentScreen = "SIMULATOR" }
                            )
                        }
                        currentScreen.equals("SIMULATOR", true) -> {
                            BunkSimulatorTab(
                                timetable = timetable,
                                attendanceData = attendanceData,
                                onBack = { currentScreen = "ATTENDANCE" }
                            )
                        }
                        currentScreen.equals("EXAMS", true) -> {
                            Exams(exams = examsData)
                        }
                        currentScreen.equals("MARKS", true) -> {
                            Marks(
                                marksData = AppBridge.marksState.value,
                                historySummary = AppBridge.historySummaryState.value,
                                historyData = AppBridge.historyItemsState.value,
                                onHistoryLoad = { handleSyncAndUpdateWidget("MARKS") }
                            )
                        }
                        currentScreen.equals("OUTINGS", true) -> {
                            VtopOutingsTab(
                                outingsData = AppBridge.outingsState.value,
                                handler = outingHandler
                            )
                        }
                        currentScreen.equals("PROFILE", true) -> {
                            val sharedPrefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
                            val semInfo = Vault.getSelectedSemester(context)
                            val creds = Vault.getCredentials(context)

                            val actualReminders = ReminderManager.loadReminders(context)
                            val profileMap = AppBridge.profileState.value?.takeIf { it.isNotEmpty() }
                                ?: Vault.getProfile(context)

                            val semesterOptions = Vault.getSemesterOptions(context)
                            val allSemesters = if (semesterOptions.isNotEmpty()) {
                                semesterOptions.map { it.name }
                            } else {
                                listOf(semInfo[1] ?: "Unknown Semester")
                            }

                            Profile(
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
                                    sharedPrefs.edit { putInt("CUSTOM_ACCENT", color.value.toULong().toInt()) }
                                },
                                currentNavStyle = navStyle,
                                onNavStyleChange = { newStyle ->
                                    navStyle = newStyle
                                    Vault.saveNavStyle(context, newStyle)
                                },
                                onLogout = { onLogoutClick.run() },
                                profileData = profileMap,
                                selectedSemester = semInfo[1] ?: semInfo[0] ?: "Unknown Semester",
                                availableSemesters = allSemesters,
                                onSemesterChange = { newSemName ->
                                    val selectedOption = semesterOptions.find { it.name == newSemName }
                                    if (selectedOption != null) {
                                        Vault.saveSelectedSemester(context, selectedOption.id, selectedOption.name)
                                        handleSyncAndUpdateWidget("PROFILE")
                                    }
                                },
                                currentRegNo = creds[0] ?: "",
                                currentPass = creds[1] ?: "",
                                onCredentialsSave = { newReg, newPass ->
                                    Vault.saveCredentials(context, newReg, newPass)
                                    handleSyncAndUpdateWidget("PROFILE")
                                },
                                reminders = actualReminders,
                                onDeleteReminder = { idToDelete ->
                                    val updated = ReminderManager.loadReminders(context).filter { it.id != idToDelete }
                                    ReminderManager.saveReminders(context, updated)
                                    handleSyncAndUpdateWidget("NONE")
                                },
                                onNavigateToAnalytics = { currentScreen = "ANALYTICS" },
                                lastSyncTime = Vault.getLastSyncTime(context),
                                onSyncClick = { handleSyncAndUpdateWidget("PROFILE") }
                            )
                        }

                        currentScreen.equals("ANALYTICS", true) -> {
                            VtopAnalyticsTab(
                                attendanceData = attendanceData,
                                historySummary = AppBridge.historySummaryState.value,
                                historyData = AppBridge.historyItemsState.value
                            )
                        }
                        else -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("$currentScreen Coming Soon", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            AnimatedVisibility(
                visible = errorMsg != null,
                enter = slideInVertically(initialOffsetY = { fullHeight: Int -> -fullHeight }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { fullHeight: Int -> -fullHeight }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(16.dp).fillMaxWidth(0.9f).clickable { AppBridge.appError.value = null }
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = errorMsg ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

            // --- NEW FLOATING NAV BAR ---
            if (navStyle == "STATIC") {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    BottomNavigation(currentScreen) { currentScreen = it }
                }
            }

            // --- FLOATING DOCK ---
            if (navStyle != "STATIC") {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp)
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
                    FloatingDockContainer(currentScreen, navItems, offsetX, offsetY, screenWidthPx, screenHeightPx, handleSyncAndUpdateWidget) { currentScreen = it }
                }
            }
        }

        val otpResolver = AppBridge.currentOtpResolver.value
        if (otpResolver != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(100f)) {
                OtpForm(
                    onVerify = { otp ->
                        otpResolver.submit(otp)
                        AppBridge.currentOtpResolver.value = null
                    },
                    onCancel = {
                        otpResolver.cancel()
                        AppBridge.currentOtpResolver.value = null
                    }
                )
            }
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

    val alpha by animateFloatAsState(
        targetValue = if (syncStatus != "IDLE") 0.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "syncAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = displayTitle, color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text(text = subtitleText, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        IconButton(
            onClick = onProfileClick,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BottomNavigation(currentScreen: String, onSelect: (String) -> Unit) {
    val items = listOf(
        Triple("HOME", "Home", Icons.Default.Home),
        Triple("ATTENDANCE", "Attendance", Icons.Default.CheckCircle),
        Triple("EXAMS", "Exams", Icons.Default.Event),
        Triple("MARKS", "Marks", Icons.Default.Assessment),
        Triple("OUTINGS", "Outings", Icons.AutoMirrored.Filled.DirectionsRun)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val (screenId, label, icon) = item
                val isSelected = currentScreen.equals(screenId, true)

                val tint by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    label = "navTint"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1f,
                    label = "navScale"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(screenId) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingDockContainer(currentScreen: String, items: List<String>, offsetX: Float, offsetY: Float, screenWidthPx: Float, screenHeightPx: Float, onSyncClick: (String) -> Unit, onSelect: (String) -> Unit) {
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    Card(modifier = Modifier.width(200.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(0.98f)), border = BorderStroke(1.dp, AppColors.glassBorder)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            items.forEach { item ->
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
                            Button(onClick = { onSyncClick(currentScreen); expanded = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppColors.glassBg)) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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