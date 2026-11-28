package com.vtop.logic

import android.content.Context
import android.util.Log
import com.vtop.models.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale

data class CourseAttemptRecord(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val credits: String,
    val grade: String,
    val examMonth: String,
    val resultDeclared: String
)

object CourseAttemptStore {
    private const val PREFS_NAME = "VTOP_PREFS"
    private const val KEY_ATTEMPTS = "COURSE_ATTEMPTS_MAP_V1"

    @Volatile
    private var memoryCache: Map<String, List<CourseAttemptRecord>> = emptyMap()

    val version = androidx.compose.runtime.mutableIntStateOf(0)

    fun updateAll(attemptsByCourse: Map<String, List<CourseAttemptRecord>>, context: Context? = null) {
        if (attemptsByCourse.isEmpty()) return
        memoryCache = attemptsByCourse
        if (context != null) {
            saveToPrefs(context, attemptsByCourse)
        }
        version.intValue++
    }

    fun getAll(context: Context? = null): Map<String, List<CourseAttemptRecord>> {
        if (memoryCache.isNotEmpty()) return memoryCache
        if (context != null) {
            val loaded = loadFromPrefs(context)
            if (loaded.isNotEmpty()) {
                memoryCache = loaded
                return loaded
            }
        }
        return emptyMap()
    }

    fun getAttempts(courseCode: String, context: Context? = null): List<CourseAttemptRecord> {
        val norm = courseCode.trim().uppercase(Locale.ENGLISH)
        return getAll(context)[norm].orEmpty()
    }

    private fun saveToPrefs(context: Context, map: Map<String, List<CourseAttemptRecord>>) {
        try {
            val root = JSONObject()
            map.forEach { (code, list) ->
                val arr = JSONArray()
                list.forEach { item ->
                    arr.put(
                        JSONObject().apply {
                            put("courseCode", item.courseCode)
                            put("courseTitle", item.courseTitle)
                            put("courseType", item.courseType)
                            put("credits", item.credits)
                            put("grade", item.grade)
                            put("examMonth", item.examMonth)
                            put("resultDeclared", item.resultDeclared)
                        }
                    )
                }
                root.put(code, arr)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ATTEMPTS, root.toString())
                .apply()
        } catch (e: Exception) {
            Log.e("MARKS_PARSER", "Failed saving course attempts", e)
        }
    }

    private fun loadFromPrefs(context: Context): Map<String, List<CourseAttemptRecord>> {
        return try {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ATTEMPTS, null) ?: return emptyMap()
            val root = JSONObject(raw)
            val result = mutableMapOf<String, List<CourseAttemptRecord>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val arr = root.optJSONArray(code) ?: continue
                val list = mutableListOf<CourseAttemptRecord>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    list.add(
                        CourseAttemptRecord(
                            courseCode = obj.optString("courseCode"),
                            courseTitle = obj.optString("courseTitle"),
                            courseType = obj.optString("courseType"),
                            credits = obj.optString("credits"),
                            grade = obj.optString("grade"),
                            examMonth = obj.optString("examMonth"),
                            resultDeclared = obj.optString("resultDeclared")
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    result[code] = list
                }
            }
            result
        } catch (e: Exception) {
            Log.e("MARKS_PARSER", "Failed loading course attempts", e)
            emptyMap()
        }
    }
}

object MarksParser {

    // Helper to scrape the Semester Dropdown lists
    fun parseSemesters(html: String): List<SemesterOption> {
        val list = mutableListOf<SemesterOption>()
        try {
            val doc = Jsoup.parse(html)
            val options = doc.select("select#semesterSubId option")
            for (opt in options) {
                val value = opt.attr("value")
                if (value.isNotBlank()) {
                    list.add(SemesterOption(id = value, name = opt.text().trim()))
                }
            }
        } catch (e: Exception) { Log.e("MARKS_PARSER", "Error parsing semesters", e) }
        return list
    }

