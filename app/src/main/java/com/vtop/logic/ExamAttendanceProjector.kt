package com.vtop.logic

import android.annotation.SuppressLint
import com.vtop.models.AcademicCalendarEvent
import com.vtop.models.AttendanceModel
import com.vtop.models.TimetableModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import android.util.Log

data class ExamTarget(
    val name: String,
    val startDate: LocalDate,
    val cutoffDate: LocalDate
)

data class ExamAttendanceProjection(
    val courseCode: String,
    val courseType: String,
    val examName: String,
    val examStartDate: LocalDate,
    val cutoffDate: LocalDate,
    val currentAttended: Int,
    val currentTotal: Int,
    val currentPct: Float,
    val classesUntilCutoff: Int,
    val projectedAttended: Int,
    val projectedTotal: Int,
    val projectedPct: Float,
    val maxBunksAllowed: Int,
    val noData: Boolean = false
)

object ExamAttendanceProjector {

    @SuppressLint("NewApi")
    fun getAllExams(
        calendarEvents: List<AcademicCalendarEvent>
    ): List<ExamTarget> {
        val exams = calendarEvents.mapNotNull { event ->
            val date = parseCalendarDate(event.date)
            val name = normalizeExamName(event.particulars)
            if (date == null || name == null) return@mapNotNull null
            name to date
        }

        return exams
            .groupBy { it.first }
            .mapNotNull { (name, entries) ->
                val start = entries.minOfOrNull { it.second } ?: return@mapNotNull null
                val cutoff = findCutoffDate(calendarEvents, start) ?: start.minusDays(3)
                ExamTarget(
                    name = name,
                    startDate = start,
                    cutoffDate = cutoff
                )
            }
            .sortedBy { it.startDate }
    }

    @SuppressLint("NewApi")
    fun findNextExam(
        calendarEvents: List<AcademicCalendarEvent>,
        today: LocalDate = LocalDate.now()
    ): ExamTarget? {
        val allExams = getAllExams(calendarEvents)
        return allExams.firstOrNull { !it.startDate.isBefore(today) }
    }

    @SuppressLint("NewApi")
    fun findCutoffDate(
        calendarEvents: List<AcademicCalendarEvent>,
        examStartDate: LocalDate
    ): LocalDate? {
        return examStartDate.minusDays(3)
    }

    @SuppressLint("NewApi")
    fun project(
        calendarEvents: List<AcademicCalendarEvent>,
        timetable: TimetableModel,
        attendanceData: List<AttendanceModel>,
        blockedDates: Map<String, String>,
        today: LocalDate = LocalDate.now()
    ): List<ExamAttendanceProjection> {
        val target = findNextExam(calendarEvents, today) ?: return emptyList()

        return projectToTarget(
            target = target,
            timetable = timetable,
            attendanceData = attendanceData,
            blockedDates = blockedDates,
            today = today
        )
    }

    @SuppressLint("NewApi")
    private fun projectToTarget(
        target: ExamTarget,
        timetable: TimetableModel,
        attendanceData: List<AttendanceModel>,
        blockedDates: Map<String, String>,
        today: LocalDate
    ): List<ExamAttendanceProjection> {
        val blocked = buildBlockedDateSet(blockedDates)

        return attendanceData.mapNotNull { att ->
            val courseCode = att.courseCode ?: return@mapNotNull null
            val courseType = att.courseType ?: ""

            val currentAttended = numericValue(att.attendedClasses)
            val currentTotal = numericValue(att.totalClasses)

            val currentPct = if (currentTotal > 0) {
                currentAttended.toFloat() / currentTotal.toFloat() * 100f
            } else {
                0f
            }

            val lastAttendanceDate = findLatestHistoryDate(att)

            if (lastAttendanceDate == null) {
                return@mapNotNull ExamAttendanceProjection(
                    courseCode = courseCode,
                    courseType = courseType,
                    examName = target.name,
                    examStartDate = target.startDate,
                    cutoffDate = target.cutoffDate,
                    currentAttended = currentAttended,
                    currentTotal = currentTotal,
                    currentPct = currentPct,
                    classesUntilCutoff = 0,
                    projectedAttended = currentAttended,
                    projectedTotal = currentTotal,
                    projectedPct = currentPct,
                    maxBunksAllowed = 0,
                    noData = true
                )
            }

            var startDate = lastAttendanceDate.plusDays(1)
            if (startDate.isBefore(today)) {
                startDate = today
            }

            var upcomingClasses = 0

            if (!startDate.isAfter(target.cutoffDate)) {
                var date = startDate

                while (!date.isAfter(target.cutoffDate)) {
                    if (!isBlocked(date, blocked)) {
                        upcomingClasses += countCourseClassesOnDate(
                            date = date,
                            timetable = timetable,
                            attendance = att
                        )
                    }

                    date = date.plusDays(1)
                }
            }

            val projectedTotal = currentTotal + upcomingClasses
            val projectedAttended = currentAttended + upcomingClasses
            val projectedPct = if (projectedTotal > 0) {
                projectedAttended.toFloat() / projectedTotal.toFloat() * 100f
            } else {
                0f
            }

            val maxBunksByPercentage = floor(
                projectedAttended - (0.75 * projectedTotal)
            ).toInt().coerceAtLeast(0)

            val maxBunksAllowed = maxBunksByPercentage.coerceAtMost(upcomingClasses)

            ExamAttendanceProjection(
                courseCode = courseCode,
                courseType = courseType,
                examName = target.name,
                examStartDate = target.startDate,
                cutoffDate = target.cutoffDate,
                currentAttended = currentAttended,
                currentTotal = currentTotal,
                currentPct = currentPct,
                classesUntilCutoff = upcomingClasses,
                projectedAttended = projectedAttended,
                projectedTotal = projectedTotal,
                projectedPct = projectedPct,
                maxBunksAllowed = maxBunksAllowed,
                noData = false
            )
        }
    }

