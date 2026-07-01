@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE", "UNUSED_PARAMETER", "UseKtx", "RedundantSamConstructor")

package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import com.vtop.utils.AnalyticsManager
import com.vtop.utils.Vault
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

val ColorDanger = Color(0xFFF87171)
private var cachedFacultyArray: org.json.JSONArray? = null

@Composable
fun getPremiumSurfaceColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF141414) else Color(0xFFFFFFFF)

@Composable
fun getPremiumBorderColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)

private fun String?.clean(): String {
    if (this.isNullOrBlank() || this.trim() == "-" || this.trim().equals("TBD", ignoreCase = true) || this.trim().equals("N/A", ignoreCase = true) || this.trim().equals("null", ignoreCase = true)) {
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

private fun parseTimeMillis(value: String?): Long {
    if (value.isNullOrBlank()) return Long.MAX_VALUE
    return try {
        val cleaned = value.trim()
        if (cleaned.contains(Regex("[a-zA-Z]"))) {
            time12Parser.parse(cleaned)?.time ?: Long.MAX_VALUE
        } else {
            time24Parser.parse(cleaned)?.time ?: Long.MAX_VALUE
        }
    } catch (_: Exception) {
        Long.MAX_VALUE
    }
}

private val time24Parser = SimpleDateFormat("HH:mm", Locale.ENGLISH)
private val time12Parser = SimpleDateFormat("hh:mm a", Locale.ENGLISH)

private fun parseSlotStartMillis(slot: String?): Long {
    if (slot.isNullOrBlank()) return Long.MAX_VALUE
    return try {
        val start = slot.split("-").firstOrNull()?.trim() ?: return Long.MAX_VALUE
        val parser = if (start.contains(Regex("[a-zA-Z]"))) time12Parser else time24Parser
        parser.parse(start)?.time ?: Long.MAX_VALUE
    } catch (_: Exception) { Long.MAX_VALUE }
}

fun processAndMergeCourses(courses: List<CourseSession>, mergeLabs: Boolean): List<ProcessedCourse> {
    if (courses.isEmpty()) return emptyList()

    val sorted = courses.sortedBy { parseSlotStartMillis(it.timeSlot) }
    if (!mergeLabs) {
        return sorted.map { ProcessedCourse(originalSession = it, mergedTimeSlot = it.timeSlot ?: "", mergedSlot = it.slot ?: "") }
    }

    val merged = mutableListOf<ProcessedCourse>()
    var activeGroup: MutableList<CourseSession> = mutableListOf()

    fun flushGroup() {
        if (activeGroup.isEmpty()) return
        val first = activeGroup.first()
        val last = activeGroup.last()
        val start = first.timeSlot?.split("-")?.firstOrNull()?.trim() ?: ""
        val end = last.timeSlot?.split("-")?.lastOrNull()?.trim() ?: ""
        val mergedSlot = activeGroup.joinToString("+") { it.slot ?: "" }
        val mergedTime = if (start.isNotBlank() && end.isNotBlank()) "$start - $end" else first.timeSlot ?: ""
        merged.add(ProcessedCourse(originalSession = first, mergedTimeSlot = mergedTime, mergedSlot = mergedSlot))
        activeGroup.clear()
    }

    sorted.forEach { session ->
        if (activeGroup.isEmpty()) {
            activeGroup.add(session)
            return@forEach
        }
        val previous = activeGroup.last()
        val sameCourse = previous.courseCode == session.courseCode
        val sameType = isSameTypeGroup(previous.courseType, session.courseType)
        val prevType = previous.courseType?.uppercase() ?: ""
        val currentType = session.courseType?.uppercase() ?: ""
        val mergeable = isSameTypeGroup(prevType, currentType)

        val shouldMerge = sameCourse && sameType && mergeable
        if (shouldMerge) {
            activeGroup.add(session)
        } else {
            flushGroup()
            activeGroup.add(session)
        }
    }
    flushGroup()
    return merged
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SimpleDateFormat")
@Composable
fun Timetable(
    timetable: TimetableModel,
    attendanceData: List<AttendanceModel>,
    examsData: List<ExamScheduleModel> = emptyList(),
    holidays: Map<String, String> = emptyMap() // Left for backwards compatibility, ignored internally
) {
    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Timetable_Screen") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var allReminders by remember { mutableStateOf(ReminderManager.loadReminders(context)) }
    val sdfDateKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }
    val sdfDayFull = remember { SimpleDateFormat("EEEE", Locale.ENGLISH) }
    val sdfDayShort = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val todayDateStr = sdfDateKey.format(Calendar.getInstance().time)
    val timelineDates = remember { (-14..30).map { offset -> Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) } } }

    val todayIndex = remember(timelineDates) {
        timelineDates.indexOfFirst {
            val now = Calendar.getInstance()
            it.get(Calendar.YEAR) == now.get(Calendar.YEAR) && it.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        }.coerceAtLeast(0)
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)

    LaunchedEffect(todayIndex) {
        if (todayIndex >= 0) {
            listState.scrollToItem(todayIndex + 1)
        }
    }

    // --- NEW DYNAMIC HOLIDAY LOGIC ---
    val selectedSemId = remember { Vault.getSelectedSemester(context)[0] }
    val academicEvents = remember(selectedSemId) { Vault.getAcademicCalendar(context, selectedSemId) }

    val normalizedHolidays = remember(academicEvents) {
        val holidayMap = mutableMapOf<String, String>()
        val sdfParse = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

        academicEvents.forEach { event ->
            val title = event.particulars ?: ""

            val isHoliday = title.contains("Holiday", true) ||
                    title.contains("no instructional", true) ||
                    title.contains("non instructional", true) ||
                    title.contains("VITOPIA", true)

            if (isHoliday) {
                try {
                    val date = sdfParse.parse(event.date)
                    if (date != null) {
                        val cal = Calendar.getInstance().apply { time = date }
                        val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                        val isMonday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY

                        val isGenericPlaceholder = title.equals("Holiday", ignoreCase = true) ||
                                title.equals("Holiday (Holiday)", ignoreCase = true) ||
                                title.contains("Sunday", ignoreCase = true) ||
                                title.contains("Instructional Day (Holiday)", ignoreCase = true) ||
                                title.contains("No Instructional", ignoreCase = true)

                        val dropGenericMonday = isMonday && (
                                title.equals("No Instructional Day (Non Instructional Day)", ignoreCase = true) ||
                                        title.equals("No Instructional Day (No Instructional Day)", ignoreCase = true)
                                )

                        if (!(isSunday && isGenericPlaceholder) && !dropGenericMonday) {
                            var cleanTitle = title.replace(" - General (Semester)", "")
                                .replace(" - Combined", "")
                                .replace(" (Holiday)", "", ignoreCase = true)
                                .replace("\n", " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()

                            if (cleanTitle.contains("VITOPIA", ignoreCase = true)) cleanTitle = "VITOPIA"

                            holidayMap[sdfKey.format(date)] = cleanTitle
                        }
                    }
                } catch (e: Exception) {}
            }
        }
        holidayMap
    }
    // ---------------------------------

    var expandedDateStr by remember { mutableStateOf(todayDateStr) }
    var selectedCourse by remember { mutableStateOf<ProcessedCourse?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val showJumpToToday by remember { derivedStateOf { abs(listState.firstVisibleItemIndex - (todayIndex + 1)) > 3 } }
    var currentTimeStr by remember { mutableStateOf(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())) }

    // THE FIX: Normalized String list for exact chronological lookup of exams
    val sortedExams = remember(examsData) {
        val formats = listOf("dd-MMM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MMM dd, yyyy")
        val standardSdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        examsData.mapNotNull { exam ->
            val dateStr = exam.examDate
            if (dateStr.isNullOrBlank() || dateStr == "-") return@mapNotNull null
            var d: Date? = null
            for (f in formats) {
                try {
                    val sdf = SimpleDateFormat(f, Locale.ENGLISH).apply { isLenient = false }
                    d = sdf.parse(dateStr.trim())
                    if (d != null) break
                } catch(e: Exception) {}
            }
            if (d != null) Pair(standardSdf.format(d), exam) else null
        }.sortedBy { it.first }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60000)
            currentTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 96.dp, bottom = 120.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                NextClassCard(timetable, allReminders, sortedExams, normalizedHolidays, currentTimeStr)
            }


            itemsIndexed(timelineDates) { index, dateCal ->
                val dateStr = sdfDateKey.format(dateCal.time)
                val dayName = sdfDayFull.format(dateCal.time)
                val dayShort = sdfDayShort.format(dateCal.time)
                val isToday = dateStr == todayDateStr
                val isWeekend = dayName == "Saturday" || dayName == "Sunday"

                val examToday = sortedExams.find { it.first == dateStr }?.second
                val holidayToday = normalizedHolidays[dateStr]

                val rawCourses = if (examToday != null || holidayToday != null) emptyList() else timetable.scheduleMap.entries.firstOrNull { it.key.trim().equals(dayName, ignoreCase = true) }?.value ?: emptyList()
                val isExpanded = expandedDateStr == dateStr
                val daysOffset = abs(index - todayIndex)
                val rowAlpha = when (daysOffset) { 0 -> 1f; 1, 2 -> 0.85f; in 3..5 -> 0.6f; in 6..14 -> 0.35f; else -> 0.15f }

                var isPrepDay = false
                var nextExamForPrep: ExamScheduleModel? = null

                val nextExInfo = sortedExams.firstOrNull { it.first > dateStr }
                if (nextExInfo != null) {
                    val nextExDate = sdfDateKey.parse(nextExInfo.first)
                    val currentCalDate = sdfDateKey.parse(dateStr)
                    if (nextExDate != null && currentCalDate != null) {
                        val daysUntil = ((nextExDate.time - currentCalDate.time) / (1000*60*60*24)).toInt()
                        if (daysUntil == 1) {
                            isPrepDay = true
                            nextExamForPrep = nextExInfo.second
                        } else if (daysUntil > 1) {
                            val prevExInfo = sortedExams.lastOrNull { it.first < dateStr }
                            if (prevExInfo != null) {
                                val prevExDate = sdfDateKey.parse(prevExInfo.first)
                                if (prevExDate != null) {
                                    val daysSince = ((currentCalDate.time - prevExDate.time) / (1000*60*60*24)).toInt()
                                    if ((daysUntil + daysSince) <= 6) {
                                        isPrepDay = true
                                        nextExamForPrep = nextExInfo.second
                                    }
                                }
                            }
                        }
                    }
                }

                if (examToday != null) {
                    ExamRow(timetable, dateCal, examToday, isToday, isExpanded, rowAlpha, onExpandToggle = { expandedDateStr = if (isExpanded) "" else dateStr })
                } else if (isPrepDay && nextExamForPrep != null) {
                    NextExamGapRow(dateCal, nextExamForPrep, isToday, rowAlpha)
                } else if (holidayToday != null) {
                    HolidayRow(dateCal, holidayToday, isToday, rowAlpha)
                } else if (rawCourses.isEmpty() && isWeekend && !isExpanded) {
                    WeekendSeparator(dayShort, dateCal.get(Calendar.DAY_OF_MONTH), rowAlpha)
                } else {
                    TimetableRow(
                        dateCal = dateCal,
                        rawCourses = rawCourses,
                        allReminders = allReminders,
                        isToday = isToday,
                        isExpanded = isExpanded,
                        alpha = rowAlpha,
                        currentTimeStr = currentTimeStr,
                        onExpandToggle = { expandedDateStr = if (isExpanded) "" else dateStr },
                        onCourseClick = { selectedCourse = it }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showJumpToToday, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 100.dp)
        ) {
            Button(
                onClick = { coroutineScope.launch { listState.animateScrollToItem(todayIndex + 1) } },
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
fun NextExamGapRow(dateCal: Calendar, nextExam: ExamScheduleModel, isToday: Boolean, alpha: Float) {
    val sdfDayShort = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val themePrimary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.fillMaxWidth(0.94f).alpha(alpha).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(themePrimary.copy(0.1f), Color.Transparent)))
            .border(1.5.dp, themePrimary.copy(0.2f), RoundedCornerShape(14.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 16.dp)) {
                Text(if (isToday) "TODAY" else dateCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = themePrimary)
                Text(sdfDayShort.format(dateCal.time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(themePrimary.copy(0.3f)))
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text("PREPARATION DAY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = themePrimary, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text("Next: ${nextExam.courseTitle.clean()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text("${nextExam.courseCode.clean()} · ${nextExam.examDate.clean()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun NextClassCard(
    timetable: TimetableModel,
    allReminders: List<CourseReminder>,
    sortedExams: List<Pair<String, ExamScheduleModel>>,
    normalizedHolidays: Map<String, String>,
    currentTimeStr: String
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val themeOnSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val sdfDateKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH) }

    val nextEvent: Pair<Any, Pair<String, Color>>? = remember(timetable, sortedExams, normalizedHolidays, currentTimeStr) {
        val mergeLabs = sharedPrefs.getBoolean("MERGE_LABS", true)
        val cal = Calendar.getInstance()
        val todayStrKey = sdfDateKey.format(cal.time)

        val examToday = sortedExams.find { it.first == todayStrKey }?.second
        if (examToday != null) return@remember Pair(examToday, "EXAM TODAY" to themePrimary)

        val holidayToday = normalizedHolidays[todayStrKey]
        if (holidayToday != null) {
            return@remember Pair(holidayToday, "HOLIDAY TODAY" to Color(0xFF10B981))
        }

        val todayStr = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
        val todayCourses = processAndMergeCourses(timetable.scheduleMap.entries.firstOrNull { it.key.trim().equals(todayStr, ignoreCase = true) }?.value ?: emptyList(), mergeLabs)
        val nextToday = todayCourses.firstOrNull { getCourseTimeStatus(it.mergedTimeSlot, true, true) in listOf(TimeStatus.NEXT, TimeStatus.ONGOING) }
        if (nextToday != null) {
            val status = getCourseTimeStatus(nextToday.mergedTimeSlot, true, true)
            val title = if (status == TimeStatus.ONGOING) "NOW" else "NEXT UP"
            return@remember Pair(nextToday, title to themePrimary)
        }

        val searchCal = Calendar.getInstance()
        for (i in 1..7) {
            searchCal.add(Calendar.DAY_OF_YEAR, 1)
            val searchStrKey = sdfDateKey.format(searchCal.time)

            val examFuture = sortedExams.find { it.first == searchStrKey }?.second
            if (examFuture != null) {
                val title = if (i == 1) "TOMORROW: EXAM" else "UPCOMING EXAM"
                return@remember Pair(examFuture, title to themePrimary)
            }

            val nextExInfo = sortedExams.firstOrNull { it.first > searchStrKey }
            if (nextExInfo != null) {
                val nextExDate = sdfDateKey.parse(nextExInfo.first)
                val searchDate = sdfDateKey.parse(searchStrKey)
                if (nextExDate != null && searchDate != null) {
                    val daysUntil = ((nextExDate.time - searchDate.time) / (1000*60*60*24)).toInt()
                    var isPrep = false
                    if (daysUntil == 1) {
                        isPrep = true
                    } else {
                        val prevExInfo = sortedExams.lastOrNull { it.first < searchStrKey }
                        if (prevExInfo != null) {
                            val prevExDate = sdfDateKey.parse(prevExInfo.first)
                            if (prevExDate != null) {
                                val daysSince = ((searchDate.time - prevExDate.time) / (1000*60*60*24)).toInt()
                                if ((daysUntil + daysSince) <= 6) isPrep = true
                            }
                        }
                    }
                    if (isPrep) {
                        if (i == 1) return@remember Pair(nextExInfo.second, "TOMORROW: PREPARATION" to themePrimary.copy(alpha = 0.8f))
                        continue
                    }
                }
            }

            if (normalizedHolidays.containsKey(searchStrKey)) continue

            val futureDayStr = SimpleDateFormat("EEEE", Locale.ENGLISH).format(searchCal.time)
            val futureCourses = processAndMergeCourses(timetable.scheduleMap.entries.firstOrNull { it.key.trim().equals(futureDayStr, ignoreCase = true) }?.value ?: emptyList(), mergeLabs)
            if (futureCourses.isNotEmpty()) return@remember Pair(futureCourses.first(), (if (i == 1) "TOMORROW" else futureDayStr.uppercase()) to themeOnSurfaceVariant)
        }
        null
    }

    if (nextEvent != null) {
        val (event, header) = nextEvent
        val (headerText, headerColor) = header
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()), border = BorderStroke(1.dp, if(headerText.contains("EXAM") || headerText.contains("PREP")) themePrimary.copy(0.3f) else getPremiumBorderColor())
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(headerText, fontSize = 9.sp, color = headerColor, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    if (event is ExamScheduleModel) {
                        if (headerText.contains("PREP")) {
                            Text("PREPARATION DAY", fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                            Text("Next: ${event.courseTitle.clean()}", fontSize = 12.sp, color = themeOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(event.courseCode.clean(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                            Text("${event.examType.clean()} · ${event.reportingTime.clean()}", fontSize = 12.sp, color = themeOnSurfaceVariant)
                        }
                    } else if (event is ProcessedCourse) {
                        Text(event.courseCode.clean(), fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                        Text(event.mergedTimeSlot.clean(), fontSize = 12.sp, color = themeOnSurfaceVariant)
                    } else if (event is String) {
                        Text("🌴 HOLIDAY", fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                        Text(event, fontSize = 12.sp, color = themeOnSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (event is ExamScheduleModel && !headerText.contains("PREP")) {
                        val v = event.venue.clean()
                        val sl = event.seatLocation.clean()
                        val sn = event.seatNumber.clean()

                        if (v != " ") Text(v, fontSize = 14.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                        val seatStr = if (sl == " " && sn == " ") " " else "$sl ($sn)"
                        if (seatStr != " ") Text(seatStr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = themePrimary, modifier = Modifier.padding(top = 2.dp))
                    } else if (event is ProcessedCourse) {
                        val activeReminder = allReminders.find { it.classId == event.classId }
                        Text(event.mergedSlot.clean(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (activeReminder != null) Text(activeReminder.type.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = ColorDanger, modifier = Modifier.padding(top = 4.dp))
                        else Text(event.venue.clean(), fontSize = 12.sp, color = themeOnSurfaceVariant)
                    }
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()),
            border = BorderStroke(1.dp, getPremiumBorderColor())
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("NO UPCOMING EVENTS", fontSize = 9.sp, color = themeOnSurfaceVariant, letterSpacing = 1.5.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text("Schedule Clear", fontSize = 22.sp, fontWeight = FontWeight.Black, color = themeOnSurface)
                    Text("Enjoy your free time or relax.", fontSize = 12.sp, color = themeOnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun HolidayRow(dateCal: Calendar, holidayName: String, isToday: Boolean, alpha: Float) {
    val sdfDayShort = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val emeraldGreen = Color(0xFF10B981)

    Box(
        modifier = Modifier.fillMaxWidth(0.94f).alpha(alpha).clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(emeraldGreen.copy(0.15f), Color.Transparent)))
            .border(1.5.dp, emeraldGreen.copy(0.3f), RoundedCornerShape(14.dp))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 16.dp)) {
                Text(if (isToday) "TODAY" else dateCal.get(Calendar.DAY_OF_MONTH).toString(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = emeraldGreen)
                Text(sdfDayShort.format(dateCal.time), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(modifier = Modifier.width(1.dp).height(30.dp).background(emeraldGreen.copy(0.3f)))
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text("🌴 HOLIDAY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = emeraldGreen, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text(holidayName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun ExamRow(timetable: TimetableModel, dateCal: Calendar, exam: ExamScheduleModel, isToday: Boolean, isExpanded: Boolean, alpha: Float, onExpandToggle: () -> Unit) {
    val sdfDayShort = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }
    val themePrimary = MaterialTheme.colorScheme.primary

    val facultyName = remember(exam.courseCode, timetable) {
        val examBaseCode = exam.courseCode?.substringBefore("-")?.trim() ?: ""
        timetable.scheduleMap.values.flatten()
            .find {
                val ttBaseCode = it.courseCode?.substringBefore("-")?.trim() ?: ""
                ttBaseCode == examBaseCode || it.courseCode?.contains(examBaseCode) == true || examBaseCode.contains(it.courseCode ?: "###")
            }?.faculty?.clean()?.takeIf { it.isNotBlank() } ?: "N/A"
    }

    Box(
        modifier = Modifier.fillMaxWidth(0.94f).alpha(alpha).clip(RoundedCornerShape(14.dp))
            .animateContentSize(animationSpec = tween(300))
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

                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                        if (v != " ") Text(text = v, fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
                        val seatStr = if (sl == " " && sn == " ") " " else "$sl ($sn)"
                        if (seatStr != " ") Text(text = seatStr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themePrimary, textAlign = TextAlign.End, modifier = Modifier.padding(top = 2.dp))
                    }
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
                val displayFaculty = if (facultyName != "N/A") facultyName else "Unknown Faculty"
                ExpandableFacultyRow(facultyName = displayFaculty)
            }
        }
    }
}

@Composable
fun TimetableRow(
    dateCal: Calendar, rawCourses: List<CourseSession>, allReminders: List<CourseReminder>,
    isToday: Boolean, isExpanded: Boolean, alpha: Float, currentTimeStr: String,
    onExpandToggle: () -> Unit, onCourseClick: (ProcessedCourse) -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    val themePrimary = MaterialTheme.colorScheme.primary
    val mergeLabs = remember { sharedPrefs.getBoolean("MERGE_LABS", true) }
    val mergedCourses = remember(rawCourses, mergeLabs) { processAndMergeCourses(rawCourses, mergeLabs) }

    val courseStatuses = remember(mergedCourses, isToday, if (isToday) currentTimeStr else "") {
        var foundNextIndex = -1
        mergedCourses.mapIndexed { index, course ->
            val isNextInLine = foundNextIndex == -1
            val status = getCourseTimeStatus(course.mergedTimeSlot, isToday, isNextInLine)
            if (status == TimeStatus.NEXT || status == TimeStatus.ONGOING) foundNextIndex = index
            status
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(0.94f).alpha(alpha).clip(RoundedCornerShape(14.dp))
            .animateContentSize(animationSpec = tween(300))
            .background(if (isExpanded) Brush.verticalGradient(listOf(if (isToday) getPremiumSurfaceColor() else Color.Transparent, Color.Transparent)) else SolidColor(if (isToday) getPremiumSurfaceColor() else Color.Transparent))
            .border(1.dp, if (isToday || isExpanded) getPremiumBorderColor() else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable { onExpandToggle() }
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
                if (mergedCourses.isEmpty()) {
                    Text("No Classes 🎉", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp))
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        itemsIndexed(mergedCourses) { index, course ->
                            val reminder = allReminders.find { it.classId == course.classId }
                            ClassTile(course = course, status = courseStatuses[index], reminderType = reminder?.type) { onCourseClick(course) }
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
                    Text(if (mergedCourses.isEmpty()) "No Classes" else "${mergedCourses.size} Classes", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
        modifier = Modifier.widthIn(min = 85.dp).wrapContentWidth().heightIn(min = 110.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()),
        border = when (status) { TimeStatus.ONGOING -> BorderStroke(1.5.dp, themePrimary); TimeStatus.NEXT -> BorderStroke(1.dp, themePrimary.copy(alpha = 0.5f)); else -> BorderStroke(1.dp, getPremiumBorderColor()) }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                Text(course.courseCode.clean(), fontSize = 11.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                val pillColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF0A0A0A) else Color(0xFFF5F5F5)
                Box(modifier = Modifier.padding(top = 4.dp).background(pillColor, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text(course.mergedTimeSlot.split("-").firstOrNull()?.trim() ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f).heightIn(min = 8.dp))
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())
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
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
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

            Card(
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                colors = CardDefaults.cardColors(containerColor = getPremiumSurfaceColor()),
                border = BorderStroke(1.dp, getPremiumBorderColor())
            ) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
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

            ExpandableFacultyRow(facultyName = course.faculty.clean())

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
fun ExpandableFacultyRow(facultyName: String) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var cabin by remember { mutableStateOf("Loading...") }
    var email by remember { mutableStateOf("Loading...") }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded && !isLoaded) {
            try {
                val jsonArray = cachedFacultyArray ?: run {
                    val parsed = org.json.JSONArray(com.vtop.utils.OtaManager.getFacultyJson(context))
                    cachedFacultyArray = parsed
                    parsed
                }

                var bestMatchObj: org.json.JSONObject? = null
                var bestDistance = Int.MAX_VALUE

                fun clean(s: String): String = s.replace(Regex("[^a-zA-Z]"), "").lowercase().removePrefix("dr").removePrefix("prof").removePrefix("mr").removePrefix("mrs")
                fun sortClean(s: String): String = s.lowercase().replace("dr.", "").replace("dr ", "").replace("prof.", "").replace("prof ", "").split(Regex("[\\s.]+")).filter { it.isNotBlank() }.sorted().joinToString("") { it.replace(Regex("[^a-z]"), "") }
                fun levenshtein(a: String, b: String): Int {
                    var cost = IntArray(a.length + 1) { it }
                    var newCost = IntArray(a.length + 1) { 0 }
                    for (i in 1..b.length) {
                        newCost[0] = i
                        for (j in 1..a.length) {
                            val match = if (a[j - 1] == b[i - 1]) 0 else 1
                            newCost[j] = minOf(cost[j] + 1, newCost[j - 1] + 1, cost[j - 1] + match)
                        }
                        val swap = cost; cost = newCost; newCost = swap
                    }
                    return cost[a.length]
                }

                val srcClean = clean(facultyName)
                val srcSorted = sortClean(facultyName)

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val targetName = obj.optString("name", "")
                    val tgtClean = clean(targetName)
                    val tgtSorted = sortClean(targetName)

                    if (tgtClean.isEmpty()) continue
                    if (srcClean.contains(tgtClean) || tgtClean.contains(srcClean) || srcSorted.contains(tgtSorted) || tgtSorted.contains(srcSorted) || targetName.contains(facultyName, ignoreCase = true)) {
                        bestMatchObj = obj
                        bestDistance = 0
                        break
                    }

                    val dist1 = levenshtein(srcClean, tgtClean)
                    val dist2 = levenshtein(srcSorted, tgtSorted)
                    val dist = minOf(dist1, dist2)

                    val maxAllowed = maxOf(1, srcClean.length / 3)
                    if (dist <= maxAllowed && dist < bestDistance) {
                        bestDistance = dist
                        bestMatchObj = obj
                    }
                }

                if (bestMatchObj != null) {
                    cabin = bestMatchObj.optString("office", "Not Provided").replace(";", "-")
                    email = bestMatchObj.optString("email", "Not Provided")
                } else {
                    cabin = "Not found"
                    email = "N/A"
                }
                isLoaded = true
            } catch (e: Exception) {
                cabin = "Error loading"
                email = "Error"
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { isExpanded = !isExpanded }.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "FACULTY", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(start = 16.dp), horizontalArrangement = Arrangement.End) {
                Text(text = facultyName, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = if (isExpanded) 2 else 1, overflow = TextOverflow.Ellipsis)
                Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).size(18.dp))
            }
        }
        if (isExpanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Cabin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = cabin, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy, contentDescription = "Copy Cabin", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).clickable {
                            clipboardManager.setText(AnnotatedString(cabin))
                            android.widget.Toast.makeText(context, "Copied: $cabin", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Email", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = email, fontSize = 14.sp, color = if (email.contains("@")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
                        modifier = Modifier.clickable(enabled = email.contains("@")) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$email"))
                            context.startActivity(intent)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy, contentDescription = "Copy Email", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).clickable(enabled = email.contains("@")) {
                            clipboardManager.setText(AnnotatedString(email))
                            android.widget.Toast.makeText(context, "Copied: $email", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WeekendSeparator(day: String, date: Int, alpha: Float) {
    Row(modifier = Modifier.fillMaxWidth().alpha(alpha).padding(vertical = 8.dp, horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(getPremiumBorderColor()))
        Text("$day $date".uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        Box(modifier = Modifier.weight(1f).height(1.dp).background(getPremiumBorderColor()))
    }
}