@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UseKtx", "RedundantSamConstructor")

package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.models.AttendanceModel
import com.vtop.models.CourseSession
import com.vtop.models.ExamScheduleModel
import com.vtop.models.TimetableModel
import com.vtop.ui.core.CourseReminder
import com.vtop.ui.core.ReminderManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

val ColorDanger = Color(0xFFF87171)

@Composable
fun getPremiumSurfaceColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF141414) else Color(0xFFFFFFFF)

@Composable
fun getPremiumBorderColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)

private fun String?.clean(): String {
    if (this == null || this.isBlank() || this.trim() == "-" || this.trim().equals("TBD", true) || this.trim().equals("N/A", true) || this.trim().equals("null", true)) {
        return " "
    }
    return this.trim()
}

private fun formatReminderDate(dateStr: String): String {
    return try {
        val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val outFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)
        val d = inFormat.parse(dateStr)
        if (d != null) outFormat.format(d) else dateStr
    } catch (_: Exception) { dateStr }
}

enum class TimeStatus { PAST, ONGOING, NEXT, FUTURE }

@SuppressLint("SimpleDateFormat")
fun getCourseTimeStatus(timeSlot: String?, isToday: Boolean, isNextInLine: Boolean): TimeStatus {
    if (!isToday) return TimeStatus.FUTURE
    if (timeSlot.isNullOrEmpty()) return TimeStatus.FUTURE

    try {
        val parts = timeSlot.split("-").map { it.trim() }
        if (parts.size < 2) return TimeStatus.FUTURE
        val sdf = SimpleDateFormat(if (parts[0].contains(Regex("[a-zA-Z]"))) "hh:mm a" else "HH:mm", Locale.ENGLISH)
        val nowCal = Calendar.getInstance()
        val currentMins = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        val startCal = Calendar.getInstance().apply { time = sdf.parse(parts[0])!! }
        val startMins = startCal.get(Calendar.HOUR_OF_DAY) * 60 + startCal.get(Calendar.MINUTE)
        val endCal = Calendar.getInstance().apply { time = sdf.parse(parts[1])!! }
        val endMins = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE)

        return when {
            currentMins > endMins -> TimeStatus.PAST
            currentMins in startMins..endMins -> TimeStatus.ONGOING
            isNextInLine -> TimeStatus.NEXT
            else -> TimeStatus.FUTURE
        }
    } catch (_: Exception) { return TimeStatus.FUTURE }
}

private fun isSameTypeGroup(a: String?, b: String?): Boolean {
    if (a == b) return true
    val aLab = a?.contains("L") == true || a?.contains("P") == true
    val bLab = b?.contains("L") == true || b?.contains("P") == true
    return aLab == bLab
}

data class ProcessedCourse(
    val originalSession: CourseSession,
    val mergedTimeSlot: String,
    val mergedSlot: String
) {
    val courseCode: String? get() = originalSession.courseCode
    val courseName: String? get() = originalSession.courseName
    val courseType: String? get() = originalSession.courseType
    val classId: String? get() = originalSession.classId
    val venue: String? get() = originalSession.venue
    val faculty: String? get() = originalSession.faculty
}

fun processAndMergeCourses(courses: List<CourseSession>, mergeLabs: Boolean): List<ProcessedCourse> {
    val timeSorted = courses.sortedBy { course ->
        try {
            val timeStr = course.timeSlot?.split("-")?.firstOrNull()?.trim() ?: ""
            if (timeStr.isNotEmpty()) {
                val sdf = SimpleDateFormat(if (timeStr.contains(Regex("[a-zA-Z]"))) "hh:mm a" else "HH:mm", Locale.ENGLISH)
                sdf.parse(timeStr)?.time ?: Long.MAX_VALUE
            } else { Long.MAX_VALUE }
        } catch (e: Exception) { Long.MAX_VALUE }
    }
    if (!mergeLabs) return timeSorted.map { ProcessedCourse(it, it.timeSlot ?: "", it.slot ?: "") }
    val result = mutableListOf<ProcessedCourse>()
    var current: ProcessedCourse? = null
    for (course in timeSorted) {
        val isLab = course.courseType?.contains("L") == true || course.courseType?.contains("P") == true
        val processed = ProcessedCourse(course, course.timeSlot ?: "", course.slot ?: "")
        if (current == null) { current = processed } else {
            val currentIsLab = current.courseType?.contains("L") == true || current.courseType?.contains("P") == true
            if (isLab && currentIsLab && current.courseCode == course.courseCode) {
                val start = current.mergedTimeSlot.split("-").firstOrNull()?.trim() ?: ""
                val end = course.timeSlot?.split("-")?.lastOrNull()?.trim() ?: ""
                val newTime = if (start.isNotEmpty() && end.isNotEmpty()) "$start - $end" else current.mergedTimeSlot
                val newSlot = "${current.mergedSlot}+${course.slot}"
                current = current.copy(mergedTimeSlot = newTime, mergedSlot = newSlot)
            } else { result.add(current); current = processed }
        }
    }
    if (current != null) result.add(current)
    return result
}

