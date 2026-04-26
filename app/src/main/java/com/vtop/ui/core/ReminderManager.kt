package com.vtop.ui.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CourseReminder(
    val id: String = UUID.randomUUID().toString(),
    val courseCode: String,
    val classId: String,
    val type: String,
    val date: String, // yyyy-MM-dd
    val syllabus: String = ""
)

object ReminderManager {
    fun loadReminders(context: Context): List<CourseReminder> {
        val prefs = context.getSharedPreferences("VTOP_Reminders", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("data", "[]") ?: "[]"
        val list = mutableListOf<CourseReminder>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val dDateStr = obj.getString("date")
                val dDate = sdf.parse(dDateStr) ?: Date()
                val targetCal = Calendar.getInstance().apply { time = dDate }

                if (!targetCal.before(todayCal)) { // Auto-discard passed reminders
                    list.add(CourseReminder(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        courseCode = obj.getString("courseCode"),
                        classId = obj.getString("classId"),
                        type = obj.getString("type"),
                        date = dDateStr,
                        syllabus = obj.optString("syllabus", "")
                    ))
                }
            }
            saveReminders(context, list)
        } catch (_: Exception) { }
        return list
    }

    fun saveReminders(context: Context, reminders: List<CourseReminder>) {
        val array = JSONArray()
        reminders.forEach { r ->
            val obj = JSONObject()
            obj.put("id", r.id); obj.put("courseCode", r.courseCode); obj.put("classId", r.classId);
            obj.put("type", r.type); obj.put("date", r.date); obj.put("syllabus", r.syllabus)
            array.put(obj)
        }
        context.getSharedPreferences("VTOP_Reminders", Context.MODE_PRIVATE).edit().putString("data", array.toString()).apply()
    }

    fun getExportJsonString(context: Context): String {
        val reminders = loadReminders(context)
        val array = JSONArray()
        reminders.forEach { r ->
            array.put(JSONObject().apply { put("id", r.id); put("courseCode", r.courseCode); put("classId", r.classId); put("type", r.type); put("date", r.date); put("syllabus", r.syllabus) })
        }
        return array.toString()
    }

    fun importFromJsonString(context: Context, jsonString: String) {
        JSONArray(jsonString) // Validate format before saving
        context.getSharedPreferences("VTOP_Reminders", Context.MODE_PRIVATE).edit().putString("data", jsonString).apply()
    }
}