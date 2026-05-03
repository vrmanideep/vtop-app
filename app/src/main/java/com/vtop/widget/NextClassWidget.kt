@file:SuppressLint("RestrictedApi")

package com.vtop.widget

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.*
import com.vtop.ui.core.CalendarSync
import com.vtop.ui.core.CourseReminder
import com.vtop.ui.core.ReminderManager
import com.vtop.ui.screens.main.ProcessedCourse
import com.vtop.ui.screens.main.processAndMergeCourses
import com.vtop.models.ExamScheduleModel
import com.vtop.utils.Vault
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Instantly catches the Smart Tick or a time change to redraw the UI
        if (intent.action == "com.vtop.widget.SMART_TICK" ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
            intent.action == Intent.ACTION_BOOT_COMPLETED) {
            GlobalScope.launch {
                NextClassWidget().updateAll(context)
            }
        }
    }
}

private fun Any?.toSafeString(): String = this?.toString() ?: ""

class NextClassWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    private fun isDateMatching(dateStr: String?, targetCal: Calendar): Boolean {
        if (dateStr.isNullOrBlank()) return false
        val formats = listOf("dd-MMM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MMM dd, yyyy")
        for (format in formats) {
            try {
                val d = SimpleDateFormat(format, Locale.ENGLISH).parse(dateStr.trim())
                if (d != null) {
                    val c = Calendar.getInstance().apply { time = d }
                    return c.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun parseTimeRangeToMillis(timeStr: String, isExam: Boolean = false): Pair<Long, Long>? {
        if (timeStr.isBlank() || timeStr == "-") return null
        val parts = timeStr.split("-")
        val startStr = parts[0].trim()
        val endStr = parts.getOrNull(1)?.trim()?.ifEmpty { startStr } ?: startStr

        try {
            val is12HourStart = startStr.contains(Regex("[a-zA-Z]"))
            val sdfStart = SimpleDateFormat(if (is12HourStart) "hh:mm a" else "HH:mm", Locale.ENGLISH)
            val startCal = Calendar.getInstance().apply { time = sdfStart.parse(startStr)!! }
            val startMs = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, startCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, startCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val is12HourEnd = endStr.contains(Regex("[a-zA-Z]"))
            val sdfEnd = SimpleDateFormat(if (is12HourEnd) "hh:mm a" else "HH:mm", Locale.ENGLISH)
            val endCal = Calendar.getInstance().apply { time = sdfEnd.parse(endStr)!! }
            if (!timeStr.contains("-") && isExam) endCal.add(Calendar.HOUR_OF_DAY, 2)

            val endMs = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, endCal.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, endCal.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            return Pair(startMs, endMs)
        } catch (e: Exception) { return null }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleSmartAlarm(context: Context, nextTimeMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, NextClassWidgetReceiver::class.java).apply {
            action = "com.vtop.widget.SMART_TICK"
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExact(android.app.AlarmManager.RTC, nextTimeMillis, pendingIntent)
        } catch (e: Exception) {
            alarmManager.set(android.app.AlarmManager.RTC, nextTimeMillis, pendingIntent)
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            val timetable = Vault.getTimetable(context)
            val exams: List<ExamScheduleModel> = Vault.getExamSchedule(context)
            val reminders = ReminderManager.loadReminders(context)

            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            val eventTimes = mutableListOf<Long>()

            // 1. Add Midnight for the next day's rollover
            val midnightCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }
            eventTimes.add(midnightCal.timeInMillis)

            // 2. HOLIDAYS & BLOCKED DATES
            val bunkCache = CalendarSync.parseBunkCache(context)
            val holidaysMap = mutableMapOf<String, String>()
            try {
                val jsonString = context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
                val jsonObject = org.json.JSONObject(jsonString)
                val semesters = jsonObject.keys()
                while (semesters.hasNext()) {
                    val semObj = jsonObject.getJSONObject(semesters.next())
                    if (semObj.has("holidays")) {
                        val hObj = semObj.getJSONObject("holidays")
                        val hKeys = hObj.keys()
                        while (hKeys.hasNext()) {
                            val dateStr = hKeys.next()
                            holidaysMap[dateStr] = hObj.getString(dateStr)
                        }
                    }
                }
            } catch (_: Exception) {}

            val todayHoliday = run {
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)
                if (holidaysMap.containsKey(dateStr)) {
                    holidaysMap[dateStr]
                } else {
                    val match = bunkCache.blockedDates.entries.find { it.key.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && it.key.get(Calendar.YEAR) == cal.get(Calendar.YEAR) }
                    match?.value
                }
            }

            var semesterEnded = false
            if (bunkCache.lastInstructionalDay.isNotEmpty()) {
                try {
                    val endMs = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(bunkCache.lastInstructionalDay)?.time ?: 0L
                    if (cal.timeInMillis > endMs + 86400000L) semesterEnded = true
                } catch (_: Exception) {}
            }

            val sharedPrefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            val mergeLabs = sharedPrefs.getBoolean("MERGE_LABS", true)

            var finalExam: ExamScheduleModel? = null
            var finalCourse: ProcessedCourse? = null

            // 3. EXAM EVALUATION
            val todayExams = exams.filter { isDateMatching(it.examDate, cal) }
            val validExams = mutableListOf<Pair<ExamScheduleModel, Pair<Long, Long>>>()
            for (exam in todayExams) {
                val timeStr = exam.examTime.toSafeString().trim().ifEmpty { exam.reportingTime.toSafeString().trim() }
                val parsed = parseTimeRangeToMillis(timeStr, isExam = true)
                if (parsed != null) {
                    validExams.add(Pair(exam, parsed))
                    eventTimes.add(parsed.first) // Alarm at Start
                    eventTimes.add(parsed.second) // Alarm at End
                    eventTimes.add(parsed.first - (35 * 60 * 1000L)) // Alarm at T-35
                }
            }

            for ((exam, times) in validExams) {
                if (times.second > now) {
                    finalExam = exam
                    break
                }
            }

            // 4. CLASS EVALUATION (WITH 35 MIN OVERRIDE)
            if (finalExam == null && todayHoliday == null) {
                val todayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
                val todayCourses = processAndMergeCourses(timetable?.scheduleMap?.get(todayStr) ?: emptyList(), mergeLabs)

                val validCourses = mutableListOf<Pair<ProcessedCourse, Pair<Long, Long>>>()
                for (course in todayCourses) {
                    val parsed = parseTimeRangeToMillis(course.mergedTimeSlot)
                    if (parsed != null) {
                        validCourses.add(Pair(course, parsed))
                        eventTimes.add(parsed.first)
                        eventTimes.add(parsed.second)
                        eventTimes.add(parsed.first - (35 * 60 * 1000L))
                    }
                }

                var candidateIndex = -1
                for (i in validCourses.indices) {
                    if (validCourses[i].second.second > now) {
                        candidateIndex = i
                        break
                    }
                }

                if (candidateIndex != -1) {
                    finalCourse = validCourses[candidateIndex].first

                    // THE 35-MIN OVERRIDE
                    if (candidateIndex + 1 < validCourses.size) {
                        val nextCoursePair = validCourses[candidateIndex + 1]
                        val nextStartMs = nextCoursePair.second.first

                        if (nextStartMs - now in 0..(35 * 60 * 1000L)) {
                            finalCourse = nextCoursePair.first
                        }
                    }
                }
            }

            // 5. SCHEDULE THE NEXT WAKEUP
            val nextAlarm = eventTimes.filter { it > now }.minOrNull() ?: midnightCal.timeInMillis
            scheduleSmartAlarm(context, nextAlarm)

            provideContent {
                val launchIntent = Intent().apply {
                    component = ComponentName(context.packageName, "com.vtop.ui.MainActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val widgetBg = ColorProvider(day = Color(0xFFF4F4F5), night = Color(0xFF141414))

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(widgetBg)
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity(launchIntent))
                        .padding(16.dp)
                ) {
                    when {
                        semesterEnded -> EmptyWidgetContent("Semester Completed")
                        finalExam != null -> ExamWidgetContent(context, finalExam)
                        finalCourse != null -> {
                            val activeReminder = reminders.find { it.classId == finalCourse!!.classId }
                            ClassWidgetContent(context, finalCourse!!, activeReminder)
                        }
                        todayHoliday != null && !todayHoliday.lowercase(Locale.getDefault()).contains("exam") -> HolidayWidgetContent(todayHoliday)
                        else -> EmptyWidgetContent("No more classes today")
                    }
                }
            }
        } catch (e: Throwable) {
            provideContent {
                Box(modifier = GlanceModifier.fillMaxSize().background(ColorProvider(day = Color.White, night = Color.White)).padding(8.dp)) {
                    Text("Crash: ${e.javaClass.simpleName}\nMsg: ${e.message.toSafeString()}", style = TextStyle(color = ColorProvider(day = Color.Red, night = Color.Red), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    @Composable
    private fun HolidayWidgetContent(holidayName: String) {
        val textPrimary = ColorProvider(day = Color(0xFF18181B), night = Color(0xFFFFFFFF))
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Holiday", style = TextStyle(color = ColorProvider(day = Color(0xFF10B981), night = Color(0xFF10B981)), fontSize = 16.sp, fontWeight = FontWeight.Bold))
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(holidayName, style = TextStyle(color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }

    @Composable
    private fun ExamWidgetContent(context: Context, exam: ExamScheduleModel) {
        val textPrimary = ColorProvider(day = Color(0xFF18181B), night = Color(0xFFFFFFFF))
        val textSecondary = ColorProvider(day = Color(0xFF71717A), night = Color(0xFFA1A1AA))
        val cardBg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1E1E1E))

        val timeStr = exam.examTime.toSafeString().trim().ifEmpty { exam.reportingTime.toSafeString().trim() }
        val parsedTimes = parseTimeRangeToMillis(timeStr, isExam = true)
        val now = System.currentTimeMillis()

        val isOngoing = parsedTimes != null && now >= parsedTimes.first && now < parsedTimes.second

        val cleanLoc = exam.seatLocation.toSafeString().trim()
        val cleanSeat = exam.seatNumber.toSafeString().trim()
        val seatDisplay = if (cleanLoc.isNotEmpty() && cleanSeat.isNotEmpty()) "$cleanLoc - $cleanSeat" else cleanLoc.ifEmpty { cleanSeat }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(cardBg).cornerRadius(16.dp).padding(12.dp)
            ) {
                Spacer(GlanceModifier.defaultWeight())

                if (isOngoing) {
                    Text("Ongoing Exam", style = TextStyle(color = ColorProvider(day = Color(0xFF4ADE80), night = Color(0xFF4ADE80)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(text = "Ends at ${formatDisplayTime(context, timeStr, getEnd = true)}", style = TextStyle(color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                } else {
                    Text("Upcoming Exam", style = TextStyle(color = ColorProvider(day = Color(0xFFF97316), night = Color(0xFFF97316)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(text = "Starts at ${formatDisplayTime(context, timeStr)}", style = TextStyle(color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Text(text = exam.courseCode.toSafeString(), style = TextStyle(color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(text = exam.courseTitle.toSafeString(), style = TextStyle(color = textSecondary, fontSize = 12.sp), maxLines = 1)

                Spacer(modifier = GlanceModifier.defaultWeight())

                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(text = "Venue: ${exam.venue.toSafeString()}", style = TextStyle(color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    if (seatDisplay.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(text = "Seat: $seatDisplay", style = TextStyle(color = ColorProvider(day = Color(0xFFF97316), night = Color(0xFFF97316)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                }

                Spacer(modifier = GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun ClassWidgetContent(context: Context, course: ProcessedCourse, reminder: CourseReminder?) {
        val textPrimary = ColorProvider(day = Color(0xFF18181B), night = Color(0xFFFFFFFF))
        val textSecondary = ColorProvider(day = Color(0xFF71717A), night = Color(0xFFA1A1AA))
        val cardBg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1E1E1E))

        val remBg = when {
            reminder == null -> ColorProvider(day = Color.Transparent, night = Color.Transparent)
            reminder.type.lowercase(Locale.getDefault()).contains("quiz") -> ColorProvider(day = Color(0xFFDBEAFE), night = Color(0xFF1E3A8A))
            reminder.type.lowercase(Locale.getDefault()).contains("viva") -> ColorProvider(day = Color(0xFFF3E8FF), night = Color(0xFF4C1D95))
            else -> ColorProvider(day = Color(0xFFFEF3C7), night = Color(0xFF78350F))
        }

        val remText = when {
            reminder == null -> ColorProvider(day = Color.Transparent, night = Color.Transparent)
            reminder.type.lowercase(Locale.getDefault()).contains("quiz") -> ColorProvider(day = Color(0xFF1D4ED8), night = Color(0xFF60A5FA))
            reminder.type.lowercase(Locale.getDefault()).contains("viva") -> ColorProvider(day = Color(0xFF6D28D9), night = Color(0xFFA78BFA))
            else -> ColorProvider(day = Color(0xFFB45309), night = Color(0xFFFCD34D))
        }

        val sdfFullDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val todayDateStr = sdfFullDate.format(Calendar.getInstance().time)
        val displayDate = if (reminder != null && reminder.date != todayDateStr) {
            try {
                val parsed = sdfFullDate.parse(reminder.date)
                if (parsed != null) SimpleDateFormat("d/M", Locale.ENGLISH).format(parsed) else reminder.date
            } catch(_: Exception) { reminder.date }
        } else "today"

        val parsedTimes = parseTimeRangeToMillis(course.mergedTimeSlot)
        val now = System.currentTimeMillis()
        val isOngoing = parsedTimes != null && now >= parsedTimes.first && now < parsedTimes.second

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(cardBg).cornerRadius(16.dp).padding(12.dp)
            ) {
                Spacer(GlanceModifier.defaultWeight())

                if (isOngoing) {
                    Text("Ongoing Class", style = TextStyle(color = ColorProvider(day = Color(0xFF4ADE80), night = Color(0xFF4ADE80)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(text = "Ends at ${formatDisplayTime(context, course.mergedTimeSlot, getEnd = true)}", style = TextStyle(color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                } else {
                    Text("Upcoming Class", style = TextStyle(color = ColorProvider(day = Color(0xFFF87171), night = Color(0xFFF87171)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(text = "Starts at ${formatDisplayTime(context, course.mergedTimeSlot)}", style = TextStyle(color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Text(text = course.courseCode.toSafeString(), style = TextStyle(color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(text = course.courseName.toSafeString(), style = TextStyle(color = textSecondary, fontSize = 12.sp), maxLines = 1)

                Spacer(modifier = GlanceModifier.defaultWeight())

                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(text = "Venue: ${course.venue.toSafeString()}", style = TextStyle(color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(text = "Slot: ${course.mergedSlot.toSafeString()}", style = TextStyle(color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }

                if (reminder != null) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                    Text(
                        text = " • ${reminder.type} " + (if (displayDate == "today") "today" else "on $displayDate") + " ",
                        style = TextStyle(color = remText, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.background(remBg).cornerRadius(4.dp).padding(4.dp)
                    )
                }

                Spacer(GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun EmptyWidgetContent(message: String) {
        val textPrimary = ColorProvider(day = Color(0xFF18181B), night = Color(0xFFFFFFFF))
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(message, style = TextStyle(color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }

    private fun formatDisplayTime(context: Context, timeSlot: String?, getEnd: Boolean = false): String {
        val safeTimeSlot = timeSlot.toSafeString()
        if (safeTimeSlot.isEmpty()) return ""
        val parts = safeTimeSlot.split("-")
        val targetStr = if (getEnd) (parts.getOrNull(1)?.trim()?.ifEmpty { parts[0].trim() } ?: parts[0].trim()) else parts[0].trim()

        return try {
            val inputFormat = SimpleDateFormat(if (targetStr.contains(Regex("[a-zA-Z]"))) "hh:mm a" else "HH:mm", Locale.ENGLISH)
            val date = inputFormat.parse(targetStr) ?: return targetStr
            val is24Hour = DateFormat.is24HourFormat(context)
            val cal = Calendar.getInstance().apply { time = date }

            if (is24Hour) {
                val h24 = cal.get(Calendar.HOUR_OF_DAY)
                val m = cal.get(Calendar.MINUTE)
                if (m == 0) "$h24:00" else String.format(Locale.getDefault(), "%d:%02d", h24, m)
            } else {
                val h12 = cal.get(Calendar.HOUR)
                val m = cal.get(Calendar.MINUTE)
                val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                val displayHour = if (h12 == 0) 12 else h12
                if (m == 0) "${displayHour}${amPm}" else String.format(Locale.getDefault(), "%d:%02d%s", displayHour, m, amPm)
            }
        } catch (_: Exception) { targetStr }
    }
}