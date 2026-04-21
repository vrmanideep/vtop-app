package com.vtop.logic

import android.annotation.SuppressLint
import com.vtop.models.AttendanceModel
import com.vtop.models.TimetableModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class BunkProjectorResult(
    val courseCode: String,
    val courseType: String,
    val currentAttended: Int,
    val currentTotal: Int,
    val currentPct: Float,
    val lastUpdatedStr: String,
    val gapClassesAdded: Int,
    val gapBreakdown: List<String>,
    val missedClassesAdded: Int,
    val missedBreakdown: List<String>,
    val projectedAttended: Int,
    val projectedTotal: Int,
    val projectedPct: Float,
    val isDanger: Boolean
)

object BunkSimulator {

    @SuppressLint("NewApi")
    fun simulateMultiDayBunk(
        validDates: List<LocalDate>,
        timetable: TimetableModel,
        attendanceData: List<AttendanceModel>,
        blockedDates: Map<String, String>
    ): List<BunkProjectorResult> {

        if (validDates.isEmpty() || attendanceData.isEmpty()) return emptyList()

        val maxBunkDate = validDates.maxOrNull() ?: return emptyList()
        val currentYear = LocalDate.now().year

        val semEndDt = LocalDate.of(currentYear, 5, 19)
        if (maxBunkDate.isAfter(semEndDt)) {
            throw IllegalArgumentException("HALT: The semester officially ends on 19-05. Cannot simulate beyond this date.")
        }

        val cleanBlocked = mutableSetOf<String>()
        blockedDates.keys.forEach { dateStr ->
            try {
                val ld = LocalDate.parse(dateStr)
                cleanBlocked.add(ld.format(DateTimeFormatter.ofPattern("dd-MM")))
            } catch (e: Exception) {
                try {
                    val ld = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                    cleanBlocked.add(ld.format(DateTimeFormatter.ofPattern("dd-MM")))
                } catch (e2: Exception) {
                    val parts = dateStr.split("-")
                    if (parts.size >= 2) {
                        cleanBlocked.add(String.format(Locale.ENGLISH, "%02d-%02d", parts[0].toInt(), parts[1].toInt()))
                    }
                }
            }
        }

        val results = mutableListOf<BunkProjectorResult>()
        val bunkSet = validDates.map { it.format(DateTimeFormatter.ofPattern("dd-MM")) }.toSet()

        for (att in attendanceData) {
            val cCode = att.courseCode ?: continue
            val cType = att.courseType ?: ""

            val cleanAttCode = cCode.replace(Regex("[^A-Z0-9]"), "").uppercase(Locale.ENGLISH)

            // Baseline check to see if the Attendance card is a Lab
            val attIsLab = cType.contains("LAB", true) || cType.contains("LO", true) || cType.contains("ELA", true) || cType.contains("PRACTICAL", true)

            val currentAttended = att.attendedClasses?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val currentTotal = att.totalClasses?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            val currentPct = att.attendancePercentage?.filter { it.isDigit() }?.toFloatOrNull() ?: 0f

            var lastUpdDt = LocalDate.now()
            var lastUpdatedStr = "Today (Assumed)"

            if (!att.history.isNullOrEmpty()) {
                val lastEntryDateStr = att.history.firstOrNull()?.date
                if (lastEntryDateStr != null) {
                    lastUpdatedStr = lastEntryDateStr
                    val cleanDate = lastEntryDateStr.substringAfter(",").trim()
                    try {
                        val parts = cleanDate.split("-")
                        if (parts.size >= 3) {
                            lastUpdDt = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                        } else if (parts.size == 2) {
                            lastUpdDt = LocalDate.of(currentYear, parts[1].toInt(), parts[0].toInt())
                        }
                    } catch (_: Exception) {}
                }
            }

            if (lastUpdDt.isAfter(LocalDate.now())) {
                lastUpdDt = LocalDate.now()
            }

            var simTotal = currentTotal
            var simAttended = currentAttended
            var gapClasses = 0
            var missedClasses = 0
            val gapBreakdown = mutableListOf<String>()
            val missedBreakdown = mutableListOf<String>()

            // ==========================================
            // PHASE A: RETROACTIVE BUNKING (PAST DATES)
            // ==========================================
            for (date in validDates) {
                if (!date.isAfter(lastUpdDt)) {
                    val dateStrFull = date.format(DateTimeFormatter.ofPattern("dd-MM"))
                    val dayNameShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    val currentDayKey = date.dayOfWeek.name.take(3).uppercase(Locale.ENGLISH)

                    val dayClasses = timetable.scheduleMap.entries.firstOrNull {
                        it.key.uppercase(Locale.ENGLISH).startsWith(currentDayKey)
                    }?.value ?: emptyList()

                    var targetFound = false
                    var penalty = 1

                    for (cls in dayClasses) {
                        val cleanTtCode = cls.courseCode.replace(Regex("[^A-Z0-9]"), "").uppercase(Locale.ENGLISH)

                        // THE FIX: Enforce Type Match (Lab == Lab, Theory == Theory)
                        val ttIsLab = cls.courseType.contains("LAB", true) || cls.courseType.contains("LO", true) || cls.courseType.contains("ELA", true) || cls.courseType.equals("L", true) || cls.courseType.equals("P", true) || cls.courseType.contains("PRACTICAL", true)

                        if ((cleanTtCode.contains(cleanAttCode) || cleanAttCode.contains(cleanTtCode)) && (attIsLab == ttIsLab)) {
                            targetFound = true
                            penalty = if (ttIsLab) 2 else 1
                            break
                        }
                    }

                    if (targetFound) {
                        val wasPresent = if (!att.history.isNullOrEmpty()) {
                            val historyMatch = att.history.firstOrNull { it.date?.contains(dateStrFull) == true }
                            historyMatch != null && (historyMatch.status?.contains("Present", true) == true || historyMatch.status?.contains("Attended", true) == true)
                        } else {
                            true // Blind trust fallback
                        }

                        if (wasPresent) {
                            simAttended -= penalty
                            missedClasses += penalty
                            missedBreakdown.add("$dateStrFull ($dayNameShort) [PAST] : -$penalty attended")
                        }
                    }
                }
            }

            // ==========================================
            // PHASE B: GAP & FUTURE BUNKING
            // ==========================================
            var currDt = lastUpdDt.plusDays(1)
            val endDt = if (maxBunkDate.isAfter(LocalDate.now())) maxBunkDate else LocalDate.now()

            while (!currDt.isAfter(endDt)) {
                val dateStrFull = currDt.format(DateTimeFormatter.ofPattern("dd-MM"))
                val dayNameShort = currDt.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                val currentDayKey = currDt.dayOfWeek.name.take(3).uppercase(Locale.ENGLISH)

                if (cleanBlocked.contains(dateStrFull)) {
                    currDt = currDt.plusDays(1)
                    continue
                }

                val dayClasses = timetable.scheduleMap.entries.firstOrNull {
                    it.key.uppercase(Locale.ENGLISH).startsWith(currentDayKey)
                }?.value ?: emptyList()

                var courseHappensToday = false
                var penalty = 1

                for (cls in dayClasses) {
                    val cleanTtCode = cls.courseCode.replace(Regex("[^A-Z0-9]"), "").uppercase(Locale.ENGLISH)

                    // THE FIX: Enforce Type Match (Lab == Lab, Theory == Theory)
                    val ttIsLab = cls.courseType.contains("LAB", true) || cls.courseType.contains("LO", true) || cls.courseType.contains("ELA", true) || cls.courseType.equals("L", true) || cls.courseType.equals("P", true) || cls.courseType.contains("PRACTICAL", true)

                    if ((cleanTtCode.contains(cleanAttCode) || cleanAttCode.contains(cleanTtCode)) && (attIsLab == ttIsLab)) {
                        courseHappensToday = true
                        penalty = if (ttIsLab) 2 else 1
                        break
                    }
                }

                if (courseHappensToday) {
                    if (bunkSet.contains(dateStrFull)) {
                        simTotal += penalty
                        missedClasses += penalty
                        missedBreakdown.add("$dateStrFull ($dayNameShort) : +$penalty missed")
                    } else {
                        simTotal += penalty
                        simAttended += penalty
                        gapClasses += penalty
                        gapBreakdown.add("$dateStrFull ($dayNameShort) : +$penalty")
                    }
                }
                currDt = currDt.plusDays(1)
            }

            if (simTotal != currentTotal || simAttended != currentAttended) {
                val projectedPct = if (simTotal > 0) (simAttended.toFloat() / simTotal.toFloat()) * 100f else 0f
                results.add(
                    BunkProjectorResult(
                        courseCode = cCode,
                        courseType = cType,
                        currentAttended = currentAttended,
                        currentTotal = currentTotal,
                        currentPct = currentPct,
                        lastUpdatedStr = lastUpdatedStr,
                        gapClassesAdded = gapClasses,
                        gapBreakdown = gapBreakdown,
                        missedClassesAdded = missedClasses,
                        missedBreakdown = missedBreakdown,
                        projectedAttended = simAttended,
                        projectedTotal = simTotal,
                        projectedPct = projectedPct,
                        isDanger = projectedPct < 75f
                    )
                )
            }
        }
        return results
    }
}