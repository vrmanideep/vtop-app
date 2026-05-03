package com.vtop.utils

import android.content.Context
import android.util.Log
import com.vtop.models.ExamScheduleModel
import com.vtop.ui.core.AppBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

object SemesterTransitionEngine {

    private const val TAG = "SEMESTER_ENGINE"

    /**
     * Parses the saved exams to find the absolute chronologically latest FAT.
     * Returns true if the current system time is 3 hours past the start of the final FAT.
     */
    fun checkIfLastFatIsOver(exams: List<ExamScheduleModel>): Boolean {
        val fatExams = exams.filter { it.examType.contains("FAT", ignoreCase = true) }

        if (fatExams.isEmpty()) return false

        val sdf = SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH)
        var latestFatStartMillis = 0L

        for (exam in fatExams) {
            try {
                val dateTimeString = "${exam.examDate.trim()} ${exam.examTime.trim()}"
                val parsedDate = sdf.parse(dateTimeString)

                if (parsedDate != null && parsedDate.time > latestFatStartMillis) {
                    latestFatStartMillis = parsedDate.time
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse FAT date/time for ${exam.courseCode}")
            }
        }

        if (latestFatStartMillis == 0L) return false

        val fatEndTime = latestFatStartMillis + (3 * 60 * 60 * 1000)
        return System.currentTimeMillis() > fatEndTime
    }

    /**
     * Reads the JSON to find the next chronological semester IDs based on commencement_date.
     * Prevents switching until exactly 24 hours before that date.
     * Resolves ties by checking which of the simultaneous semesters the user is actually enrolled in.
     */
    suspend fun attemptAutoSwitch(context: Context): Boolean {
        try {
            val jsonString = context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            var earliestFutureStart = Long.MAX_VALUE
            val nextSemIds = mutableListOf<String>()
            val now = System.currentTimeMillis()

            // Exactly 24 hours in milliseconds
            val oneDayMillis = 24L * 60 * 60 * 1000

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val keys = root.keys()

            // 1. Find the absolute next commencement date(s)
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.getJSONObject(key)

                if (obj.has("commencement_date") && obj.has("id")) {
                    val dateStr = obj.getString("commencement_date")
                    val startMillis = sdf.parse(dateStr)?.time ?: 0L

                    if (startMillis > now) {
                        if (startMillis < earliestFutureStart) {
                            earliestFutureStart = startMillis
                            nextSemIds.clear()
                            nextSemIds.add(obj.getString("id"))
                        } else if (startMillis == earliestFutureStart) {
                            // Catch ties (e.g., LSUM and SSUM1 starting on the exact same day)
                            nextSemIds.add(obj.getString("id"))
                        }
                    }
                }
            }

            // Failsafe: Reached end of calendar data
            if (nextSemIds.isEmpty()) return false

            // 2. The "Waiting Room" Check: Are we within 24 hours of commencement?
            val windowStartMillis = earliestFutureStart - oneDayMillis
            if (now < windowStartMillis) {
                Log.d(TAG, "Next semester(s) ${nextSemIds.joinToString()} found, but waiting until 24 hours before commencement.")
                return false
            }

            // 3. Cross-reference all tied candidates with the user's actual portal options
            val availableOptions = Vault.getSemesterOptions(context)
            var matchedOption: com.vtop.models.SemesterOption? = null

            // The Tie-Breaker: Check which of the overlapping semesters the user is actually enrolled in
            for (semId in nextSemIds) {
                matchedOption = availableOptions.find {
                    it.name.equals(semId, ignoreCase = true) || it.name.contains(semId, ignoreCase = true)
                }
                if (matchedOption != null) {
                    break // We found the exact one!
                }
            }

            if (matchedOption != null) {
                // EXECUTING THE SWITCH
                Log.d(TAG, "Match found! Auto-switching to ${matchedOption.name}")

                Vault.saveSelectedSemester(context, matchedOption.id, matchedOption.name)

                Vault.saveAttendance(context, emptyList())
                Vault.saveMarks(context, emptyList())
                Vault.saveExamSchedule(context, emptyList())

                withContext(Dispatchers.Main) {
                    AppBridge.isSemesterCompleted.value = false
                }

                NotificationHelper.showNotification(
                    context = context,
                    title = "Semester Updated",
                    message = "Welcome to ${matchedOption.name}. We are syncing your new timetable in the background.",
                    notificationId = 501
                )

                return true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Auto-switch failed: ${e.message}")
        }

        return false
    }
}