private fun findExamForDate(dateCal: Calendar, exams: List<ExamScheduleModel>): ExamScheduleModel? {
    val targetYear = dateCal.get(Calendar.YEAR)
    val targetDayOfYear = dateCal.get(Calendar.DAY_OF_YEAR)

    return exams.find { exam ->
        val dateStr = exam.examDate ?: return@find false
        try {
            val formats = listOf("dd-MMM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MMM dd, yyyy")
            var matched = false
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                    val d = sdf.parse(dateStr.trim())
                    if (d != null) {
                        val c = Calendar.getInstance().apply { time = d }
                        if (c.get(Calendar.YEAR) == targetYear && c.get(Calendar.DAY_OF_YEAR) == targetDayOfYear) {
                            matched = true
                            break
                        }
                    }
                } catch (e: Exception) { }
            }
            matched
        } catch (e: Exception) { false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SimpleDateFormat")
@Composable
fun Timetable(timetable: TimetableModel, attendanceData: List<AttendanceModel>, examsData: List<ExamScheduleModel> = emptyList()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 30)
    var allReminders by remember { mutableStateOf(ReminderManager.loadReminders(context)) }
    val sdfDateKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val sdfDayFull = remember { SimpleDateFormat("EEEE", Locale.ENGLISH) }
    val sdfDayShort = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val todayDateStr = sdfDateKey.format(Calendar.getInstance().time)
    val timelineDates = remember { (-30..60).map { offset -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) } } }
    var expandedDateStr by remember { mutableStateOf(todayDateStr) }
    var selectedCourse by remember { mutableStateOf<ProcessedCourse?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showJumpToToday by remember { derivedStateOf { listState.firstVisibleItemIndex !in 25..35 } }
    var tick by remember { mutableIntStateOf(0) }
    var currentTimeStr by remember { mutableStateOf(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())) }

    LaunchedEffect(Unit) {
        while (true) { delay(60000); currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()); tick++ }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            NextClassCard(timetable, allReminders, tick, examsData)
            LazyColumn(
                state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(timelineDates) { index, dateCal ->
                    val dateStr = sdfDateKey.format(dateCal.time)
                    val dayName = sdfDayFull.format(dateCal.time)
                    val dayShort = sdfDayShort.format(dateCal.time)
                    val isToday = dateStr == todayDateStr
                    val isWeekend = dayName == "Saturday" || dayName == "Sunday"
                    val examToday = findExamForDate(dateCal, examsData)
                    val rawCourses = if (examToday != null) emptyList() else timetable.scheduleMap[dayName] ?: emptyList()
                    val isExpanded = expandedDateStr == dateStr
                    val daysOffset = abs(index - 30)
                    val rowAlpha = when (daysOffset) { 0 -> 1f; 1, 2 -> 0.85f; in 3..5 -> 0.6f; in 6..14 -> 0.35f; else -> 0.15f }

                    if (examToday != null) {
                        ExamRow(dateCal, examToday, isToday, isExpanded, rowAlpha, onExpandToggle = { expandedDateStr = if (isExpanded) "" else dateStr })
                    } else if (rawCourses.isEmpty() && isWeekend && !isExpanded) {
                        WeekendSeparator(dayShort, dateCal.get(Calendar.DAY_OF_MONTH), rowAlpha)
                    } else {
                        TimetableRow(
                            dateCal, rawCourses, allReminders, isToday, isExpanded, rowAlpha, tick,
                            onExpandToggle = { expandedDateStr = if (isExpanded) "" else dateStr },
                            onCourseClick = { selectedCourse = it }
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showJumpToToday, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 40.dp)
        ) {
            Button(
                onClick = { coroutineScope.launch { listState.animateScrollToItem(30) } },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(20.dp), elevation = ButtonDefaults.buttonElevation(8.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("$currentTimeStr • Today", color = MaterialTheme.colorScheme.surface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (selectedCourse != null) {
        val bottomSheetBg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF111111) else Color(0xFFFFFFFF)
        ModalBottomSheet(onDismissRequest = { selectedCourse = null }, sheetState = sheetState, containerColor = bottomSheetBg) {
            CourseDetailsSheet(course = selectedCourse!!, attendanceData = attendanceData, allReminders = allReminders, onRemindersUpdated = { allReminders = it })
        }
    }
}

@Composable
fun NextClassCard(timetable: TimetableModel, allReminders: List<CourseReminder>, tick: Int, exams: List<ExamScheduleModel>) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val themeOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val nextEvent = remember(timetable, tick, exams) {
        val mergeLabs = sharedPrefs.getBoolean("MERGE_LABS", true)
        val cal = Calendar.getInstance()
        val examToday = findExamForDate(cal, exams)
        if (examToday != null) return@remember Pair(examToday, "EXAM TODAY" to themePrimary)
        val todayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        val todayCourses = processAndMergeCourses(timetable.scheduleMap[todayStr] ?: emptyList(), mergeLabs)
        val nextToday = todayCourses.firstOrNull { course ->
            val status = getCourseTimeStatus(course.mergedTimeSlot, true, true)
            status == TimeStatus.NEXT || status == TimeStatus.ONGOING
        }
        if (nextToday != null) return@remember Pair(nextToday, "NEXT UP" to themePrimary.copy(alpha = 0.8f))
        for (i in 1..7) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val examFuture = findExamForDate(cal, exams)
            if (examFuture != null) return@remember Pair(examFuture, "UPCOMING EXAM" to themePrimary)
            val futureDayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
            val futureCourses = processAndMergeCourses(timetable.scheduleMap[futureDayStr] ?: emptyList(), mergeLabs)
            if (futureCourses.isNotEmpty()) return@remember Pair(futureCourses.first(), (if (i == 1) "TOMORROW" else futureDayStr.uppercase()) to themeOnSurfaceVariant)
        }
        null
    }

    if (nextEvent != null) {
        val (event, header) = nextEvent
        val (headerText, headerColor) = header
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()), border = BorderStroke(1.dp, if(headerText.contains("EXAM")) themePrimary.copy(0.3f) else getPremiumBorderColor())
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(headerText, fontSize = 9.sp, color = headerColor, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    if (event is ExamScheduleModel) {
                        Text(event.courseCode.clean(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                        Text("${event.examType.clean()} · ${event.reportingTime.clean()}", fontSize = 12.sp, color = themeOnSurfaceVariant)
                    } else if (event is ProcessedCourse) {
                        Text(event.courseCode.clean(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                        Text(event.mergedTimeSlot.clean(), fontSize = 12.sp, color = themeOnSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (event is ExamScheduleModel) {
                        val v = event.venue.clean()
                        val sl = event.seatLocation.clean()
                        val sn = event.seatNumber.clean()
                        val rightStr = if (v == " " && sl == " " && sn == " ") " " else "$v - $sl ($sn)"

                        Text(rightStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themePrimary)
                    } else if (event is ProcessedCourse) {
                        val activeReminder = allReminders.find { it.classId == event.classId }
                        Text(event.mergedSlot.clean(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (activeReminder != null) Text(activeReminder.type.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = ColorDanger, modifier = Modifier.padding(top = 4.dp))
                        else Text(event.venue.clean(), fontSize = 12.sp, color = themeOnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ExamRow(dateCal: Calendar, exam: ExamScheduleModel, isToday: Boolean, isExpanded: Boolean, alpha: Float, onExpandToggle: () -> Unit) {
    val sdfDayShort = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val themePrimary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).alpha(alpha).clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(themePrimary.copy(0.15f), Color.Transparent)))
            .border(1.5.dp, themePrimary.copy(0.4f), RoundedCornerShape(14.dp))
            .clickable { onExpandToggle() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isToday) "TODAY" else dateCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = themePrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(sdfDayShort.format(dateCal.time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val eType = exam.examType.clean()
                Text(if (eType == " ") "EXAM" else eType, fontSize = 10.sp, fontWeight = FontWeight.Black, color = themePrimary, modifier = Modifier.background(themePrimary.copy(0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(exam.courseCode.clean(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    Text(exam.courseTitle.clean(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (!isExpanded) {
                    val v = exam.venue.clean()
                    val sl = exam.seatLocation.clean()
                    val sn = exam.seatNumber.clean()
                    val rightStr = if (v == " " && sl == " " && sn == " ") " " else "$v and $sl ($sn)"

                    Text(
                        text = rightStr,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp)); HorizontalDivider(color = themePrimary.copy(0.2f)); Spacer(Modifier.height(12.dp))

                val eTime = (exam.examTime ?: exam.reportingTime).clean()
                DetailRow("Exam time", eTime)
                DetailRow("Venue", exam.venue.clean())
                DetailRow("Seat location", exam.seatLocation.clean())
                DetailRow("Seat number", exam.seatNumber.clean())
                DetailRow("Class ID", exam.classId.clean())
            }
        }
    }
}

@Composable
fun TimetableRow(dateCal: Calendar, rawCourses: List<CourseSession>, allReminders: List<CourseReminder>, isToday: Boolean, isExpanded: Boolean, alpha: Float, tick: Int, onExpandToggle: () -> Unit, onCourseClick: (ProcessedCourse) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    val themePrimary = MaterialTheme.colorScheme.primary
    val mergedCourses = remember(rawCourses) { processAndMergeCourses(rawCourses, sharedPrefs.getBoolean("MERGE_LABS", true)) }
    val courseStatuses = remember(mergedCourses, tick) {
        var foundNextIndex = -1
        mergedCourses.mapIndexed { index, course ->
            val isNextInLine = foundNextIndex == -1
            val status = getCourseTimeStatus(course.mergedTimeSlot, isToday, isNextInLine)
            if (status == TimeStatus.NEXT || status == TimeStatus.ONGOING) foundNextIndex = index
            status
        }
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).alpha(alpha).scale(if (isToday) 1f else 0.96f).clip(RoundedCornerShape(14.dp))
        .background(if (isExpanded) Brush.verticalGradient(listOf(if (isToday) getPremiumSurfaceColor() else Color.Transparent, Color.Transparent)) else SolidColor(if (isToday) getPremiumSurfaceColor() else Color.Transparent))
        .border(1.dp, if (isToday || isExpanded) getPremiumBorderColor() else Color.Transparent, RoundedCornerShape(14.dp))
        .clickable { onExpandToggle() }.animateContentSize()
    ) {
        if (isExpanded) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(dateCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Text(SimpleDateFormat("EEE", Locale.ENGLISH).format(dateCal.time), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${mergedCourses.size} Classes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                if (mergedCourses.isEmpty()) Text("No Classes 🎉", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp))
                else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(mergedCourses) { index, course ->
                            val reminder = allReminders.find { it.classId == course.classId }
                            ClassTile(course, courseStatuses[index], reminder?.type) { onCourseClick(course) }
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isToday) "Today" else SimpleDateFormat("EEE", Locale.ENGLISH).format(dateCal.time), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(dateCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                    Text(if (mergedCourses.isEmpty()) "No Classes " else "${mergedCourses.size} Classes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                if (mergedCourses.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                        courseStatuses.forEach { status ->
                            val barColor = when (status) { TimeStatus.PAST -> MaterialTheme.colorScheme.onSurfaceVariant; TimeStatus.ONGOING, TimeStatus.NEXT -> themePrimary; TimeStatus.FUTURE -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) }
                            Box(modifier = Modifier.padding(horizontal = 3.dp).width(20.dp).height(if (status == TimeStatus.ONGOING) 5.dp else 3.dp).background(brush = Brush.horizontalGradient(listOf(barColor.copy(alpha = 0.6f), barColor)), shape = CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassTile(course: ProcessedCourse, status: TimeStatus, reminderType: String?, onClick: () -> Unit) {
    val themePrimary = MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.width(85.dp).height(110.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()),
        border = when (status) { TimeStatus.ONGOING -> BorderStroke(1.5.dp, themePrimary); TimeStatus.NEXT -> BorderStroke(1.dp, themePrimary.copy(alpha = 0.5f)); else -> BorderStroke(1.dp, getPremiumBorderColor()) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(8.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Text(course.courseCode.clean(), fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                val pillColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
                Box(modifier = Modifier.padding(top = 4.dp).background(pillColor, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text(course.mergedTimeSlot.split("-").firstOrNull()?.trim() ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(course.mergedSlot.clean(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailsSheet(
    course: ProcessedCourse, attendanceData: List<AttendanceModel>, allReminders: List<CourseReminder>,
    onRemindersUpdated: (List<CourseReminder>) -> Unit
) {
    val context = LocalContext.current
    val themePrimary = MaterialTheme.colorScheme.primary

    val activeReminders = allReminders.filter { it.classId == course.classId }
    var editingReminderId by remember(course.classId) { mutableStateOf<String?>(null) }
    var isEditingReminder by remember(course.classId) { mutableStateOf(false) }

    val attendance = attendanceData.find {
        it.classId == course.classId && !it.classId.isNullOrBlank()
    } ?: attendanceData.find {
        it.courseCode == course.courseCode && isSameTypeGroup(it.courseType, course.courseType)
    } ?: attendanceData.find { it.courseCode == course.courseCode }

    val attendanceStr = attendance?.attendancePercentage?.replace("%", "") ?: "N/A"
    val attenValue = attendanceStr.toFloatOrNull() ?: 0f
    val attenColor = if (attendanceStr == "N/A") MaterialTheme.colorScheme.onSurfaceVariant else if (attenValue >= 75f) Color(0xFF4ADE80) else ColorDanger
    var isViewingAttendance by remember(course.classId) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)).padding(horizontal = 24.dp).padding(bottom = 32.dp).imePadding().verticalScroll(rememberScrollState())
    ) {
        if (isViewingAttendance && attendance != null) {

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isViewingAttendance = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
                Text("Attendance Details", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            }
            com.vtop.ui.screens.main.AttendanceDetailCore(course = attendance, onSimulateClick = null)

        } else if (isEditingReminder) {
            val reminderToEdit = activeReminders.find { it.id == editingReminderId }

            Text(if (editingReminderId == null) "New Reminder" else "Edit Reminder", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))

            val isLab = course.courseType?.contains("L") == true || course.courseType?.contains("P") == true
            val predefinedTypes = remember(isLab) { mutableListOf("Quiz", "Assignment").apply { if (isLab) addAll(listOf("Viva", "Record")); add("Others") } }

            var selectedType by remember(course.classId, editingReminderId) { mutableStateOf(when { reminderToEdit == null -> "Quiz"; predefinedTypes.contains(reminderToEdit.type) -> reminderToEdit.type; else -> "Others" }) }
            var customType by remember(course.classId, editingReminderId) { mutableStateOf(if (selectedType == "Others") reminderToEdit?.type ?: "" else "") }
            val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
            var selectedDate by remember(course.classId, editingReminderId) { mutableStateOf(reminderToEdit?.date ?: sdf.format(Date())) }
            var showDatePicker by remember { mutableStateOf(false) }
            var syllabus by remember(course.classId, editingReminderId) { mutableStateOf(reminderToEdit?.syllabus ?: "") }

            Text("TYPE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                predefinedTypes.forEach { type ->
                    val isSelected = selectedType == type
                    Box(modifier = Modifier.background(if (isSelected) themePrimary else getPremiumSurfaceColor(), RoundedCornerShape(8.dp)).clickable { selectedType = type }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(type, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (selectedType == "Others") {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = customType, onValueChange = { customType = it }, label = { Text("Custom Type", color = MaterialTheme.colorScheme.onSurfaceVariant) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Spacer(Modifier.height(16.dp))
            Text("DATE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()), border = BorderStroke(1.dp, getPremiumBorderColor())) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, null, tint = themePrimary)
                    Spacer(Modifier.width(12.dp))
                    Text(selectedDate, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(initialSelectedDateMillis = sdf.parse(selectedDate)?.time?.plus(TimeZone.getDefault().rawOffset))
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }; selectedDate = sdf.format(cal.time) }; showDatePicker = false }) { Text("OK", color = themePrimary) } },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                ) { DatePicker(state = datePickerState) }
            }
            Spacer(Modifier.height(16.dp))
            Text("SYLLABUS / NOTES", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = syllabus, onValueChange = { syllabus = it }, modifier = Modifier.fillMaxWidth().height(100.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = themePrimary, unfocusedBorderColor = MaterialTheme.colorScheme.outline, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface), placeholder = { Text("Modules, topics, or notes...", color = MaterialTheme.colorScheme.onSurfaceVariant) })
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { isEditingReminder = false }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = getPremiumSurfaceColor()), shape = RoundedCornerShape(12.dp)) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
                }
                Button(
                    onClick = {
                        val finalType = if (selectedType == "Others") customType.ifBlank { "Task" } else selectedType
                        val newReminder = CourseReminder(
                            id = editingReminderId ?: java.util.UUID.randomUUID().toString(),
                            courseCode = course.courseCode ?: "", classId = course.classId ?: "", type = finalType, date = selectedDate, syllabus = syllabus
                        )
                        val updatedList = allReminders.toMutableList()
                        if (editingReminderId != null) updatedList.removeAll { it.id == editingReminderId }
                        updatedList.add(newReminder)
                        ReminderManager.saveReminders(context, updatedList)
                        onRemindersUpdated(updatedList)
                        isEditingReminder = false
                    },
                    modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = themePrimary), shape = RoundedCornerShape(12.dp)
                ) { Text("SAVE", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black) }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(course.courseCode.clean(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = themePrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$attendanceStr%", fontSize = 16.sp, fontWeight = FontWeight.Black, color = attenColor)
                    if (attendance != null) {
                        Box(modifier = Modifier.background(getPremiumSurfaceColor(), RoundedCornerShape(6.dp)).clickable { isViewingAttendance = true }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("VIEW", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text = course.courseName.clean(), fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, lineHeight = 28.sp)
            Spacer(Modifier.height(24.dp))
            DetailRow(label = "TIME", value = course.mergedTimeSlot.clean())
            DetailRow(label = "SLOT", value = course.mergedSlot.clean())
            DetailRow(label = "VENUE", value = course.venue.clean())
            DetailRow(label = "FACULTY", value = course.faculty.clean())
            DetailRow(label = "CLASS ID", value = course.classId.clean())
            Spacer(Modifier.height(24.dp))

            activeReminders.forEach { reminder ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()),
                    border = BorderStroke(1.dp, getPremiumBorderColor()),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reminder.type, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Due: ${formatReminderDate(reminder.date)}", color = themePrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (reminder.syllabus.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(reminder.syllabus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row {
                            IconButton(onClick = { editingReminderId = reminder.id; isEditingReminder = true }) {
                                Icon(Icons.Outlined.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val updatedList = allReminders.toMutableList().apply { removeAll { it.id == reminder.id } }
                                ReminderManager.saveReminders(context, updatedList)
                                onRemindersUpdated(updatedList)
                            }) {
                                Icon(Icons.Default.DeleteOutline, "Delete", tint = ColorDanger)
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { editingReminderId = null; isEditingReminder = true },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                border = BorderStroke(1.dp, getPremiumBorderColor())
            ) {
                Text("+ Add Reminder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(text = value, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 16.dp))
    }
}

@Composable
fun WeekendSeparator(day: String, date: Int, alpha: Float) {
    Row(modifier = Modifier.fillMaxWidth().alpha(alpha).padding(vertical = 8.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(getPremiumBorderColor()))
        Text("$day $date".uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        Box(modifier = Modifier.weight(1f).height(1.dp).background(getPremiumBorderColor()))
    }
}