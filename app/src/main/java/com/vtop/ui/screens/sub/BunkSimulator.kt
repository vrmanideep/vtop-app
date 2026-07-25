package com.vtop.ui.screens.sub

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.logic.ExamAttendanceProjector
import com.vtop.models.*
import com.vtop.utils.AnalyticsManager
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

// --- UI Data Models ---

data class BunkOccurrence(
    val date: LocalDate,
    val courseCode: String,
    val courseType: String,
    val slot: String,
    val weight: Int
) {
    val key: String
        get() = "$date|$courseCode|$courseType|$slot"
}

data class CourseTypeBunkUiModel(
    val displayType: String,
    val currentAttended: Int,
    val currentTotal: Int,
    val currentPct: Float,
    val remainingClasses: Int,
    val plannedBunks: Int,
    val selectedOccurrences: List<BunkOccurrence>,
    val projectedAttended: Int,
    val projectedTotal: Int,
    val projectedPct: Float,
    val additionalSafeBunks: Int,
    val noData: Boolean = false
)

data class CourseBunkUiModel(
    val courseCode: String,
    val courseName: String?,
    val theory: CourseTypeBunkUiModel?,
    val lab: CourseTypeBunkUiModel?
)

data class CalendarContext(
    val semesterName: String = "Unknown Semester",
    val startDate: LocalDate = LocalDate.MIN,
    val endDate: LocalDate = LocalDate.MAX,
    val trueEndDate: LocalDate = LocalDate.MAX,
    val weekOffs: List<String> = emptyList(),
    val holidays: Map<LocalDate, String> = emptyMap()
)

// --- Helper Functions ---

private fun parseAttendanceDate(
    dateStr: String?,
    referenceDate: LocalDate
): LocalDate? {
    if (dateStr.isNullOrBlank()) return null

    return try {
        val value = dateStr.trim()
        val datePart = value.substringAfter(",").trim()
        val parts = datePart.split("-")

        if (parts.size != 2) return null

        val day = parts[0].toInt()
        val month = parts[1].toInt()

        LocalDate.of(referenceDate.year, month, day)
    } catch (_: Exception) {
        null
    }
}

