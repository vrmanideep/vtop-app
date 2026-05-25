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
import com.vtop.models.AttendanceModel
import com.vtop.models.TimetableModel
import com.vtop.utils.AnalyticsManager
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
        return timetable.scheduleMap?.entries?.firstOrNull { it.key.equals(dayName, ignoreCase = true) }?.value?.size ?: 0
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
                    BunkResultCard(result, lastUpdatedDate)
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
fun BunkResultCard(res: BunkProjectorResult, lastUpdatedDate: String) {
    var expanded by remember { mutableStateOf(false) }

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
    val allSems = mutableListOf<CalendarContext>()
    try {
        val jsonString = try {
            com.vtop.utils.OtaManager.getCalendarJson(context)
        } catch (e: Exception) {
            context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
        }

        val root = JSONObject(jsonString)
        val keys = root.keys()

        while (keys.hasNext()) {
            val key = keys.next()
            val semBlock = root.optJSONObject(key) ?: continue

            val startStr = semBlock.optString("start_date", "").replace(" ", "")
            val endStr = semBlock.optString("last_instructional_day", "").replace(" ", "")

            var startDate = if (startStr.isNotEmpty()) LocalDate.parse(startStr) else LocalDate.MIN
            var endDate = if (endStr.isNotEmpty()) LocalDate.parse(endStr) else LocalDate.MAX

            if (startDate.isAfter(endDate) && startDate.year > endDate.year - 2) {
                startDate = startDate.minusYears(1)
            }

            val weekOffs = mutableListOf<String>()
            val wOffArr = semBlock.optJSONArray("week_off")
            if (wOffArr != null) {
                for (i in 0 until wOffArr.length()) {
                    weekOffs.add(wOffArr.getString(i))
                }
            }

            val holidaysMap = mutableMapOf<LocalDate, String>()
            val hols = semBlock.optJSONObject("holidays")
            if (hols != null) {
                val holKeys = hols.keys()
                while (holKeys.hasNext()) {
                    val hk = holKeys.next()
                    try { holidaysMap[LocalDate.parse(hk.replace(" ", ""))] = hols.getString(hk) } catch (_: Exception) {}
                }
            }

            val exams = semBlock.optJSONObject("exams")
            if (exams != null) {
                val examKeys = exams.keys()
                while (examKeys.hasNext()) {
                    val examType = examKeys.next()
                    val dates = exams.optJSONArray(examType)
                    if (dates != null) {
                        for (i in 0 until dates.length()) {
                            try { holidaysMap[LocalDate.parse(dates.getString(i).replace(" ", ""))] = "$examType Exam" } catch (_: Exception) {}
                        }
                    }
                }
            }

            val trueEndDate = holidaysMap.keys.maxOrNull()?.let {
                if (it.isAfter(endDate)) it else endDate
            } ?: endDate

            allSems.add(CalendarContext(key, startDate, endDate, trueEndDate, weekOffs, holidaysMap))
        }
    } catch (_: Exception) {}

    if (allSems.isEmpty()) return CalendarContext(semesterName = selectedSemester)

    val exactMatch = allSems.find { it.semesterName.equals(selectedSemester, ignoreCase = true) }
    if (exactMatch != null) return exactMatch

    val cleanSelected = selectedSemester.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9]"), "")
    val fuzzyMatch = allSems.find {
        val cleanKey = it.semesterName.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9]"), "")
        cleanKey.contains(cleanSelected) || cleanSelected.contains(cleanKey) ||
                (cleanSelected.contains("summer") && cleanKey.contains("summer")) ||
                (cleanSelected.contains("winter") && cleanKey.contains("winter"))
    }
    if (fuzzyMatch != null) {
        return fuzzyMatch.copy(semesterName = selectedSemester)
    }

    val today = LocalDate.now()
    val inSession = allSems.filter { !today.isBefore(it.startDate) && !today.isAfter(it.trueEndDate) }
    if (inSession.isNotEmpty()) return inSession.first().copy(semesterName = selectedSemester)

    val upcomingSems = allSems.filter { today.isBefore(it.startDate) }
    if (upcomingSems.isNotEmpty()) return upcomingSems.minByOrNull { it.startDate }!!.copy(semesterName = selectedSemester)

    return allSems.maxByOrNull { it.trueEndDate }?.copy(semesterName = selectedSemester) ?: CalendarContext(semesterName = selectedSemester)
}