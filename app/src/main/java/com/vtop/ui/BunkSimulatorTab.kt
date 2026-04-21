package com.vtop.ui

import android.annotation.SuppressLint
import androidx.compose.ui.window.Dialog
import android.widget.Toast
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
import com.vtop.models.AttendanceModel
import com.vtop.models.TimetableModel
import com.vtop.logic.BunkSimulator
import com.vtop.logic.BunkProjectorResult
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import androidx.compose.foundation.border

// Lives in RAM. Dies when the app is swiped away.
object DevSettings {
    var isDevModeEnabled by mutableStateOf(false)
}

@SuppressLint("NewApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BunkSimulatorTab(timetable: TimetableModel, attendanceData: List<AttendanceModel>, onBack: () -> Unit) {
    val context = LocalContext.current

    val holidayMap = remember { loadHolidaysFromAssets(context) }

    var selectedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var showCalendar by remember { mutableStateOf(false) }

    var simulationResults by remember { mutableStateOf<List<BunkProjectorResult>>(emptyList()) }
    var hasRun by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("<", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Bunk Simulator", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }

        Text("Select specific dates to simulate how skipping classes will impact your final attendance percentage.", color = Color.Gray, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { showCalendar = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFF333333)),
            shape = RoundedCornerShape(12.dp)
        ) {
            val text = if (selectedDates.isEmpty()) "Tap to Select Dates" else "${selectedDates.size} Dates Selected"
            Text(text, color = if (selectedDates.isEmpty()) Color.Gray else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (selectedDates.isNotEmpty()) {
                    try {
                        val datesList = selectedDates.sorted()
                        simulationResults = BunkSimulator.simulateMultiDayBunk(
                            validDates = datesList,
                            timetable = timetable,
                            attendanceData = attendanceData,
                            blockedDates = holidayMap
                        )
                        hasRun = true
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Please select at least one date", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("RUN SIMULATION", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFF222222))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (hasRun && simulationResults.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No attendance changes detected for the selected dates. You are safe!", color = Color(0xFF4CAF50), textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(simulationResults) { result ->
                    BunkResultCard(result)
                }
            }
        }
    }

    // Beautiful Custom Centered Dialog Calendar
    if (showCalendar) {
        Dialog(onDismissRequest = { showCalendar = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    MinimalMultiCalendar(
                        selectedDates = selectedDates,
                        blockedDateKeys = holidayMap.keys,
                        onDateToggled = { date ->
                            selectedDates = if (selectedDates.contains(date)) selectedDates - date else selectedDates + date
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showCalendar = false },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("DONE", color = Color.Black, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
fun MinimalMultiCalendar(
    selectedDates: Set<LocalDate>,
    blockedDateKeys: Set<String>,
    onDateToggled: (LocalDate) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    val today = LocalDate.now()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Text("<", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            val monthName = currentMonth.month.name.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
            Text("$monthName ${currentMonth.year}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Text(">", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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

                        // Check if the date is in the JSON Holidays
                        val isoDateStr = date.toString()
                        val dmyDateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                        val isHoliday = blockedDateKeys.contains(isoDateStr) || blockedDateKeys.contains(dmyDateStr)

                        val isPast = date.isBefore(today)
                        val isSelected = selectedDates.contains(date)

                        // THE FIX: Disable if past OR if it's a holiday
                        val isDisabled = isPast || isHoliday

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> Color.White
                                        isHoliday -> Color(0xFF3B1A1A) // Visually mark holidays
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(enabled = !isDisabled) { onDateToggled(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayIndex.toString(),
                                color = when {
                                    isSelected -> Color.Black
                                    isHoliday -> Color(0xFFF44336).copy(alpha = 0.5f) // Reddish grey for holidays
                                    isPast -> Color(0xFFAAAAAA) // Grey for past dates
                                    else -> Color.White
                                },
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@SuppressLint("NewApi")
@Composable
fun BunkResultCard(res: BunkProjectorResult) {
    val context = LocalContext.current
    val cardColor = if (res.isDanger) Color(0xFF3B1A1A) else Color(0xFF141414)
    val strokeColor = if (res.isDanger) Color(0xFFF44336).copy(alpha = 0.5f) else Color(0xFF333333)
    val pctColor = if (res.isDanger) Color(0xFFF44336) else Color(0xFF4CAF50)

    // --- SMART DATE FORMATTER (Transforms to "07-Apr-26") ---
    val formattedDate = remember(res.lastUpdatedStr) {
        if (res.lastUpdatedStr == "Today (Assumed)") return@remember "Today"
        try {
            val clean = res.lastUpdatedStr.substringAfter(",").trim()
            val parts = clean.split("-")
            val year = when {
                parts.size >= 3 && parts[0].length == 4 -> parts[0].toInt()
                parts.size >= 3 && parts[2].length == 4 -> parts[2].toInt()
                else -> LocalDate.now().year
            }
            val month = parts[1].toInt()
            val day = when {
                parts.size >= 3 && parts[0].length == 4 -> parts[2].toInt()
                else -> parts[0].toInt()
            }
            LocalDate.of(year, month, day).format(DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH))
        } catch (e: Exception) {
            res.lastUpdatedStr // Fallback if parsing fails
        }
    }

    // --- 5-TAP DEV MODE TRACKER ---
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, strokeColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 400) {
                    tapCount++
                    if (tapCount >= 5) {
                        DevSettings.isDevModeEnabled = !DevSettings.isDevModeEnabled
                        tapCount = 0
                        val msg = if (DevSettings.isDevModeEnabled) "🛠 DEV MODE ACTIVATED" else "🔒 DEV MODE DISABLED"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    tapCount = 1
                }
                lastTapTime = now
            }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${res.courseCode} (${res.courseType})", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (res.isDanger) {
                    Text(
                        "DANGER",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFFF44336), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    // THE FIX: Added formatted date to the Current label
                    Text("Current (As of $formattedDate)", color = Color.Gray, fontSize = 11.sp)
                    Text("${String.format(Locale.US, "%.0f", res.currentPct)}% (${res.currentAttended}/${res.currentTotal})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Projected", color = Color.Gray, fontSize = 11.sp)
                    Text("${String.format(Locale.US, "%.0f", res.projectedPct)}% (${res.projectedAttended}/${res.projectedTotal})", color = pctColor, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }

            if (res.gapClassesAdded > 0 || res.missedClassesAdded > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(8.dp))

                if (res.gapClassesAdded > 0) {
                    Text("In-Between: +${res.gapClassesAdded} classes (Assuming present)", color = Color(0xFF4CAF50), fontSize = 12.sp)
                }
                if (res.missedClassesAdded > 0) {
                    Text("Bunking: -${res.missedClassesAdded} classes", color = Color(0xFFF44336), fontSize = 12.sp)
                    res.missedBreakdown.forEach { log ->
                        Text(" • $log", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                    }
                }
            }

            // ==========================================
            // --- RAW DEV MODE MATH DROPDOWN ---
            // ==========================================
            if (DevSettings.isDevModeEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text("🛠 RAW MATH TRACE", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Last Known VTOP Date: ${res.lastUpdatedStr}", color = Color.LightGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Total Base: ${res.currentTotal} + (Gap) ${res.gapClassesAdded} + (Missed) ${res.missedClassesAdded} = ${res.projectedTotal}", color = Color.Yellow, fontSize = 11.sp)

                    val retroDeducted = res.missedClassesAdded - (res.projectedTotal - res.currentTotal - res.gapClassesAdded)

                    if (retroDeducted > 0) {
                        Text("Attended Base: ${res.currentAttended} + (Gap) ${res.gapClassesAdded} - (Retro Bunked) $retroDeducted = ${res.projectedAttended}", color = Color.Yellow, fontSize = 11.sp)
                    } else {
                        Text("Attended Base: ${res.currentAttended} + (Gap) ${res.gapClassesAdded} = ${res.projectedAttended}", color = Color.Yellow, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Final Math: (${res.projectedAttended} / ${res.projectedTotal}) * 100 = ${res.projectedPct}%", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
fun loadHolidaysFromAssets(context: android.content.Context): Map<String, String> {
    val map = mutableMapOf<String, String>()
    try {
        val jsonString = context.assets.open("bunk_cache.json").bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        if (jsonObject.has("blocked_dates")) {
            val blocked = jsonObject.getJSONObject("blocked_dates")
            val keys = blocked.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = blocked.getString(key)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return map
}