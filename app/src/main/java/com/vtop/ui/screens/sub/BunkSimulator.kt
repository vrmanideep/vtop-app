package com.vtop.ui.screens.sub

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.logic.BunkProjectorResult
import com.vtop.models.*
import com.vtop.utils.AnalyticsManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

data class CalendarContext(
    val semesterName: String = "Unknown Semester",
    val startDate: LocalDate = LocalDate.MIN,
    val endDate: LocalDate = LocalDate.MAX,
    val trueEndDate: LocalDate = LocalDate.MAX,
    val weekOffs: List<String> = emptyList(),
    val holidays: Map<LocalDate, String> = emptyMap()
)

fun isInstructionalDay(date: LocalDate, ctx: CalendarContext): Boolean {
    if (date.isBefore(ctx.startDate) || date.isAfter(ctx.endDate)) return false
    if (ctx.weekOffs.any { it.equals(date.dayOfWeek.name, ignoreCase = true) }) return false
    if (ctx.holidays.containsKey(date)) return false
    return true
}

private fun isSameTypeGroup(a: String?, b: String?): Boolean {
    if (a == b) return true
    val aLab = a?.contains("L") == true || a?.contains("P") == true
    val bLab = b?.contains("L") == true || b?.contains("P") == true
    return aLab == bLab
}

