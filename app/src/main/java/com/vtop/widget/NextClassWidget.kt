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
import java.util.concurrent.TimeUnit

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Force an instant widget redraw the second the system clock changes
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            GlobalScope.launch {
                NextClassWidget().updateAll(context)
            }
        }
    }
}

// Helper to completely bypass Kotlin's strict null-safety compiler warnings
private fun Any?.toSafeString(): String = this?.toString() ?: ""

class NextClassWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            val timetable = Vault.getTimetable(context)

            val exams: List<ExamScheduleModel> = Vault.getExamSchedule(context)
            val reminders = ReminderManager.loadReminders(context)

            val cal = Calendar.getInstance()
            val currentTimeMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            // 1. HOLIDAY & SEMESTER END CHECKS
            val bunkCache = CalendarSync.parseBunkCache(context)
            var blockReason: String? = null
            bunkCache.blockedDates.forEach { (blockedCal, reason) ->
                if (blockedCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    blockedCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)) {
                    blockReason = reason
                }
            }

            var semesterEnded = false
            if (bunkCache.lastInstructionalDay.isNotEmpty()) {
                try {
                    val endMs = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(bunkCache.lastInstructionalDay)?.time ?: 0L
                    if (cal.timeInMillis > endMs + 86400000L) { // Add 1 day padding
                        semesterEnded = true
                    }
                } catch (_: Exception) {}
            }

            // 2. EXAM DAY LOGIC (Overrides standard timetable)
            val dateFormats = listOf("dd-MMM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "MMM dd, yyyy")
            val todayExams = exams.filter { exam ->
                val dateStr = exam.examDate.toSafeString().trim()
                var parsedDate: Date? = null
                for (format in dateFormats) {
                    try {
                        parsedDate = SimpleDateFormat(format, Locale.ENGLISH).parse(dateStr)
                        if (parsedDate != null) break
                    } catch (_: Exception) {}
                }
                if (parsedDate != null) {
                    val examCal = Calendar.getInstance().apply { time = parsedDate }
                    examCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                            examCal.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
                } else false
            }

            var targetExam: ExamScheduleModel? = null
            if (todayExams.isNotEmpty()) {
                for (exam in todayExams) {
                    val timeStr = exam.examTime.toSafeString().trim().ifEmpty { exam.reportingTime.toSafeString().trim() }
                    if (timeStr == "-") continue

                    val parts = timeStr.split("-")
                    val endStr = parts.getOrNull(1)?.trim()?.ifEmpty { parts.getOrNull(0)?.trim() } ?: parts.getOrNull(0)?.trim() ?: ""

                    if (endStr.isNotEmpty()) {
                        try {
                            val is12Hour = endStr.contains(Regex("[a-zA-Z]"))
                            val sdfTime = SimpleDateFormat(if (is12Hour) "hh:mm a" else "HH:mm", Locale.ENGLISH)
                            val parsedTime = sdfTime.parse(endStr)

                            if (parsedTime != null) {
                                val endCal = Calendar.getInstance().apply { time = parsedTime }
                                if (!timeStr.contains("-")) endCal.add(Calendar.HOUR_OF_DAY, 2)
                                val endMin = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE)

                                if (endMin > currentTimeMin) {
                                    targetExam = exam
                                    break
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // 3. REGULAR CLASS LOGIC
            val todayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
            val rawCourses = timetable?.scheduleMap?.get(todayStr) ?: emptyList()

            val sharedPrefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            val mergeLabs = sharedPrefs.getBoolean("MERGE_LABS", true)
            val todayCourses = processAndMergeCourses(rawCourses, mergeLabs)

            var targetCourse: ProcessedCourse? = null
            if (todayExams.isEmpty()) {
                for (i in todayCourses.indices) {
                    val course = todayCourses[i]
                    try {
                        val endStr = course.mergedTimeSlot.toSafeString().split("-").getOrNull(1)?.trim() ?: ""
                        if (endStr.isNotEmpty()) {
                            val is12Hour = endStr.contains(Regex("[a-zA-Z]"))
                            val sdfTime = SimpleDateFormat(if (is12Hour) "hh:mm a" else "HH:mm", Locale.ENGLISH)
                            val parsedTime = sdfTime.parse(endStr)

                            if (parsedTime != null) {
                                val endCal = Calendar.getInstance().apply { time = parsedTime }
                                val endMin = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE)

                                if (endMin > currentTimeMin) {
                                    targetCourse = course
                                    break
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Freeze variables for Kotlin Smart Casting
            val finalExam = targetExam
            val finalCourse = targetCourse

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
                        todayExams.isNotEmpty() && finalExam != null -> ExamWidgetContent(context, finalExam)
                        todayExams.isNotEmpty() && finalExam == null -> EmptyWidgetContent("All exams completed today")
                        blockReason != null && !blockReason.lowercase(Locale.getDefault()).contains("exam") -> EmptyWidgetContent(blockReason)
                        finalCourse != null -> {
                            val activeReminder = reminders.find { it.classId == finalCourse.classId }
                            ClassWidgetContent(context, finalCourse, activeReminder)
                        }
                        else -> EmptyWidgetContent("No more classes today")
                    }
                }
            }
        } catch (e: Throwable) {
            provideContent {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(day = Color.White, night = Color.White))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Crash: ${e.javaClass.simpleName}\nMsg: ${e.message.toSafeString()}",
                        style = TextStyle(color = ColorProvider(day = Color.Red, night = Color.Red), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    @Composable
    private fun ExamWidgetContent(context: Context, exam: ExamScheduleModel) {
        val textPrimary = ColorProvider(day = Color(0xFF18181B), night = Color(0xFFFFFFFF))
        val textSecondary = ColorProvider(day = Color(0xFF71717A), night = Color(0xFFA1A1AA))
        val cardBg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1E1E1E))

        val timeStr = exam.examTime.toSafeString().trim().ifEmpty { exam.reportingTime.toSafeString().trim() }
        val startTimeStr = formatDisplayTime(context, timeStr)
        val diffMinutes = getMinutesUntilEvent(timeStr)

        val cleanLoc = exam.seatLocation.toSafeString().trim()
        val cleanSeat = exam.seatNumber.toSafeString().trim()
        val seatDisplay = if (cleanLoc.isNotEmpty() && cleanSeat.isNotEmpty()) "$cleanLoc - $cleanSeat" else cleanLoc.ifEmpty { cleanSeat }

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize() // Automatically stretches to fill 4x4 or 8x8 sizes
                    .background(cardBg)
                    .cornerRadius(16.dp)
                    .padding(16.dp)
            ) {
                Spacer(GlanceModifier.defaultWeight()) // Elastic spacing

                if (diffMinutes in 0..60) {
                    Text("Exam starts in", style = TextStyle(color = ColorProvider(day = Color(0xFFF97316), night = Color(0xFFF97316)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text(text = if (diffMinutes == 0L) "Now" else "$diffMinutes mins", style = TextStyle(color = textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                } else if (diffMinutes < 0) {
                    Text("Ongoing Exam", style = TextStyle(color = ColorProvider(day = Color(0xFF4ADE80), night = Color(0xFF4ADE80)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text(text = "In Progress", style = TextStyle(color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                } else {
                    Text("Starts at", style = TextStyle(color = textSecondary, fontSize = 14.sp))
                    Text(text = startTimeStr, style = TextStyle(color = textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Text(text = exam.courseCode.toSafeString(), style = TextStyle(color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text(text = exam.courseTitle.toSafeString(), style = TextStyle(color = textSecondary, fontSize = 14.sp))

                Spacer(modifier = GlanceModifier.defaultWeight())

                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Venue: ${exam.venue.toSafeString()}", style = TextStyle(color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    if (seatDisplay.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.width(12.dp))
                        Text(text = "Seat: $seatDisplay", style = TextStyle(color = ColorProvider(day = Color(0xFFF97316), night = Color(0xFFF97316)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
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

        val startTimeStr = formatDisplayTime(context, course.mergedTimeSlot)
        val diffMinutes = getMinutesUntilEvent(course.mergedTimeSlot)

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize() // Automatically stretches
                    .background(cardBg)
                    .cornerRadius(16.dp)
                    .padding(16.dp)
            ) {
                Spacer(GlanceModifier.defaultWeight())

                if (diffMinutes in 0..35) {
                    Text("Class starts in", style = TextStyle(color = ColorProvider(day = Color(0xFFF87171), night = Color(0xFFF87171)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text(text = if (diffMinutes == 0L) "Now" else "$diffMinutes mins", style = TextStyle(color = textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                } else if (diffMinutes < 0) {
                    Text("Ongoing class", style = TextStyle(color = ColorProvider(day = Color(0xFF4ADE80), night = Color(0xFF4ADE80)), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text(text = "Started", style = TextStyle(color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                } else {
                    Text("Upcoming class at", style = TextStyle(color = textSecondary, fontSize = 14.sp))
                    Text(text = startTimeStr, style = TextStyle(color = textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = GlanceModifier.defaultWeight())

                Text(text = course.courseCode.toSafeString(), style = TextStyle(color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Text(text = course.courseName.toSafeString(), style = TextStyle(color = textSecondary, fontSize = 14.sp))

                Spacer(modifier = GlanceModifier.defaultWeight())

                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = course.venue.toSafeString(), style = TextStyle(color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(text = course.mergedSlot.toSafeString(), style = TextStyle(color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }

                if (reminder != null) {
                    Spacer(modifier = GlanceModifier.height(12.dp))
                    Text(
                        text = " • ${reminder.type} " + (if (displayDate == "today") "today" else "on $displayDate") + " ",
                        style = TextStyle(color = remText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
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

    private fun formatDisplayTime(context: Context, timeSlot: String?): String {
        val safeTimeSlot = timeSlot.toSafeString()
        if (safeTimeSlot.isEmpty()) return ""
        val startStr = safeTimeSlot.split("-").firstOrNull()?.trim() ?: return ""
        return try {
            val inputFormat = SimpleDateFormat(if (startStr.contains(Regex("[a-zA-Z]"))) "hh:mm a" else "HH:mm", Locale.ENGLISH)
            val date = inputFormat.parse(startStr) ?: return startStr
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
        } catch (_: Exception) { startStr }
    }

    private fun getMinutesUntilEvent(timeSlot: String?): Long {
        val safeTimeSlot = timeSlot.toSafeString()
        if (safeTimeSlot.isEmpty()) return 999L
        val startStr = safeTimeSlot.split("-").firstOrNull()?.trim() ?: return 999L
        return try {
            val sdf = SimpleDateFormat(if (startStr.contains(Regex("[a-zA-Z]"))) "hh:mm a" else "HH:mm", Locale.ENGLISH)
            val startTime = sdf.parse(startStr) ?: return 999L
            val now = Calendar.getInstance()
            val startCal = Calendar.getInstance().apply {
                time = startTime
                set(Calendar.YEAR, now.get(Calendar.YEAR))
                set(Calendar.MONTH, now.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH))
            }
            val diffMillis = startCal.timeInMillis - now.timeInMillis
            TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        } catch(_: Exception) { 999L }
    }
}