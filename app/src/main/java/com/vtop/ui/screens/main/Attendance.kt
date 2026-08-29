package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.models.AttendanceModel
import com.vtop.utils.AnalyticsManager
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState


@Composable
fun premiumSurfaceColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF141414) else Color(0xFFFFFFFF)

@Composable
fun premiumBorderColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)

// --- TRUE COUNT ENGINE: Ignores VTOP's raw lab hours and counts physical history rows ---
fun getRealAttendanceCounts(item: AttendanceModel): Pair<Int, Int> {
    val history = item.history
    if (!history.isNullOrEmpty()) {
        val total = history.size
        val attended = history.count { h ->
            val status = h.status?.uppercase(Locale.getDefault()) ?: ""
            status.contains("PRESENT") || status.contains("DUTY") || status.contains("ON DUTY")
        }
        return Pair(attended, total)
    }
    // Fallback ONLY if the history array failed to load entirely
    val rawAttended = item.attendedClasses?.toString()?.toIntOrNull() ?: 0
    val rawTotal = item.totalClasses?.toString()?.toIntOrNull() ?: 0
    return Pair(rawAttended, rawTotal)
}

// --- RESTORED BUNK LOGIC ---
sealed class BunkState {
    data class Safe(val canMiss: Int) : BunkState()
    data class AtRisk(val mustAttend: Int) : BunkState()
    object NoData : BunkState()
}

fun calculateBunkBudget(attended: Int, total: Int, target: Float = 0.75f): BunkState {
    if (total == 0) return BunkState.NoData
    val currentPct = attended.toFloat() / total

    return if (currentPct >= target) {
        var canMiss = 0
        while ((attended.toFloat() / (total + canMiss + 1)) >= target) {
            canMiss++
        }
        BunkState.Safe(canMiss)
    } else {
        var mustAttend = 0
        while (((attended + mustAttend).toFloat() / (total + mustAttend)) < target) {
            mustAttend++
        }
        BunkState.AtRisk(mustAttend)
    }
}


// Add the above imports to your existing import block