    @SuppressLint("NewApi")
    private fun countCourseClassesOnDate(
        date: LocalDate,
        timetable: TimetableModel,
        attendance: AttendanceModel
    ): Int {
        val attendanceCode = normalizeCourseCode(attendance.courseCode ?: return 0)
        val attendanceType = attendance.courseType ?: ""
        val attendanceIsLab = isLab(attendanceType)
        val dayKey = date.dayOfWeek.name.take(3).uppercase(Locale.ENGLISH)

        val classes = timetable.scheduleMap.entries
            .firstOrNull {
                it.key.uppercase(Locale.ENGLISH).startsWith(dayKey)
            }
            ?.value
            ?: emptyList()

        var count = 0
        for (cls in classes) {
            val timetableCode = normalizeCourseCode(cls.courseCode)
            val timetableIsLab = isLab(cls.courseType)

            val sameCourse = timetableCode.contains(attendanceCode) || attendanceCode.contains(timetableCode)

            if (sameCourse && attendanceIsLab == timetableIsLab) {
                count += getSlotWeight(cls.slot)
            }
        }
        return count
    }

    private fun getSlotWeight(slot: String?): Int {
        if (slot.isNullOrBlank() || slot == "-" || slot.equals("N/A", ignoreCase = true)) return 1
        return slot.split("+").count { it.isNotBlank() }.coerceAtLeast(1)
    }

    private fun isLab(type: String): Boolean {
        return type.contains("LAB", true) ||
                type.contains("LO", true) ||
                type.contains("ELA", true) ||
                type.equals("L", true) ||
                type.equals("P", true) ||
                type.contains("PRACTICAL", true)
    }

    private fun normalizeCourseCode(code: String): String {
        return code.replace(Regex("[^A-Z0-9]"), "").uppercase(Locale.ENGLISH)
    }

    private fun numericValue(value: String?): Int {
        return value?.filter { it.isDigit() }?.toIntOrNull() ?: 0
    }

    @SuppressLint("NewApi")
    private fun findLatestHistoryDate(attendance: AttendanceModel): LocalDate? {
        return attendance.history
            ?.mapNotNull { parseHistoryDate(it.date) }
            ?.maxOrNull()
    }

    @SuppressLint("NewApi")
    private fun parseHistoryDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val clean = value.substringAfter(",").trim()

        Regex("""(\d{1,2})-(\d{1,2})""").matchEntire(clean)?.let {
            val day = it.groupValues[1].toInt()
            val month = it.groupValues[2].toInt()

            return try {
                LocalDate.of(LocalDate.now().year, month, day)
            } catch (_: Exception) { null }
        }

        val formats = listOf(
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yy", Locale.ENGLISH),
            DateTimeFormatter.ISO_LOCAL_DATE
        )

        for (formatter in formats) {
            try { return LocalDate.parse(clean, formatter) } catch (_: Exception) {}
        }
        return null
    }

    @SuppressLint("NewApi")
    private fun parseCalendarDate(value: String): LocalDate? {
        val parts = value.trim().uppercase(Locale.ENGLISH).split("-")
        if (parts.size != 3) return null

        val day = parts[0].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null

        val month = when (parts[1]) {
            "JANUARY" -> 1
            "FEBRUARY" -> 2
            "MARCH" -> 3
            "APRIL" -> 4
            "MAY" -> 5
            "JUNE" -> 6
            "JULY" -> 7
            "AUGUST" -> 8
            "SEPTEMBER" -> 9
            "OCTOBER" -> 10
            "NOVEMBER" -> 11
            "DECEMBER" -> 12
            else -> return null
        }

        return try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) { null }
    }

    private fun normalizeExamName(particulars: String): String? {
        return when {
            particulars.contains("CAT - II", true) || particulars.contains("CAT-II", true) || particulars.contains("Continuous Assessment Test - II", true) -> "CAT-II"
            particulars.contains("CAT - I", true) || particulars.contains("CAT-I", true) || particulars.contains("Continuous Assessment Test - I", true) -> "CAT-I"
            particulars.contains("LAB FAT", true) || particulars.contains("Laboratory FAT", true) -> null
            particulars.contains("Final Assessment Test", true) || particulars.contains("FAT", true) -> "FAT"
            else -> null
        }
    }

    @SuppressLint("NewApi")
    private fun buildBlockedDateSet(blockedDates: Map<String, String>): Set<LocalDate> {
        val result = mutableSetOf<LocalDate>()
        for (value in blockedDates.keys) {
            val formats = listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH))
            for (formatter in formats) {
                try {
                    result.add(LocalDate.parse(value, formatter))
                    break
                } catch (_: Exception) {}
            }
        }
        return result
    }

    @SuppressLint("NewApi")
    private fun isBlocked(date: LocalDate, blockedDates: Set<LocalDate>): Boolean {
        return date in blockedDates
    }
}