private fun getPostedDates(
    attendance: AttendanceModel,
    referenceDate: LocalDate
): Set<LocalDate> {
    return attendance.history
        ?.mapNotNull {
            parseAttendanceDate(it.date, referenceDate)
        }
        ?.toSet()
        .orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectDatePickerDialog(
    initialSelectedDates: Set<LocalDate>,
    onDismissRequest: () -> Unit,
    onDatesSelected: (Set<LocalDate>) -> Unit
) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var localSelectedDates by remember { mutableStateOf(initialSelectedDates) }

    DatePickerDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onDatesSelected(localSelectedDates) }) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", fontWeight = FontWeight.Bold)
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)) {
                Text(
                    text = "SELECT DATES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (localSelectedDates.isEmpty()) "No dates selected"
                    else "${localSelectedDates.size} days selected",
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Row {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Month")
                    }
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Month")
                    }
                }
            }

            val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                daysOfWeek.forEach { dow ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(text = dow, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val firstDayOfMonth = currentMonth.atDay(1)
            val startOffset = (firstDayOfMonth.dayOfWeek.value % 7) // Shift so Sunday = 0
            val daysInMonth = currentMonth.lengthOfMonth()
            val totalCells = startOffset + daysInMonth
            val rows = kotlin.math.ceil(totalCells / 7f).toInt()

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                for (row in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val day = cellIndex - startOffset + 1

                            if (day in 1..daysInMonth) {
                                val date = currentMonth.atDay(day)
                                val isSelected = localSelectedDates.contains(date)
                                val isToday = date == LocalDate.now()

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            localSelectedDates = if (isSelected) localSelectedDates - date else localSelectedDates + date
                                        }
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            width = if (isToday && !isSelected) 1.dp else 0.dp,
                                            color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.toString(),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

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

private fun isLabType(type: String?): Boolean {
    val t = type?.uppercase() ?: return false
    return t.contains("L") || t.contains("P") || t.contains("PRACTICAL") || t.contains("ELA")
}

private fun getSlotWeight(slotStr: String?): Int {
    if (slotStr.isNullOrBlank() || slotStr == "-" || slotStr.equals("N/A", ignoreCase = true)) return 0
    return slotStr.split("+").size
}

private fun getClassesForDate(
    timetable: TimetableModel,
    date: LocalDate
): List<CourseSession> {
    val dayKey = date.dayOfWeek.name
    return timetable.scheduleMap?.entries?.firstOrNull {
        it.key.equals(dayKey, ignoreCase = true)
    }?.value.orEmpty()
}

@SuppressLint("NewApi")
fun getCalendarContext(context: Context, selectedSemester: String): CalendarContext {
    val semId = com.vtop.utils.Vault.getSelectedSemester(context)[0]
    val liveEvents = com.vtop.utils.Vault.getAcademicCalendar(context, semId)

    if (liveEvents.isEmpty()) return CalendarContext(semesterName = selectedSemester)

    val sdfParse = java.text.SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
    var startDate = java.time.LocalDate.MAX
    var endDate = java.time.LocalDate.MIN
    val holidays = mutableMapOf<LocalDate, String>()
    val weekOffs = listOf("SUNDAY")

    for (event in liveEvents) {
        try {
            val dateObj = sdfParse.parse(event.date) ?: continue
            val localDate = Instant.ofEpochMilli(dateObj.time).atZone(ZoneId.systemDefault()).toLocalDate()
            val title = event.particulars.lowercase(Locale.ENGLISH)

            if (title.contains("commencement")) {
                if (localDate.isBefore(startDate)) startDate = localDate
            }
            if (title.contains("last instructional day") || title.contains("last working day") || title.contains("last day")) {
                if (localDate.isAfter(endDate)) endDate = localDate
            }
            if (title.contains("holiday") || title.contains("exam") || title.contains("cat") || title.contains("fat") ||
                title.contains("no instructional") || title.contains("non instructional")) {
                holidays[localDate] = event.particulars
            }
        } catch (_: Exception) {}
    }

    if (startDate == java.time.LocalDate.MAX) {
        startDate = try {
            val d = sdfParse.parse(liveEvents.first().date)
            Instant.ofEpochMilli(d!!.time).atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) { LocalDate.MIN }
    }

    if (endDate == java.time.LocalDate.MIN) {
        endDate = try {
            val d = sdfParse.parse(liveEvents.last().date)
            Instant.ofEpochMilli(d!!.time).atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) { LocalDate.MAX }
    }

    return CalendarContext(
        semesterName = selectedSemester,
        startDate = startDate,
        endDate = endDate,
        trueEndDate = endDate,
        weekOffs = weekOffs,
        holidays = holidays
    )
}

// --- Main Composable ---

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
    val semesterId = remember(selectedSemester) { com.vtop.utils.Vault.getSelectedSemester(context)[0] }
    val academicCalendar = remember(selectedSemester) { com.vtop.utils.Vault.getAcademicCalendar(context, semesterId) }
    val calCtx = remember(selectedSemester) { getCalendarContext(context, selectedSemester) }

    val examTarget = remember(academicCalendar) { ExamAttendanceProjector.findNextExam(academicCalendar) }
    val calculationEndDate = examTarget?.cutoffDate ?: calCtx.trueEndDate
    val examName = examTarget?.name ?: "END OF SEMESTER"

    var selectedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var attendanceOverrides by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showDatePicker by remember { mutableStateOf(false) }

    val today = LocalDate.now()

    // --- Core Calculation Pipeline (Derived State) ---
    // Now depends directly on `selectedDates` instead of `appliedDates`
    val groupedCourses = remember(selectedDates, attendanceOverrides, timetable, attendanceData, calculationEndDate, calCtx) {
        val courseMap = mutableMapOf<String, MutableList<AttendanceModel>>()
        attendanceData.forEach { att ->
            val code = att.courseCode ?: return@forEach
            courseMap.getOrPut(code) { mutableListOf() }.add(att)
        }

        courseMap.mapNotNull { (code, attList) ->
            var theoryModel: CourseTypeBunkUiModel? = null
            var labModel: CourseTypeBunkUiModel? = null

            val courseName = timetable.scheduleMap?.values?.flatten()?.firstOrNull { it.courseCode == code }?.courseName

            attList.forEach { att ->
                val isLab = isLabType(att.courseType)
                val displayType = if (isLab) "Lab" else "Theory"

                val noData = try { att.history.isNullOrEmpty() } catch (_: Exception) { true }
                val postedDates = getPostedDates(att, today)
                val lastPostedDate = postedDates.maxOrNull()

                val projectionStartDate = lastPostedDate?.plusDays(1) ?: calCtx.startDate

                Log.d(
                    "BUNK_BOUNDARY",
                    "course=$code type=${att.courseType} " +
                            "posted=$postedDates " +
                            "lastPosted=$lastPostedDate " +
                            "projectionStart=$projectionStartDate " +
                            "cutoff=$calculationEndDate"
                )

                val attended = att.attendedClasses?.toIntOrNull() ?: 0
                val total = att.totalClasses?.toIntOrNull() ?: 0
                val currentPct = if (total > 0) attended.toFloat() / total * 100f else 0f

                var remaining = 0
                var plannedBunks = 0
                val selectedOccurrences = mutableListOf<BunkOccurrence>()

                var curr = projectionStartDate
                while (!curr.isAfter(calculationEndDate)) {
                    if (isInstructionalDay(curr, calCtx)) {
                        val matchingClasses = getClassesForDate(timetable, curr).filter {
                            it.courseCode == code && isSameTypeGroup(it.courseType, att.courseType)
                        }

                        val weight = matchingClasses.sumOf { getSlotWeight(it.slot) }

                        if (weight > 0) {
                            Log.d(
                                "BUNK_PROJECT",
                                "course=$code type=${att.courseType} " +
                                        "date=$curr weight=$weight " +
                                        "selected=${selectedDates.contains(curr)}"
                            )

                            remaining += weight

                            if (selectedDates.contains(curr)) {
                                matchingClasses.forEach { session ->
                                    val sessionWeight = getSlotWeight(session.slot)

                                    if (sessionWeight > 0) {
                                        val occurrence = BunkOccurrence(
                                            date = curr,
                                            courseCode = code,
                                            courseType = att.courseType ?: "",
                                            slot = session.slot ?: "",
                                            weight = sessionWeight
                                        )

                                        selectedOccurrences += occurrence

                                        if (occurrence.key !in attendanceOverrides) {
                                            plannedBunks += sessionWeight
                                        }
                                    }
                                }
                            }
                        }
                    }
                    curr = curr.plusDays(1)
                }

                val projectedAttended = attended + remaining - plannedBunks
                val projectedTotal = total + remaining
                val projectedPct = if (projectedTotal > 0) projectedAttended.toFloat() / projectedTotal * 100f else 0f

                val rawSafe = floor(projectedAttended - 0.75 * projectedTotal).toInt()
                val remainingUnselected = (remaining - plannedBunks).coerceAtLeast(0)
                val additionalSafeBunks = rawSafe.coerceIn(0, remainingUnselected)

                val model = CourseTypeBunkUiModel(
                    displayType = displayType,
                    currentAttended = attended,
                    currentTotal = total,
                    currentPct = currentPct,
                    remainingClasses = remaining,
                    plannedBunks = plannedBunks,
                    selectedOccurrences = selectedOccurrences,
                    projectedAttended = projectedAttended,
                    projectedTotal = projectedTotal,
                    projectedPct = projectedPct,
                    additionalSafeBunks = additionalSafeBunks,
                    noData = noData
                )

                if (isLab) labModel = model else theoryModel = model
            }

            if (theoryModel == null && labModel == null) return@mapNotNull null
            CourseBunkUiModel(code, courseName, theoryModel, labModel)
        }.sortedBy { it.courseCode }
    }

    fun getSimulatableClassCount(date: LocalDate): Int {
        if (!isInstructionalDay(date, calCtx)) return 0
        val sessions = getClassesForDate(timetable, date)
        var totalSimulatableWeight = 0

        for (session in sessions) {
            val matchingAtts = attendanceData.filter {
                it.courseCode == session.courseCode && isSameTypeGroup(it.courseType, session.courseType)
            }

            if (matchingAtts.isEmpty()) {
                totalSimulatableWeight += getSlotWeight(session.slot)
            } else {
                val simulatable = matchingAtts.any { att ->
                    val lastPosted = getPostedDates(att, today).maxOrNull()
                    lastPosted == null || date.isAfter(lastPosted)
                }
                if (simulatable) {
                    totalSimulatableWeight += getSlotWeight(session.slot)
                }
            }
        }
        return totalSimulatableWeight
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 20.dp, end = 20.dp)) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text("Bunk Simulator", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        }

        val dateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (examTarget != null) {
                    Text(
                        text = "$examName starts on ${examTarget.startDate.format(dateFormatter)}.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = buildAnnotatedString {
                        append("Attendance might be calculated until ")
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(calculationEndDate.format(dateFormatter))
                        }
                        append(".")
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (calCtx.startDate != LocalDate.MIN) {
                        if (today.isBefore(calCtx.startDate)) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Classes begin on ${calCtx.startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}.", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else if (today.isAfter(calCtx.trueEndDate)) {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("${calCtx.semesterName} is completely finished.", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Text(
                        text = "SELECT DAYS TO BUNK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
                    )

                    val quickDates = listOf(
                        "Today" to today,
                        "Tomorrow" to today.plusDays(1),
                        today.plusDays(2).dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() } to today.plusDays(2)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickDates.forEach { (label, date) ->
                            val isSelected = selectedDates.contains(date)
                            val count = getSimulatableClassCount(date)
                            val chipLabel = if (count > 0) "$label · $count classes" else "$label · No classes"

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        selectedDates = selectedDates - date
                                        attendanceOverrides = attendanceOverrides.filterNot { key ->
                                            key.startsWith("$date|")
                                        }.toSet()
                                    } else {
                                        selectedDates = selectedDates + date
                                    }
                                },
                                label = { Text(chipLabel, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(50)
                            )
                        }

                        FilterChip(
                            selected = false,
                            onClick = { showDatePicker = true },
                            label = { Text("+ Pick Date", fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar", modifier = Modifier.size(16.dp)) },
                            shape = RoundedCornerShape(50)
                        )
                    }

                    if (selectedDates.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            selectedDates.sorted().forEach { date ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedDates = selectedDates - date
                                            attendanceOverrides = attendanceOverrides.filterNot { key ->
                                                key.startsWith("$date|")
                                            }.toSet()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            TextButton(
                                onClick = {
                                    selectedDates = emptySet()
                                    attendanceOverrides = emptySet()
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Clear all", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "COURSE DETAILS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                    )
                }
            }

            items(groupedCourses, key = { it.courseCode }) { course ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    CourseBunkCard(
                        course = course,
                        examName = examName,
                        calculationDateStr = calculationEndDate.format(dateFormatter),
                        attendanceOverrides = attendanceOverrides,
                        onAttendanceOverrideChange = { occurrence, attend ->
                            attendanceOverrides = if (attend) {
                                attendanceOverrides + occurrence.key
                            } else {
                                attendanceOverrides - occurrence.key
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        MultiSelectDatePickerDialog(
            initialSelectedDates = selectedDates,
            onDismissRequest = { showDatePicker = false },
            onDatesSelected = { newDates ->
                val validDateStrings = newDates.map { it.toString() }.toSet()
                attendanceOverrides = attendanceOverrides.filter { key ->
                    key.substringBefore("|") in validDateStrings
                }.toSet()
                selectedDates = newDates
                showDatePicker = false
            }
        )
    }
}

// --- Course Card Composable ---

@Composable
fun CourseBunkCard(
    course: CourseBunkUiModel,
    examName: String,
    calculationDateStr: String,
    attendanceOverrides: Set<String>,
    onAttendanceOverrideChange: (BunkOccurrence, Boolean) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(if (course.theory != null) "Theory" else "Lab") }

    val activeComponent = if (selectedTab == "Theory") course.theory else course.lab
    if (activeComponent == null) return

    val isSafe = activeComponent.projectedPct >= 75f && !activeComponent.noData
    val isDanger = activeComponent.projectedPct < 75f && !activeComponent.noData

    val statusColor = when {
        activeComponent.noData -> Color(0xFFF59E0B)
        isDanger -> MaterialTheme.colorScheme.error
        else -> Color(0xFF10B981)
    }

    var bunksExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(course.courseCode, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    if (!course.courseName.isNullOrBlank()) {
                        Text(course.courseName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when {
                            activeComponent.noData -> "NO DATA"
                            isDanger -> "BELOW 75%"
                            else -> "SAFE"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            if (course.theory != null && course.lab != null) {
                Spacer(Modifier.height(16.dp))
                TabRow(
                    selectedTabIndex = if (selectedTab == "Theory") 0 else 1,
                    containerColor = Color.Transparent,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) },
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[if (selectedTab == "Theory") 0 else 1]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == "Theory",
                        onClick = { selectedTab = "Theory" },
                        text = { Text("Theory", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Tab(
                        selected = selectedTab == "Lab",
                        onClick = { selectedTab = "Lab" },
                        text = { Text("Lab", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (activeComponent.noData) {
                Text(
                    text = "Attendance history unavailable.",
                    color = statusColor,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CURRENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${activeComponent.currentPct.roundToInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Text("${activeComponent.currentAttended} / ${activeComponent.currentTotal}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("UNTIL $examName", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${activeComponent.projectedPct.roundToInt()}%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = statusColor)
                        Text("${activeComponent.projectedAttended} / ${activeComponent.projectedTotal}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (activeComponent.selectedOccurrences.isNotEmpty()) {
                    val classStr = if (activeComponent.plannedBunks == 1) "class" else "classes"

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { bunksExpanded = !bunksExpanded }.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                        append("Bunking ${activeComponent.plannedBunks}")
                                    }
                                    append(" $classStr")
                                },
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = if (bunksExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (bunksExpanded) {
                            Spacer(Modifier.height(8.dp))
                            activeComponent.selectedOccurrences.forEach { occurrence ->
                                val isAttending = occurrence.key in attendanceOverrides
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val dFormatter = DateTimeFormatter.ofPattern("dd MMM · EEEE", Locale.ENGLISH)
                                        Text(occurrence.date.format(dFormatter), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        val slotStr = if (occurrence.weight == 1) occurrence.slot else "${occurrence.slot} · ${occurrence.weight} classes"
                                        Text(slotStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Button(
                                        onClick = { onAttendanceOverrideChange(occurrence, !isAttending) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAttending) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            contentColor = if (isAttending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = null
                                    ) {
                                        Text(
                                            text = if (isAttending) "Bunk" else "Attend",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (isDanger) {
                        val overBy = floor((0.75 * activeComponent.projectedTotal) - activeComponent.projectedAttended).toInt().coerceAtLeast(1)
                        val overByStr = if (overBy == 1) "class" else "classes"
                        Text(
                            text = buildAnnotatedString {
                                append("Over safe limit by ")
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("$overBy")
                                }
                                append(" $overByStr")
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(vertical = 2.dp)
                        )
                    } else {
                        val safeStr = if (activeComponent.additionalSafeBunks == 1) "class" else "classes"
                        Text(
                            text = buildAnnotatedString {
                                append("Can still bunk ")
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("${activeComponent.additionalSafeBunks}")
                                }
                                append(" more $safeStr")
                            },
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                } else {
                    if (isDanger) {
                        val toAttend = floor((0.75 * activeComponent.projectedTotal) - activeComponent.projectedAttended).toInt().coerceAtLeast(1)
                        val attendStr = if (toAttend == 1) "class" else "classes"
                        Text(
                            text = buildAnnotatedString {
                                append("Must attend next ")
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("$toAttend")
                                }
                                append(" $attendStr")
                            },
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(vertical = 2.dp)
                        )
                    } else {
                        val safeStr = if (activeComponent.additionalSafeBunks == 1) "class" else "classes"
                        Text(
                            text = buildAnnotatedString {
                                append("Can ")
                                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append("bunk ${activeComponent.additionalSafeBunks}")
                                }
                                append(" $safeStr")
                            },
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }

                val remStr = if (activeComponent.remainingClasses == 1) "class" else "classes"
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append("${activeComponent.remainingClasses}")
                        }
                        append(" $remStr ")
                        withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append("remaining")
                        }
                    },
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}