@Composable
fun AttendanceCard(item: AttendanceModel, onClick: () -> Unit) {

    val attended = item.attendedClasses?.toString()?.toIntOrNull() ?: 0
    val total = item.totalClasses?.toString()?.toIntOrNull() ?: 0
    val percentage = item.attendancePercentage?.toFloatOrNull()?.toInt() ?: 0
    val progress = (percentage / 100f).coerceIn(0f, 1f)

    val statusColor = when {
        percentage < 75 -> MaterialTheme.colorScheme.error
        percentage < 80 -> Color(0xFFF59E0B) // Warning Amber
        else -> Color(0xFF4CAF50) // Success Green
    }

    val bunkState = remember(attended, total) { calculateBunkBudget(attended, total) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = premiumSurfaceColor()),
        border = BorderStroke(1.dp, premiumBorderColor()),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Title + Subtitle (Chip)
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "${item.courseCode ?: ""} - ${item.courseName ?: "Unknown Course"}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    BunkPredictorChip(bunkState)
                }

                // Right side: Percentage + Fraction
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$percentage%",
                        color = statusColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$attended/$total",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Anchor: Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(premiumBorderColor())
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        statusColor.copy(alpha = 0.4f),
                                        statusColor
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@SuppressLint("NewApi")
@Composable
fun Attendance(attendanceData: List<AttendanceModel>, onLaunchSimulator: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Attendance_Screen")
    }

    var selectedCourse by remember { mutableStateOf<AttendanceModel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (attendanceData.isEmpty()) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Text("No Attendance Data Found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Evaluate Risk using our True Count engine
    val atRiskCount = attendanceData.count { item ->
        val (_, total) = getRealAttendanceCounts(item)
        if (total == 0) false else {
            val (attended, _) = getRealAttendanceCounts(item)
            ((attended.toFloat() / total) * 100) < 75
        }
    }

    // Group courses by type
    val groupedCourses = remember(attendanceData) {
        val theory = mutableListOf<AttendanceModel>()
        val lab = mutableListOf<AttendanceModel>()
        val project = mutableListOf<AttendanceModel>()
        val other = mutableListOf<AttendanceModel>()

        attendanceData.forEach { course ->
            val type = course.courseType?.uppercase(Locale.getDefault()) ?: ""
            when {
                type.contains("TH") || type.contains("ETH") -> theory.add(course)
                type.contains("LO") || type.contains("ELA") || type.contains("LAB") -> lab.add(course)
                type.contains("PJT") || type.contains("EPJ") -> project.add(course)
                else -> other.add(course)
            }
        }

        // Only keep categories that actually have courses
        val map = mutableMapOf<String, List<AttendanceModel>>()
        if (theory.isNotEmpty()) map["Theory"] = theory.sortedBy { it.courseCode }
        if (lab.isNotEmpty()) map["Lab"] = lab.sortedBy { it.courseCode }
        if (project.isNotEmpty()) map["Project"] = project.sortedBy { it.courseCode }
        if (other.isNotEmpty()) map["Other"] = other.sortedBy { it.courseCode }
        map
    }

    val categories = groupedCourses.keys.toList()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(pageCount = { categories.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 96.dp, bottom = 120.dp) // Maintain outer container padding
    ) {
        // 1. Header Cards (Bunk Simulator & Risk)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onLaunchSimulator() },
                colors = CardDefaults.cardColors(containerColor = premiumSurfaceColor()),
                border = BorderStroke(1.dp, premiumBorderColor()),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = "Simulator",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Bunk Simulator",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = if (atRiskCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else premiumSurfaceColor()),
                border = BorderStroke(1.dp, if (atRiskCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else premiumBorderColor()),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = atRiskCount.toString(),
                        color = if (atRiskCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Courses at Risk",
                        color = if (atRiskCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // 2. TabRow (Standard Underline)
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            containerColor = Color.Transparent, // Inherit background
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            }
        ) {
            categories.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                Tab(
                    selected = isSelected,
                    onClick = {
                        selectedTabIndex = index
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 3. Horizontal Pager for Category Swiping
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val currentCategory = categories.getOrNull(page) ?: categories.first()
            val coursesToDisplay = groupedCourses[currentCategory] ?: emptyList()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                items(coursesToDisplay) { course ->
                    Box(modifier = Modifier.padding(bottom = 16.dp)) {
                        AttendanceCard(item = course, onClick = { selectedCourse = course })
                    }
                }
            }
        }
    }

    if (selectedCourse != null) {
        val bottomSheetBg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF111111) else Color(0xFFFFFFFF)
        ModalBottomSheet(
            onDismissRequest = { selectedCourse = null },
            containerColor = bottomSheetBg,
            sheetState = sheetState
        ) {
            AttendanceBottomSheetContent(
                course = selectedCourse!!,
                onSimulateClick = { selectedCourse = null; onLaunchSimulator() },
                onBack = { selectedCourse = null }
            )
        }
    }
}
@Composable
private fun BunkPredictorChip(bunkState: BunkState) {
    val (statusText, isDangerous) = when (bunkState) {
        is BunkState.Safe -> {
            if (bunkState.canMiss == 0) {
                "Cannot skip anymore" to true
            } else {
                "Can bunk ${bunkState.canMiss} more" to false
            }
        }
        is BunkState.AtRisk -> "Must attend ${bunkState.mustAttend} more" to true
        BunkState.NoData -> "" to false
    }

    if (statusText.isEmpty()) return
    val chipStatusColor = if (isDangerous) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)

    Row(
        modifier = Modifier.background(chipStatusColor.copy(alpha = 0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (isDangerous) Icons.Default.Warning else Icons.Default.CheckCircle, contentDescription = null, tint = chipStatusColor, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text = statusText, color = chipStatusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AttendanceBottomSheetContent(course: AttendanceModel, onSimulateClick: () -> Unit, onBack: () -> Unit) {
    val cType = course.courseType ?: ""
    val categoryLabel = when {
        cType.contains("TH", ignoreCase = true) || cType.contains("ETH", ignoreCase = true) -> "Theory"
        cType.contains("LO", ignoreCase = true) || cType.contains("ELA", ignoreCase = true) -> "Lab"
        cType.contains("PJT", ignoreCase = true) || cType.contains("EPT", ignoreCase = true) -> "Project"
        else -> cType.ifEmpty { "Theory" }
    }

    val scrollState = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Column(modifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.85f).verticalScroll(scrollState).padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
            Text(text = "${course.courseCode} · $categoryLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Text(text = course.courseName ?: "N/A", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(Modifier.height(16.dp))

        AttendanceDetailCore(course = course, onSimulateClick = onSimulateClick)
    }
}

// --- SHARED CORE EXPORTED FOR TIMETABLE ---
@Composable
fun AttendanceDetailCore(course: AttendanceModel, onSimulateClick: (() -> Unit)? = null) {
    val attended = course.attendedClasses?.toIntOrNull() ?: 0
    val total = course.totalClasses?.toIntOrNull() ?: 0
    val percentage = course.attendancePercentage?.toFloatOrNull()?.toInt() ?: 0
    val isSafe = percentage >= 75

    val statusColor = if (isSafe) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    val bunkState = calculateBunkBudget(attended, total)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { if (total > 0) attended.toFloat() / total else 0f },
                modifier = Modifier.fillMaxSize(),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round
            )
            Text(text = "$percentage%", color = statusColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(attended.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text("Attended", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
                .background(premiumSurfaceColor(), RoundedCornerShape(12.dp))
                .border(1.dp, premiumBorderColor(), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            when (bunkState) {
                is BunkState.Safe -> Column {
                    Text(text = "You can safely skip the next ${bunkState.canMiss} classes and stay above 75%.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
                is BunkState.AtRisk -> Column {
                    Text("Recovery Path", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "If you attend the next ${bunkState.mustAttend} consecutive classes, you will reach 75%.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
                is BunkState.NoData -> Text("Not enough data to calculate predictions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }

        if (onSimulateClick != null) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            var showHistory by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showHistory = !showHistory }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Full History", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Icon(if (showHistory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Toggle History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                AnimatedVisibility(showHistory) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        DetailedAttendanceHistoryView(course)
                    }
                }
            }
        }
    }
}

// ── Filterable History View ──────────────────────────────────────────────
@Composable
fun DetailedAttendanceHistoryView(item: AttendanceModel) {
    val history = item.history ?: return

    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredHistory = remember(history, selectedFilter) {
        history.filter { h ->
            val statusUpper = h.status?.uppercase(Locale.getDefault()) ?: ""
            when (selectedFilter) {
                "PRESENT" -> statusUpper.contains("PRESENT")
                "ABSENT" -> !statusUpper.contains("PRESENT") && !statusUpper.contains("DUTY")
                "DUTY" -> statusUpper.contains("DUTY") || statusUpper.contains("ON DUTY")
                else -> true
            }
        }
    }

    Column {
        // ── Filter Chips ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttendanceFilterChip("All", selectedFilter == "ALL") { selectedFilter = "ALL" }
            AttendanceFilterChip("Present", selectedFilter == "PRESENT") { selectedFilter = "PRESENT" }
            AttendanceFilterChip("Absent", selectedFilter == "ABSENT") { selectedFilter = "ABSENT" }
            AttendanceFilterChip("On Duty", selectedFilter == "DUTY") { selectedFilter = "DUTY" }
        }

        // ── Table Header ──
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(text = "DATE", modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "TIME/SLOT", modifier = Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "STATUS", modifier = Modifier.weight(0.25f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
        }

        // ── Table Body ──
        if (filteredHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Text("No records found.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        } else {
            filteredHistory.forEach { h ->
                val statusUpper = h.status?.uppercase(Locale.getDefault()) ?: ""
                val (bgColor, textColor) = when {
                    statusUpper.contains("PRESENT") -> Color(0xFF4CAF50).copy(alpha = 0.2f) to Color(0xFF4CAF50)
                    statusUpper.contains("DUTY") -> Color(0xFF2196F3).copy(alpha = 0.2f) to Color(0xFF2196F3)
                    else -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f) to MaterialTheme.colorScheme.error
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = h.date ?: "--", modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(text = "${h.time ?: "--"} / ${h.slot ?: "--"}", modifier = Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    Box(modifier = Modifier.weight(0.25f), contentAlignment = Alignment.CenterEnd) {
                        Box(modifier = Modifier.background(bgColor, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(text = h.status ?: "--", color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else premiumBorderColor()

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}