package com.vtop.widget

import android.content.Context
import com.vtop.models.CourseSession
import com.vtop.ui.CourseReminder
import com.vtop.ui.ReminderManager
import com.vtop.utils.Vault
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WidgetLogic {

    fun getNextClassData(context: Context): Pair<CourseSession, CourseReminder?>? {
        val timetable = Vault.getTimetable(context) ?: return null
        val reminders = ReminderManager.loadReminders(context)

        val cal = Calendar.getInstance()
        val currentDay = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
        val todayClasses = timetable.scheduleMap[currentDay] ?: return null

        val currentTimeStr = SimpleDateFormat("HH:mm", Locale.ENGLISH).format(cal.time)
        val currentHour = currentTimeStr.split(":")[0].toInt()
        val currentMin = currentTimeStr.split(":")[1].toInt()
        val currentTotalMins = (currentHour * 60) + currentMin

        var nextClass: CourseSession? = null
        for (course in todayClasses) {
            val timeParts = course.timeSlot?.split("-") ?: continue
            if (timeParts.isEmpty()) continue

            // Parse start time (e.g., "10:00" or "10:00 AM")
            var startStr = timeParts[0].trim()
            val isStartPm = startStr.contains("PM", ignoreCase = true)
            startStr = startStr.replace("AM", "", true).replace("PM", "", true).trim()

            val startHourRaw = startStr.split(":")[0].toIntOrNull() ?: 0
            val startMin = startStr.split(":")[1].toIntOrNull() ?: 0

            var startHour = startHourRaw
            if (isStartPm && startHour < 12) startHour += 12
            if (!isStartPm && startHour == 12) startHour = 0

            val startTotalMins = (startHour * 60) + startMin

            // Show this class if the current time is BEFORE its start time + 10 mins
            if (currentTotalMins < startTotalMins + 30) {
                nextClass = course
                break
            }
        }

        if (nextClass == null) return null

        // Find the most urgent upcoming reminder that matches this specific class only
        val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)

        val upcomingReminder = reminders.filter {
            it.classId == nextClass.classId && it.date >= todayDateStr
        }.minByOrNull { it.date }

        return Pair(nextClass, upcomingReminder)
    }
}