    fun parseMarks(html: String): List<CourseMark> {
        val list = mutableListOf<CourseMark>()
        try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.customTable > tbody > tr")

            var i = 1 // Skip header row
            while (i < rows.size) {
                val mainRow = rows[i]
                if (mainRow.hasClass("tableContent") && mainRow.select("td").size >= 9) {
                    val cells = mainRow.select("td")
                    val courseCode = cells[2].text().trim()
                    val courseTitle = cells[3].text().trim()
                    val courseType = cells[4].text().trim()

                    val details = mutableListOf<MarkDetail>()
                    var totalWeightageMark = 0.0
                    var totalWeightagePercent = 0.0

                    // The immediate next row contains the nested details table
                    if (i + 1 < rows.size) {
                        val detailRow = rows[i + 1]
                        val detailCells = detailRow.select("table.customTable-level1 tr.tableContent-level1")
                        for (dRow in detailCells) {
                            val dCols = dRow.select("td")
                            if (dCols.size >= 7) {
                                val weightagePercent = dCols[3].text().toDoubleOrNull() ?: 0.0
                                val weightageMark = dCols[6].text().toDoubleOrNull() ?: 0.0

                                details.add(
                                    MarkDetail(
                                        title = dCols[1].text().trim(),
                                        maxMark = dCols[2].text().trim(),
                                        weightagePercent = weightagePercent,
                                        scoredMark = dCols[5].text().trim(),
                                        weightageMark = weightageMark
                                    )
                                )
                                totalWeightagePercent += weightagePercent
                                totalWeightageMark += weightageMark
                            }
                        }
                        i++ // Skip the nested row for the next main loop iteration
                    }

                    list.add(CourseMark(courseCode, courseTitle, courseType, details, totalWeightageMark, totalWeightagePercent))
                }
                i++
            }
        } catch (e: Exception) { Log.e("MARKS_PARSER", "Error parsing marks", e) }
        return list
    }

    fun parseGrades(html: String): List<CourseGrade> {
        val list = mutableListOf<CourseGrade>()
        try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.table-hover tr")
            for (row in rows) {
                val cells = row.select("td")
                if (cells.size >= 8) {
                    list.add(
                        CourseGrade(
                            courseCode = cells[1].text().trim(),
                            courseTitle = cells[2].text().trim(),
                            courseType = cells[3].text().trim(),
                            grade = cells[6].text().trim()
                        )
                    )
                }
            }
        } catch (e: Exception) { Log.e("MARKS_PARSER", "Error parsing grades", e) }
        return list
    }

    fun parseHistory(html: String, context: Context? = null): Pair<CGPASummary?, List<GradeHistoryItem>> {
        var summary: CGPASummary? = null
        val historyList = mutableListOf<GradeHistoryItem>()
        val attemptsByCourse = mutableMapOf<String, List<CourseAttemptRecord>>()

        try {
            val doc = Jsoup.parse(html)

            // 1. Extract CGPA Summary
            val cgpaRow = doc.select("div.box-body.table-responsive tbody tr").first()
            if (cgpaRow != null) {
                val cells = cgpaRow.select("td")
                if (cells.size >= 3) {
                    summary = CGPASummary(
                        creditsRegistered = cells[0].text().trim(),
                        creditsEarned = cells[1].text().trim(),
                        cgpa = cells[2].text().trim()
                    )
                }
            }

            // 2. Extract History Safely + Multi-Attempt Details
            val courseTables = doc.select("table.customTable")
            for (table in courseTables) {
                if (!table.html().contains("Course Code")) {
                    continue
                }

                val rows = table.select("tr.tableContent")
                for (row in rows) {
                    val cells = row.select("td")

                    if (cells.size >= 10 && cells[0].text().trim().toIntOrNull() != null) {
                        val courseCode = cells[1].text().trim()
                        val courseTitle = cells[2].text().trim()
                        val courseType = cells[3].text().trim()
                        val credits = cells[4].text().trim()
                        val grade = cells[5].text().trim()
                        val examMonth = cells[6].text().trim()
                        val resultDeclared = cells[7].text().trim()
                        val courseDistribution = cells[8].text().trim()

                        historyList.add(
                            GradeHistoryItem(
                                courseCode = courseCode,
                                courseTitle = courseTitle,
                                courseType = courseType,
                                credits = credits,
                                grade = grade,
                                examMonth = examMonth,
                                courseDistribution = courseDistribution
                            )
                        )

                        // Parse nested attempts from tr#detailsView_<COURSE_CODE>
                        val detailRow = doc.selectFirst("tr#detailsView_$courseCode")
                        val parsedAttempts = mutableListOf<CourseAttemptRecord>()
                        if (detailRow != null) {
                            val subRows = detailRow.select("table.customTable-level1 tr.tableContent-level1")
                            for (sub in subRows) {
                                val subCells = sub.select("td")
                                if (subCells.size >= 7) {
                                    val subCredits = subCells[3].text().trim()
                                    val subGrade = subCells[4].text().trim()
                                    // Skip ETH/ELA sub-rows that have blank credits and "-"/"Y" grades
                                    if (subCredits.isNotEmpty() && subGrade.isNotEmpty() && subGrade != "-") {
                                        parsedAttempts.add(
                                            CourseAttemptRecord(
                                                courseCode = subCells[0].text().trim(),
                                                courseTitle = subCells[1].text().trim(),
                                                courseType = subCells[2].text().trim(),
                                                credits = subCredits,
                                                grade = subGrade,
                                                examMonth = subCells[5].text().trim(),
                                                resultDeclared = subCells[6].text().trim()
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (parsedAttempts.isEmpty()) {
                            parsedAttempts.add(
                                CourseAttemptRecord(
                                    courseCode = courseCode,
                                    courseTitle = courseTitle,
                                    courseType = courseType,
                                    credits = credits,
                                    grade = grade,
                                    examMonth = examMonth,
                                    resultDeclared = resultDeclared
                                )
                            )
                        }

                        attemptsByCourse[courseCode.uppercase(Locale.ENGLISH)] = parsedAttempts
                    }
                }
            }

            if (attemptsByCourse.isNotEmpty()) {
                CourseAttemptStore.updateAll(attemptsByCourse, context)
            }
        } catch (e: Exception) {
            Log.e("GRADE-HISTORY_PARSER", "Error parsing history", e)
        }

        return Pair(summary, historyList)
    }
}