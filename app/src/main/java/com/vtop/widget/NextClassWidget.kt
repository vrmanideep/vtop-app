package com.vtop.widget

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
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.vtop.models.CourseSession
import com.vtop.ui.core.CourseReminder
import com.vtop.ui.core.ReminderManager
import com.vtop.utils.Vault
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.vtop.R

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

class NextClassWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        try {
            val timetable = Vault.getTimetable(context)
            val reminders = ReminderManager.loadReminders(context)

            val cal = Calendar.getInstance()
            val todayStr = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
            val todayCourses = timetable?.scheduleMap?.get(todayStr) ?: emptyList()

            var targetCourse: CourseSession? = null
            val currentTimeMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            val sdfTime = SimpleDateFormat("HH:mm", Locale.ENGLISH)

            for (i in todayCourses.indices) {
                val course = todayCourses[i]
                try {
                    val endStr = course.timeSlot?.split("-")?.get(1)?.trim() ?: ""
                    val endCal = Calendar.getInstance().apply { time = sdfTime.parse(endStr)!! }
                    val endMin = endCal.get(Calendar.HOUR_OF_DAY) * 60 + endCal.get(Calendar.MINUTE)

                    if (endMin > currentTimeMin) {
                        targetCourse = course
                        break
                    }
                } catch (_: Exception) {}
            }

            provideContent {
                val launchIntent = Intent().apply {
                    component = ComponentName(context.packageName, "com.vtop.ui.MainActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                val widgetBg = ColorProvider(R.color.widget_bg)

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(widgetBg)
                        .cornerRadius(24.dp)
                        .clickable(actionStartActivity(launchIntent))
                        .padding(16.dp)
                )

                {
                    if (targetCourse != null) {
                        val activeReminder = reminders.find { it.classId == targetCourse.classId }
                        WidgetContent(context, targetCourse, activeReminder)
                    } else {
                        EmptyWidgetContent()
                    }
                }
            }
        } catch (e: Throwable) {
            // THE DEBUG LIFESAVER: This catches the silent crash and prints it on your screen!
            provideContent {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color.White))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Crash: ${e.javaClass.simpleName}\nMsg: ${e.message}",
                        style = TextStyle(color = ColorProvider(Color.Red), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        course: CourseSession,
        reminder: CourseReminder?
    ) {
        val textPrimary = ColorProvider(R.color.widget_text_primary)
        val textSecondary = ColorProvider(R.color.widget_text_secondary)
        val cardBg = ColorProvider(R.color.widget_card_bg)

        val remBg = ColorProvider(
            when {
                reminder == null -> android.R.color.transparent
                reminder.type.lowercase(Locale.getDefault()).contains("quiz") -> R.color.widget_rem_quiz_bg
                reminder.type.lowercase(Locale.getDefault()).contains("viva") -> R.color.widget_rem_viva_bg
                else -> R.color.widget_rem_def_bg
            }
        )

        val remText = ColorProvider(
            when {
                reminder == null -> android.R.color.transparent
                reminder.type.lowercase(Locale.getDefault()).contains("quiz") -> R.color.widget_rem_quiz_text
                reminder.type.lowercase(Locale.getDefault()).contains("viva") -> R.color.widget_rem_viva_text
                else -> R.color.widget_rem_def_text
            }
        )

        val sdfFullDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val todayDateStr = sdfFullDate.format(Calendar.getInstance().time)
        val displayDate = if (reminder != null && reminder.date != todayDateStr) {
            try {
                val parsed = sdfFullDate.parse(reminder.date)
                SimpleDateFormat("d/M", Locale.ENGLISH).format(parsed!!)
            } catch(_: Exception) { reminder.date }
        } else "today"

        val headerText = if (reminder != null && reminder.date != displayDate) {
            "${reminder.type.uppercase(Locale.getDefault())} ON ${displayDate.uppercase(Locale.getDefault())}"
        } else {
            "UPCOMING CLASS"
        }

        val startTimeStr = formatDisplayTime(context, course.timeSlot)
        val diffMinutes = getMinutesUntilClass(course.timeSlot)

        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(text = headerText, style = TextStyle(color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            Spacer(modifier = GlanceModifier.height(10.dp))

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(cardBg)
                    .cornerRadius(16.dp)
                    .padding(12.dp)
            ) {
                if (diffMinutes in 0..35) {
                    Text("Class starts in", style = TextStyle(color = ColorProvider(Color(0xFFF87171)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(text = if (diffMinutes == 0L) "Now" else "$diffMinutes mins", style = TextStyle(color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                } else if (diffMinutes < 0) {
                    Text("Ongoing class", style = TextStyle(color = ColorProvider(Color(0xFF4ADE80)), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(text = "Started", style = TextStyle(color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold))
                } else {
                    Text("Upcoming class at", style = TextStyle(color = textSecondary, fontSize = 12.sp))
                    Text(text = startTimeStr, style = TextStyle(color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = GlanceModifier.height(8.dp))
                Text(course.courseCode ?: "", style = TextStyle(color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                Text(course.courseName ?: "", style = TextStyle(color = textSecondary, fontSize = 12.sp))
                Spacer(modifier = GlanceModifier.height(10.dp))

                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(course.venue ?: "", style = TextStyle(color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(course.slot ?: "", style = TextStyle(color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }

                if (reminder != null) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Text(
                        text = " • ${reminder.type} " + (if (displayDate == "today") "today" else "on $displayDate") + " ",
                        style = TextStyle(color = remText, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.background(remBg).cornerRadius(4.dp).padding(4.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyWidgetContent() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No more classes today", style = TextStyle(color = ColorProvider(R.color.widget_text_primary), fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }

    private fun formatDisplayTime(context: Context, timeSlot: String?): String {
        if (timeSlot.isNullOrEmpty()) return ""
        val startStr = timeSlot.split("-")[0].trim()
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

    private fun getMinutesUntilClass(timeSlot: String?): Long {
        if (timeSlot.isNullOrEmpty()) return 999L
        val startStr = timeSlot.split("-")[0].trim()
        try {
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
            return TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        } catch(_: Exception) { return 999L }
    }
}