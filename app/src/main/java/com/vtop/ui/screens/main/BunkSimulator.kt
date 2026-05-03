package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vtop.logic.BunkProjectorResult
import com.vtop.logic.BunkSimulator
import com.vtop.models.AttendanceModel
import com.vtop.models.TimetableModel
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import androidx.compose.foundation.border

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunkSimulatorTab(timetable: TimetableModel, attendanceData: List<AttendanceModel>, onBack: () -> Unit) {
    val context = LocalContext.current

    // Explicitly targeting the new JSON file
    val holidayMap = remember { buildComprehensiveHolidayMap(context) }

    var selectedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var showCalendar by remember { mutableStateOf(false) }
    var simulationResults by remember { mutableStateOf<List<BunkProjectorResult>>(emptyList()) }
    var hasRun by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        // Redesigned Header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Text("<", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Bunk Simulator", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }

        Text("Select specific dates to simulate how skipping classes will impact your final attendance percentage.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(modifier = Modifier.height(28.dp))

        // Redesigned Selector Button
        OutlinedCard(
            onClick = { showCalendar = true },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, if (selectedDates.isEmpty()) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (selectedDates.isEmpty()) "Tap to select dates..." else "${selectedDates.size} Dates Selected",
                    color = if (selectedDates.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("+", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Redesigned Action Button
        Button(
            onClick = {
                if (selectedDates.isNotEmpty()) {
                    try {
                        simulationResults = BunkSimulator.simulateMultiDayBunk(selectedDates.sorted(), timetable, attendanceData, holidayMap)
                        hasRun = true
                    } catch (e: Exception) { Toast.makeText(context, e.message, Toast.LENGTH_LONG).show() }
                } else Toast.makeText(context, "Please select at least one date", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
        ) {
            Text("RUN SIMULATION", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp, fontSize = 15.sp)
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                    ) {
                        Text("No attendance changes detected for the selected dates. You are safe!", color = Color(0xFF10B981), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp).fillMaxWidth())
                    }
                }
            } else {
                items(simulationResults) { result -> BunkResultCard(result) }
            }
        }
    }

    if (showCalendar) {
        Dialog(onDismissRequest = { showCalendar = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    MinimalMultiCalendar(selectedDates, holidayMap.keys) { date ->
                        selectedDates = if (selectedDates.contains(date)) selectedDates - date else selectedDates + date
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showCalendar = false },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("CONFIRM SELECTION", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
fun MinimalMultiCalendar(selectedDates: Set<LocalDate>, blockedDateKeys: Set<String>, onDateToggled: (LocalDate) -> Unit) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("<", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Black) }
            Text("${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${currentMonth.year}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 18.sp)
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp, fontWeight = FontWeight.Black) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val firstDayOfMonth = currentMonth.atDay(1)
        val startOffset = firstDayOfMonth.dayOfWeek.value % 7
        val daysInMonth = currentMonth.lengthOfMonth()
        val rows = ceil((startOffset + daysInMonth) / 7.0).toInt()

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 7) {
                    val dayIndex = row * 7 + col - startOffset + 1
                    if (dayIndex in 1..daysInMonth) {
                        val date = currentMonth.atDay(dayIndex)
                        val isHoliday = blockedDateKeys.contains(date.toString()) || blockedDateKeys.contains(date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")))
                        val isPast = date.isBefore(today)
                        val isSelected = selectedDates.contains(date)
                        val isToday = date == today
                        val isDisabled = isPast || isHoliday

                        val bgColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isHoliday -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                else -> Color.Transparent
                            }, animationSpec = tween(200)
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(bgColor)
                                .border(
                                    width = if (isToday && !isSelected) 2.dp else 0.dp,
                                    color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable(enabled = !isDisabled) { onDateToggled(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayIndex.toString(),
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isHoliday -> MaterialTheme.colorScheme.error
                                    isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    isToday -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontSize = 15.sp,
                                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.SemiBold
                            )
                        }
                    } else Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun BunkResultCard(res: BunkProjectorResult) {
    var expanded by remember { mutableStateOf(false) }
    val pctColor = if (res.isDanger) MaterialTheme.colorScheme.error else Color(0xFF10B981)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(res.courseCode, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(res.courseType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${res.currentPct.toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(" ➔ ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text("${res.projectedPct.toInt()}%", color = pctColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("CALCULATION TRACE", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Base: ${res.currentTotal} + (Gap) ${res.gapClassesAdded} + (Missed) ${res.missedClassesAdded} = ${res.projectedTotal} Total", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    val retroDeducted = res.missedClassesAdded - (res.projectedTotal - res.currentTotal - res.gapClassesAdded)
                    Text("Attended: ${res.currentAttended} + (Gap) ${res.gapClassesAdded} - (Retro) ${maxOf(0, retroDeducted)} = ${res.projectedAttended}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Final: (${res.projectedAttended} / ${res.projectedTotal}) * 100 = ${res.projectedPct}%", color = pctColor, fontSize = 12.sp, fontWeight = FontWeight.Black)

                    if (res.missedBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("CLASSES MISSED:", color = MaterialTheme.colorScheme.error, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        res.missedBreakdown.forEach { log ->
                            Text("• $log", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
fun buildComprehensiveHolidayMap(context: Context): Map<String, String> {
    val map = mutableMapOf<String, String>()
    try {
        // Updated to use academic_calendar.json explicitly
        val jsonString = context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
        val root = JSONObject(jsonString)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val now = System.currentTimeMillis()

        var activeSem: JSONObject? = null
        val weekOffs = mutableListOf<String>()
        var lastDay = ""

        val keys = root.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val semBlock = root.optJSONObject(key)
            if (semBlock != null) {
                val startStr = semBlock.optString("start_date", "")
                val endStr = semBlock.optString("last_instructional_day", "")
                if (startStr.isNotEmpty() && endStr.isNotEmpty()) {
                    val startMs = sdf.parse(startStr)?.time ?: 0L
                    val endMs = sdf.parse(endStr)?.time ?: 0L
                    val extendedEndMs = endMs + (30L * 24 * 60 * 60 * 1000)
                    if (now in startMs..extendedEndMs) {
                        activeSem = semBlock
                        break
                    }
                }
            }
        }

        if (activeSem != null) {
            lastDay = activeSem.optString("last_instructional_day", "")

            val hols = activeSem.optJSONObject("holidays")
            if (hols != null) {
                val holKeys = hols.keys()
                while (holKeys.hasNext()) {
                    val hk = holKeys.next()
                    map[hk] = hols.getString(hk)
                }
            }

            val exams = activeSem.optJSONObject("exams")
            if (exams != null) {
                val examKeys = exams.keys()
                while (examKeys.hasNext()) {
                    val examType = examKeys.next()
                    val dates = exams.optJSONArray(examType)
                    if (dates != null) {
                        for (i in 0 until dates.length()) {
                            map[dates.getString(i)] = "$examType Exam"
                        }
                    }
                }
            }

            val wOffArr = activeSem.optJSONArray("week_off")
            if (wOffArr != null) {
                for (i in 0 until wOffArr.length()) {
                    weekOffs.add(wOffArr.getString(i).uppercase(Locale.getDefault()))
                }
            }
        }

        if (lastDay.isNotEmpty() && weekOffs.isNotEmpty()) {
            val endLocalDate = LocalDate.parse(lastDay)
            var current = LocalDate.now()
            while (!current.isAfter(endLocalDate)) {
                val dayName = current.dayOfWeek.name
                if (weekOffs.contains(dayName)) {
                    map[current.toString()] = "Week Off"
                }
                current = current.plusDays(1)
            }
        }
    } catch (_: Exception) {}

    return map
}