// =====================================================================
// HYBRID PREDICTIVE ENGINE (Time-Series Velocity + Slot Weight Math)
// =====================================================================
@SuppressLint("SimpleDateFormat")
private fun analyzeAttendanceTrend(
    attendance: AttendanceModel?,
    timetable: TimetableModel,
    calCtx: CalendarContext
): String? {
    if (attendance == null || calCtx.trueEndDate == LocalDate.MAX) return null

    val historyList = try { attendance.history } catch (e: Exception) { null }
    if (historyList.isNullOrEmpty()) return null

    // 1. Convert History to Timestamps
    val sortedHistory = historyList.mapNotNull { item ->
        try {
            val dateStr = item.date?.trim() ?: return@mapNotNull null
            val statusStr = item.status?.trim() ?: ""
            val sdf = if (dateStr.contains(Regex("[a-zA-Z]"))) {
                SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
            } else {
                SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            }
            val d = sdf.parse(dateStr)
            if (d != null) Pair(d.time, statusStr) else null
        } catch (e: Exception) { null }
    }.sortedBy { it.first }

    if (sortedHistory.isEmpty()) return null

    // 2. Define the "Momentum Window" (Last 28 Days)
    val currentMillis = System.currentTimeMillis()
    val windowStartMillis = currentMillis - (28L * 24 * 60 * 60 * 1000)

    var recentTotal = 0
    var recentAttended = 0

    sortedHistory.forEach { (time, status) ->
        if (time >= windowStartMillis) {
            recentTotal++
            if (status.equals("Present", ignoreCase = true) ||
                status.equals("On Duty", ignoreCase = true) ||
                status.equals("Attended", ignoreCase = true)) {
                recentAttended++
            }
        }
    }

    // Need a minimum of 4 classes in the last month to gauge a reliable velocity
    if (recentTotal < 4) return "AI Need Data: Attend a few more classes to establish a reliable bunking trend."

    // 3. Calculate Absence Velocity (V_abs)
    val vAbs = 1.0 - (recentAttended.toDouble() / recentTotal)
    val currentPct = attendance.attendancePercentage?.replace("%", "")?.toDoubleOrNull() ?: 100.0

    if (vAbs <= 0.05) {
        return if (currentPct >= 75.0) "AI Insight: Safe. Your recent attendance is flawless."
        else "AI Insight: You are below 75%, but your flawless recent attendance will pull you up."
    }

    // 4. The Deterministic Projection Loop
    var projectedAttended = attendance.attendedClasses?.toDoubleOrNull() ?: 0.0
    var projectedTotal = attendance.totalClasses?.toDoubleOrNull() ?: 0.0

    var simDate = LocalDate.now().plusDays(1)
    var crashDate: LocalDate? = null

    // Helper: Calculate slot weight (e.g., L55+L56 = 2)
    fun getSlotWeight(slotStr: String?): Int {
        if (slotStr.isNullOrBlank() || slotStr == "-" || slotStr.equals("N/A", ignoreCase = true)) return 0
        return slotStr.split("+").size
    }

    while (!simDate.isAfter(calCtx.trueEndDate)) {
        if (isInstructionalDay(simDate, calCtx)) {
            val dayName = simDate.dayOfWeek.name

            // Find all classes for this subject on this specific day
            val dayCourses = timetable.scheduleMap[dayName]?.filter {
                it.courseCode == attendance.courseCode && isSameTypeGroup(it.courseType, attendance.courseType)
            } ?: emptyList()

            var dailyWeight = 0
            dayCourses.forEach { dailyWeight += getSlotWeight(it.slot) }

            if (dailyWeight > 0) {
                projectedTotal += dailyWeight
                // Apply the absence velocity to predict attendance
                val expectedAttendance = dailyWeight * (1.0 - vAbs)
                projectedAttended += expectedAttendance

                val simulatedPct = (projectedAttended / projectedTotal) * 100.0
                if (simulatedPct < 75.0) {
                    crashDate = simDate
                    break
                }
            }
        }
        simDate = simDate.plusDays(1)
    }

    if (currentPct < 75.0) {
        return "AI Warning: You are currently in the danger zone."
    }

    if (crashDate != null) {
        val formatter = DateTimeFormatter.ofPattern("MMM dd")
        return "AI Warning: At your current rate (missing ${(vAbs * 100).toInt()}% of classes), you will drop below 75% on ${crashDate.format(formatter)}."
    }

    return "AI Insight: You are bunking occasionally, but mathematics project you will safely finish above 75%."
}

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunkSimulatorTab(
    timetable: TimetableModel,
    attendanceData: List<AttendanceModel>,
    selectedSemester: String,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Bunk_Simulator_Screen") }

    val context = LocalContext.current
    val calCtx = remember(selectedSemester) { getCalendarContext(context, selectedSemester) }

    val legacyHolidayMap = remember(calCtx) {
        val map = mutableMapOf<String, String>()
        calCtx.holidays.forEach { (k, v) -> map[k.toString()] = v }

        if (calCtx.startDate != LocalDate.MIN && calCtx.endDate != LocalDate.MAX) {
            var curr = calCtx.startDate
            while (!curr.isAfter(calCtx.endDate)) {
                if (calCtx.weekOffs.any { it.equals(curr.dayOfWeek.name, ignoreCase = true) }) {
                    map[curr.toString()] = "Week Off"
                }
                curr = curr.plusDays(1)
            }

            var postSem = calCtx.endDate.plusDays(1)
            val limit = postSem.plusDays(100)
            while (postSem.isBefore(limit)) {
                if (!map.containsKey(postSem.toString())) {
                    map[postSem.toString()] = "Semester Ended"
                }
                postSem = postSem.plusDays(1)
            }

            val today = LocalDate.now()
            if (today.isBefore(calCtx.startDate)) {
                var preSem = today
                while (preSem.isBefore(calCtx.startDate)) {
                    if (!map.containsKey(preSem.toString())) {
                        map[preSem.toString()] = "Not Started"
                    }
                    preSem = preSem.plusDays(1)
                }
            }
        }
        map
    }

    var selectedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var simulationResults by remember { mutableStateOf<List<BunkProjectorResult>>(emptyList()) }
    var hasRun by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState()

    fun getClassesForDate(date: LocalDate): Int {
        val dayName = date.dayOfWeek.name
        val courses = timetable.scheduleMap?.entries?.firstOrNull { it.key.equals(dayName, ignoreCase = true) }?.value ?: emptyList()
        var dailyWeight = 0
        courses.forEach {
            val slotStr = it.slot
            if (!slotStr.isNullOrBlank() && slotStr != "-" && !slotStr.equals("N/A", ignoreCase = true)) {
                dailyWeight += slotStr.split("+").size
            }
        }
        return dailyWeight
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Bunk Simulator", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        }

        val today = LocalDate.now()

        if (calCtx.startDate != LocalDate.MIN) {
            if (today.isBefore(calCtx.startDate)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("${calCtx.semesterName} has not started yet. Classes begin on ${calCtx.startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}.", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (today.isAfter(calCtx.endDate) && !today.isAfter(calCtx.trueEndDate)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("${calCtx.semesterName} instructional classes have ended. Exams are ongoing.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else if (today.isAfter(calCtx.trueEndDate)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("${calCtx.semesterName} is completely finished.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val quickDates = listOf(
            "Today" to today,
            "Tomorrow" to today.plusDays(1),
            today.plusDays(2).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } to today.plusDays(2)
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickDates.forEach { (label, date) ->
                val isSelected = selectedDates.contains(date)

                val tagModifier = when {
                    date.isBefore(calCtx.startDate) -> "Not Started"
                    date.isAfter(calCtx.endDate) -> "Ended"
                    calCtx.holidays.containsKey(date) -> calCtx.holidays[date]?.let { if (it.contains("Exam")) "Exam" else "Holiday" } ?: "Holiday"
                    calCtx.weekOffs.any { it.equals(date.dayOfWeek.name, ignoreCase = true) } -> "Week Off"
                    else -> null
                }

                val classCount = if (tagModifier == null) getClassesForDate(date) else 0
                val chipLabel = when {
                    tagModifier != null -> "$label · $tagModifier"
                    classCount > 0 -> "$label · $classCount classes"
                    else -> "$label · No classes"
                }

                val isYellowTint = tagModifier != null

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedDates = if (isSelected) selectedDates - date else selectedDates + date
                        hasRun = false
                    },
                    label = { Text(chipLabel, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (isYellowTint) Color(0xFFB8860B).copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                        selectedLabelColor = if (isYellowTint) Color.White else MaterialTheme.colorScheme.onPrimary,
                        containerColor = if (isYellowTint) Color(0xFFB8860B).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(50)
                )
            }

            FilterChip(
                selected = false,
                onClick = { showDatePicker = true },
                label = { Text("Pick Date", fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar", modifier = Modifier.size(16.dp)) },
                shape = RoundedCornerShape(50)
            )
        }

        if (selectedDates.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedDates.sorted().forEach { date ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .clickable {
                                selectedDates = selectedDates - date
                                hasRun = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        val validDates = selectedDates.filter { isInstructionalDay(it, calCtx) }
        val totalClassesToSkip = validDates.sumOf { getClassesForDate(it) }

        Button(
            onClick = {
                if (calCtx.startDate == LocalDate.MIN) {
                    errorMessage = "Calendar dates for $selectedSemester are currently unavailable. Ensure your calendar data is synced via settings."
                    return@Button
                }

                if (today.isBefore(calCtx.startDate)) {
                    errorMessage = "${calCtx.semesterName} has not commenced yet. You cannot simulate attendance until classes begin."
                    return@Button
                }

                if (attendanceData.isEmpty()) {
                    errorMessage = "There is no attendance data available for the currently selected semester ($selectedSemester)."
                    return@Button
                }

                if (timetable.scheduleMap.isNullOrEmpty()) {
                    errorMessage = "There is no timetable available for the currently selected semester."
                    return@Button
                }

                if (selectedDates.isNotEmpty()) {
                    if (validDates.isEmpty()) {
                        simulationResults = emptyList()
                        hasRun = true
                    } else {
                        try {
                            simulationResults = com.vtop.logic.BunkSimulator.simulateMultiDayBunk(validDates.sorted(), timetable, attendanceData, legacyHolidayMap)
                            hasRun = true
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Unknown backend error occurred"
                        }
                    }
                } else {
                    errorMessage = "Please select at least one date."
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (selectedDates.isEmpty()) "Calculate attendance" else "Simulate skipping $totalClassesToSkip classes",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (hasRun && simulationResults.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color(0xFF10B981))
                            Spacer(Modifier.height(16.dp))
                            Text("No instructional days selected", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Holidays, exams, and out-of-semester days do not affect attendance calculations.", color = Color(0xFF10B981).copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(simulationResults) { result ->
                    val relatedAttendance = attendanceData.find { it.courseCode == result.courseCode && it.courseType == result.courseType }
                    val lastUpdatedDate = relatedAttendance?.history?.firstOrNull()?.date ?: "Unknown Date"
                    BunkResultCard(result, lastUpdatedDate, relatedAttendance, timetable, calCtx)
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        selectedDates = selectedDates + selected
                        hasRun = false
                    }
                    showDatePicker = false
                }) { Text("Add Date", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
        ) { DatePicker(state = datePickerState) }
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = {
                Text("Notice", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text(errorMessage!!, fontSize = 14.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp
        )
    }
}

@Composable
fun BunkResultCard(
    res: BunkProjectorResult,
    lastUpdatedDate: String,
    relatedAttendance: AttendanceModel?,
    timetable: TimetableModel,
    calCtx: CalendarContext
) {
    var expanded by remember { mutableStateOf(false) }

    // Run the Hybrid AI analysis prediction
    val aiInsight = remember(relatedAttendance, timetable, calCtx) {
        analyzeAttendanceTrend(relatedAttendance, timetable, calCtx)
    }

    val statusColor = when {
        res.noData -> Color(0xFFF59E0B)
        res.isDanger -> MaterialTheme.colorScheme.error
        else -> Color(0xFF10B981)
    }

    val cardBgTint = statusColor.copy(alpha = 0.18f)
    val cardBorder = statusColor.copy(alpha = 0.4f)

    val currentInt = res.currentPct.roundToInt()
    val projectedInt = floor(res.projectedPct).toInt()

    val animatedProjected by animateFloatAsState(
        targetValue = projectedInt.toFloat(),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "projectedAnimation"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgTint),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                res.noData -> "⚠ Missing Data"
                                res.isDanger -> "⚠ Danger"
                                else -> "✓ Safe"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(res.courseCode, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(res.courseType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 26.dp)) {
                    Text("$currentInt%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(" ➔ ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text("${if (res.noData) currentInt else animatedProjected.roundToInt()}%", color = statusColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (res.noData) {
                Text(
                    text = "Cannot simulate future attendance. VTOP has not recorded any class history for this subject yet.",
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val currentFraction = (res.currentPct / 100f).coerceIn(0f, 1f).toFloat()
                val projectedFraction = (res.projectedPct / 100f).coerceIn(0f, 1f).toFloat()

                val animatedCurrentWidth by animateFloatAsState(
                    targetValue = currentFraction,
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label = "currentWidth"
                )

                val animatedProjectedWidth by animateFloatAsState(
                    targetValue = projectedFraction,
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label = "projectedWidth"
                )

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f, targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(animation = tween(800, easing = LinearOutSlowInEasing), repeatMode = RepeatMode.Reverse),
                    label = "alpha"
                )

                Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    // Track & Fill Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        if (currentFraction > projectedFraction) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedCurrentWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProjectedWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(statusColor)
                        )
                    }

                    // Premium Floating 75% Checkpoint Bubble
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(8.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset(y = (-18).dp, x = 5.dp)
                        ) {
                            Text(
                                text = "75%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = MaterialTheme.colorScheme.primary)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), CircleShape)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${animatedProjected.roundToInt()}%", fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.Bold)
                    Text("$currentInt%", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // AI PREDICTION BLOCK
                if (aiInsight != null) {
                    val isWarning = aiInsight.contains("Warning") || aiInsight.contains("danger")
                    val iconTint = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val bgTint = if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    val bBorderTint = if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgTint, RoundedCornerShape(8.dp))
                            .border(1.dp, bBorderTint, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = iconTint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(aiInsight, color = iconTint, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("CALCULATION", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Initial", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${res.currentAttended} / ${res.currentTotal}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        if (res.gapClassesAdded > 0) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("Gap", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text("+${res.gapClassesAdded} attended", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Skipped", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${res.missedClassesAdded} missed", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }

                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                            Text("Final", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${res.projectedAttended} / ${res.projectedTotal} = ${projectedInt}%", color = statusColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("Last updated on VTOP", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(lastUpdatedDate, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        if (res.missedBreakdown.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("AFFECTED", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            res.missedBreakdown.forEach { log ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Box(modifier = Modifier.size(5.dp).background(MaterialTheme.colorScheme.error.copy(alpha=0.6f), CircleShape))
                                    Spacer(Modifier.width(8.dp))
                                    Text(log, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
fun getCalendarContext(context: Context, selectedSemester: String): CalendarContext {
    // 1. Fetch Live Academic Calendar from Vault
    val semId = com.vtop.utils.Vault.getSelectedSemester(context)[0]
    val liveEvents = com.vtop.utils.Vault.getAcademicCalendar(context, semId)

    // Fallback if calendar hasn't been synced yet
    if (liveEvents.isEmpty()) {
        return CalendarContext(semesterName = selectedSemester)
    }

    val sdfParse = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.ENGLISH)
    var startDate = java.time.LocalDate.MAX
    var endDate = java.time.LocalDate.MIN
    val holidays = mutableMapOf<java.time.LocalDate, String>()
    val weekOffs = listOf("SUNDAY") // VTOP default

    // 2. Parse live descriptions to build the simulation rules
    for (event in liveEvents) {
        try {
            val dateObj = sdfParse.parse(event.date) ?: continue
            val localDate = java.time.Instant.ofEpochMilli(dateObj.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val title = event.particulars.lowercase(java.util.Locale.ENGLISH)

            // Identify Semester Boundaries
            if (title.contains("commencement")) {
                if (localDate.isBefore(startDate)) startDate = localDate
            }
            if (title.contains("last instructional day") || title.contains("last working day") || title.contains("last day")) {
                if (localDate.isAfter(endDate)) endDate = localDate
            }

            // Identify Bunk Blockers (Holidays, Exams, & No-Class Days)
            if (title.contains("holiday") || title.contains("exam") || title.contains("cat") || title.contains("fat") ||
                title.contains("no instructional") || title.contains("non instructional")) {
                holidays[localDate] = event.particulars
            }
        } catch (ignored: Exception) {}
    }

    // 3. Smart Fallbacks (if VTOP didn't use specific keywords)
    if (startDate == java.time.LocalDate.MAX) {
        startDate = try {
            val d = sdfParse.parse(liveEvents.first().date)
            java.time.Instant.ofEpochMilli(d!!.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } catch (e: Exception) { java.time.LocalDate.MIN }
    }

    if (endDate == java.time.LocalDate.MIN) {
        endDate = try {
            val d = sdfParse.parse(liveEvents.last().date)
            java.time.Instant.ofEpochMilli(d!!.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } catch (e: Exception) { java.time.LocalDate.MAX }
    }

    return CalendarContext(
        semesterName = selectedSemester,
        startDate = startDate,
        endDate = endDate,
        trueEndDate = endDate, // VTOP dynamic end date
        weekOffs = weekOffs,
        holidays = holidays
    )
}