package com.vtop.ui.screens.sub

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val weight: Int,
    val isDefaultBunk: Boolean = true
) {
    val key: String
        get() = "$date|$courseCode|$courseType|$slot"
}

data class AuditLogEntry(
    val dateStr: String,
    val dayOfWeek: String,
    val reason: String,
    val deltaClasses: Int,
    val isBunk: Boolean,
    val occurrences: List<BunkOccurrence>
)

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
    val auditStartDateStr: String,
    val auditStartAttended: Int,
    val auditStartTotal: Int,
    val auditLog: List<AuditLogEntry>,
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

            // Refined check to explicitly allow LAB FAT as an instructional day
            val isHoliday = title.contains("holiday") ||
                    title.contains("no instructional") ||
                    title.contains("non instructional") ||
                    title.contains("exam") ||
                    (title.contains("cat") && !title.contains("vacation")) ||
                    (title.contains("fat") && !title.contains("lab fat"))

            if (isHoliday) {
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
    val prefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }

    val semesterId = remember(selectedSemester) { com.vtop.utils.Vault.getSelectedSemester(context)[0] }
    val academicCalendar = remember(selectedSemester) { com.vtop.utils.Vault.getAcademicCalendar(context, semesterId) }
    val calCtx = remember(selectedSemester) { getCalendarContext(context, selectedSemester) }
    val today = LocalDate.now()

    // --- Persistent State & Exam Lookups ---
    var selectedTarget by remember {
        mutableStateOf(prefs.getString("BUNK_TARGET", "AUTO") ?: "AUTO")
    }
    var customTargetDate by remember {
        val savedEpoch = prefs.getLong("BUNK_CUSTOM_DATE", -1L)
        val initialDate = if (savedEpoch != -1L) Instant.ofEpochMilli(savedEpoch).atZone(ZoneId.of("UTC")).toLocalDate() else null
        mutableStateOf<LocalDate?>(initialDate)
    }

    var showTargetDatePicker by remember { mutableStateOf(false) }

    val allExams = remember(academicCalendar) { ExamAttendanceProjector.getAllExams(academicCalendar) }

    val examTarget = remember(selectedTarget, allExams, today, customTargetDate) {
        when (selectedTarget) {
            "AUTO" -> allExams.firstOrNull { !it.startDate.isBefore(today) }
            "EOS" -> null
            "CUSTOM" -> {
                val referenceDate = customTargetDate ?: today
                allExams.firstOrNull { !it.startDate.isBefore(referenceDate) }
            }
            else -> allExams.firstOrNull { it.name == selectedTarget }
        }
    }

    val calculationEndDate = when (selectedTarget) {
        "CUSTOM" -> customTargetDate ?: calCtx.trueEndDate
        "EOS" -> calCtx.trueEndDate
        else -> examTarget?.cutoffDate ?: calCtx.trueEndDate
    }

    val examName = when (selectedTarget) {
        "CUSTOM" -> "CUSTOM DATE"
        "EOS" -> "END OF SEMESTER"
        else -> examTarget?.name ?: "END OF SEMESTER"
    }

    var selectedDates by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var attendanceOverrides by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBunkDatePicker by remember { mutableStateOf(false) }

    // --- Core Calculation Pipeline (Derived State) ---
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

                val attended = att.attendedClasses?.toIntOrNull() ?: 0
                val total = att.totalClasses?.toIntOrNull() ?: 0
                val currentPct = if (total > 0) attended.toFloat() / total * 100f else 0f

                var remaining = 0
                var plannedBunks = 0
                val selectedOccurrences = mutableListOf<BunkOccurrence>()
                val auditLog = mutableListOf<AuditLogEntry>()

                val dFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
                val dateOnlyFmt = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
                val dayOnlyFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)

                val auditStartStr = lastPostedDate?.format(dFormatter) ?: "Semester Start"

                var curr = projectionStartDate
                while (!curr.isAfter(calculationEndDate)) {
                    if (isInstructionalDay(curr, calCtx)) {
                        val matchingClasses = getClassesForDate(timetable, curr).filter {
                            it.courseCode == code && isSameTypeGroup(it.courseType, att.courseType)
                        }

                        val weight = matchingClasses.sumOf { getSlotWeight(it.slot) }

                        if (weight > 0) {
                            remaining += weight

                            val isSelected = selectedDates.contains(curr)
                            val isGapDay = !curr.isAfter(today)

                            val dStr = curr.format(dateOnlyFmt)
                            val dayStr = curr.format(dayOnlyFmt)

                            val bunkOccs = mutableListOf<BunkOccurrence>()
                            val attendOccs = mutableListOf<BunkOccurrence>()

                            matchingClasses.forEach { session ->
                                val sessionWeight = getSlotWeight(session.slot)

                                if (sessionWeight > 0) {
                                    val isDefaultBunk = isSelected
                                    val occurrence = BunkOccurrence(
                                        date = curr,
                                        courseCode = code,
                                        courseType = att.courseType ?: "",
                                        slot = session.slot ?: "",
                                        weight = sessionWeight,
                                        isDefaultBunk = isDefaultBunk
                                    )

                                    val isBunking = if (isDefaultBunk) {
                                        occurrence.key !in attendanceOverrides
                                    } else {
                                        occurrence.key in attendanceOverrides
                                    }

                                    if (isSelected || isGapDay || isBunking) {
                                        selectedOccurrences += occurrence
                                    }

                                    if (isBunking) {
                                        plannedBunks += sessionWeight
                                        bunkOccs += occurrence
                                    } else {
                                        attendOccs += occurrence
                                    }
                                }
                            }

                            if (bunkOccs.isNotEmpty()) {
                                val dWeight = bunkOccs.sumOf { it.weight }
                                val reason = if (isGapDay) "Not posted (Bunked)" else "Planned Bunk"
                                auditLog.add(AuditLogEntry(dStr, dayStr, reason, dWeight, true, bunkOccs))
                            }

                            if (attendOccs.isNotEmpty()) {
                                val dWeight = attendOccs.sumOf { it.weight }
                                val reason = if (isGapDay) "Not posted (Attended)" else "Future class"
                                auditLog.add(AuditLogEntry(dStr, dayStr, reason, dWeight, false, attendOccs))
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
                    auditStartDateStr = auditStartStr,
                    auditStartAttended = attended,
                    auditStartTotal = total,
                    auditLog = auditLog,
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    if (examTarget != null) {
                        val isPast = examTarget.startDate.isBefore(today)
                        Text(
                            text = "${examTarget.name} starts on ${examTarget.startDate.format(dateFormatter)}.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    val inlineContent = mapOf(
                        "editIcon" to InlineTextContent(
                            Placeholder(
                                width = 15.sp,
                                height = 15.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change Date",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )

                    Text(
                        text = buildAnnotatedString {
                            append("Attendance might be calculated until ")
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append(calculationEndDate.format(dateFormatter))
                            }
                            append(" ")
                            appendInlineContent("editIcon", "[edit]")
                            append(".")
                        },
                        inlineContent = inlineContent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showTargetDatePicker = true }
                            .padding(vertical = 2.dp)
                    )
                }

                Box {
                    var targetDropdownExpanded by remember { mutableStateOf(false) }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.clickable { targetDropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val btnText = when (selectedTarget) {
                                "AUTO" -> "Auto"
                                "EOS" -> "EOS"
                                "CUSTOM" -> "Custom"
                                else -> selectedTarget
                            }
                            val isError = examTarget != null && examTarget.startDate.isBefore(today)

                            Text(
                                text = btnText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(start = 4.dp),
                                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    MaterialTheme(
                        shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenu(
                            expanded = targetDropdownExpanded,
                            onDismissRequest = { targetDropdownExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            val isAuto = selectedTarget == "AUTO"
                            DropdownMenuItem(
                                text = { Text("Auto-detect", fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) },
                                trailingIcon = if (isAuto) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } } else null,
                                colors = MenuDefaults.itemColors(textColor = if (isAuto) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                                onClick = {
                                    selectedTarget = "AUTO"
                                    prefs.edit().putString("BUNK_TARGET", "AUTO").apply()
                                    targetDropdownExpanded = false
                                }
                            )

                            allExams.forEach { exam ->
                                val isPast = exam.startDate.isBefore(today)
                                val isSelected = selectedTarget == exam.name
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            exam.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    },
                                    trailingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = if (isPast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } } else null,
                                    colors = MenuDefaults.itemColors(
                                        textColor = if (isPast) MaterialTheme.colorScheme.error
                                        else if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    ),
                                    onClick = {
                                        selectedTarget = exam.name
                                        prefs.edit().putString("BUNK_TARGET", exam.name).apply()
                                        targetDropdownExpanded = false
                                    }
                                )
                            }

                            val isEos = selectedTarget == "EOS"
                            DropdownMenuItem(
                                text = { Text("End of Semester", fontWeight = if (isEos) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp) },
                                trailingIcon = if (isEos) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) } } else null,
                                colors = MenuDefaults.itemColors(textColor = if (isEos) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                                onClick = {
                                    selectedTarget = "EOS"
                                    prefs.edit().putString("BUNK_TARGET", "EOS").apply()
                                    targetDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
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
                            onClick = { showBunkDatePicker = true },
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
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(groupedCourses, key = { it.courseCode }) { course ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    CourseBunkCard(
                        course = course,
                        examName = examName,
                        calculationDateStr = calculationEndDate.format(dateFormatter),
                        attendanceOverrides = attendanceOverrides,
                        onAttendanceOverrideChange = { occurrence, wantsToAttend ->
                            val wantsToBunk = !wantsToAttend
                            val needsOverride = occurrence.isDefaultBunk != wantsToBunk

                            attendanceOverrides = if (needsOverride) {
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

    if (showBunkDatePicker) {
        MultiSelectDatePickerDialog(
            initialSelectedDates = selectedDates,
            onDismissRequest = { showBunkDatePicker = false },
            onDatesSelected = { newDates ->
                val validDateStrings = newDates.map { it.toString() }.toSet()
                attendanceOverrides = attendanceOverrides.filter { key ->
                    key.substringBefore("|") in validDateStrings
                }.toSet()
                selectedDates = newDates
                showBunkDatePicker = false
            }
        )
    }

    // --- Target Date Picker Dialog ---
    if (showTargetDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customTargetDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
                ?: today.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showTargetDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        customTargetDate = newDate
                        selectedTarget = "CUSTOM"
                        prefs.edit()
                            .putString("BUNK_TARGET", "CUSTOM")
                            .putLong("BUNK_CUSTOM_DATE", millis)
                            .apply()
                    }
                    showTargetDatePicker = false
                }) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDatePicker = false }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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
    var showDetailsDialog by rememberSaveable { mutableStateOf(false) }

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
                                val hasOverride = occurrence.key in attendanceOverrides
                                val isBunking = if (occurrence.isDefaultBunk) !hasOverride else hasOverride
                                val isAttending = !isBunking
                                val wantsToAttend = isBunking

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val dFormatter = DateTimeFormatter.ofPattern("dd MMM · EEEE", Locale.ENGLISH)
                                        Text(
                                            occurrence.date.format(dFormatter),
                                            fontSize = 12.sp,
                                            color = if (!occurrence.isDefaultBunk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val slotStr = if (occurrence.weight == 1) occurrence.slot else "${occurrence.slot} · ${occurrence.weight} classes"
                                        Text(
                                            if (!occurrence.isDefaultBunk) "$slotStr (Not posted)" else slotStr,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
                                        onClick = { onAttendanceOverrideChange(occurrence, wantsToAttend) },
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

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val remStr = if (activeComponent.remainingClasses == 1) "class" else "classes"
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                append("${activeComponent.remainingClasses}")
                            }
                            append(" $remStr remaining")
                        },
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "View Calculation",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { showDetailsDialog = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = {
                Text(
                    text = "${course.courseCode} (${activeComponent.displayType})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {

                    // --- Header Card: Starting Point ---
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("STARTING POINT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(activeComponent.auditStartDateStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("${activeComponent.auditStartAttended} / ${activeComponent.auditStartTotal}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    // --- Table Header ---
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.20f))
                        Text("DETAILS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.50f))
                        Text("ACTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.30f), textAlign = TextAlign.End)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // --- Scrollable Table Body ---
                    if (activeComponent.auditLog.isEmpty()) {
                        Text(
                            text = "No classes found before target date.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp).align(Alignment.CenterHorizontally)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            items(activeComponent.auditLog) { entry ->
                                val isInteractive = entry.occurrences.isNotEmpty()
                                val impactStr = if (entry.isBunk) "-${entry.deltaClasses}" else "+${entry.deltaClasses}"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (entry.isBunk) MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (entry.isBunk) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Date Column
                                    Column(modifier = Modifier.weight(0.20f)) {
                                        Text(entry.dateStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(entry.dayOfWeek, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    // Details Column
                                    Row(modifier = Modifier.weight(0.50f), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (entry.isBunk) Icons.Default.Close else Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (entry.isBunk) MaterialTheme.colorScheme.error else Color(0xFF10B981),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${entry.reason} ($impactStr)",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    // Action Column
                                    Box(modifier = Modifier.weight(0.30f), contentAlignment = Alignment.CenterEnd) {
                                        if (isInteractive) {
                                            val isAttending = !entry.isBunk
                                            Surface(
                                                color = if (isAttending) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.clickable {
                                                    val newAttendState = entry.isBunk // Toggle the current state
                                                    entry.occurrences.forEach { occ ->
                                                        onAttendanceOverrideChange(occ, newAttendState)
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = if (isAttending) "- Bunk" else "+ Add",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isAttending) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                        } else {
                                            Text("-", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Footer Card: Final Projected ---
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("FINAL PROJECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.7f))
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                Text("${activeComponent.projectedPct.roundToInt()}%", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("${activeComponent.projectedAttended} / ${activeComponent.projectedTotal}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}