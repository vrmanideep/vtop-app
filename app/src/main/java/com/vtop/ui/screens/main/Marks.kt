@file:Suppress("SpellCheckingInspection")
package com.vtop.ui.screens.main

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.composables.icons.lucide.*
import com.vtop.core.*
import com.vtop.logic.*
import com.vtop.models.*
import com.vtop.sync.*
import com.vtop.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val MarksPrimaryAccent = Color(0xFF0090FF)
private val MarksColorSuccess = Color(0xFF4ADE80)
private val MarksColorWarning = Color(0xFFF97316)
private val MarksColorDanger = Color(0xFFF87171)

private val MarksColorSGrade = Color(0xFFa855f7)
private val MarksColorAGrade = Color(0xFF4ADE80)
private val MarksColorBGrade = Color(0xFF60A5FA)
private val MarksColorCGrade = Color(0xFFFBBF24)
private val MarksColorFGrade = Color(0xFFF87171)

data class UiMarkDetail(
    val title: String,
    val scoredMaxStr: String,
    val wgtMaxStr: String
)

data class UiMarkComponent(
    val name: String,
    val gainedRaw: Double,
    val totalRaw: Double,
    val details: List<UiMarkDetail>
)

data class UiMark(
    val courseCode: String,
    val courseType: String,
    val courseTitle: String,
    val gainedRaw: Double,
    val totalRaw: Double,
    val components: List<UiMarkComponent>
)

private data class PlannedCourseEntry(
    val id: String,
    val courseCode: String,
    val courseTitle: String,
    val credits: Double,
    val grade: String
)

private data class ReportCourseRow(
    val key: String,
    val effectiveIndex: Int?, // non-null only for the effective row counted in CGPA
    val item: GradeHistoryItem
)

private data class ReportSemester(
    val semester: String,
    val courses: List<ReportCourseRow>,
    val gpa: Double,
    val totalCredits: Double,
    val countedCredits: Double
)

private data class GradeRangeCutoff(
    val grade: String,
    val rawText: String,
    val minInclusive: Double?,
    val maxExclusive: Double?,
    val hasPolicyNote: Boolean
)

private data class GradeDetailAssessmentRow(
    val slNo: String,
    val title: String,
    val maxMark: String,
    val weightagePercent: String,
    val status: String,
    val scoredMark: String,
    val weightageMark: String
)

private data class GradeDetailComponentTable(
    val classNumber: String,
    val courseType: String,
    val rows: List<GradeDetailAssessmentRow>,
    val totalWeightageMark: String
)

private data class ParsedGradeViewDetail(
    val semesterSubId: String,
    val semesterName: String,
    val courseId: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val gradingType: String,
    val grandTotal: Double,
    val grandTotalText: String,
    val grade: String,
    val classStrength: Int,
    val gradingStrength: Int,
    val mean: Double,
    val sd: Double,
    val gradeCutoffs: List<GradeRangeCutoff>,
    val sPolicyNote: String?,
    val componentTables: List<GradeDetailComponentTable>
)

private fun ParsedGradeViewDetail.toJsonString(): String {
    val root = JSONObject().apply {
        put("semesterSubId", semesterSubId)
        put("semesterName", semesterName)
        put("courseId", courseId)
        put("courseCode", courseCode)
        put("courseTitle", courseTitle)
        put("courseType", courseType)
        put("gradingType", gradingType)
        put("grandTotal", grandTotal)
        put("grandTotalText", grandTotalText)
        put("grade", grade)
        put("classStrength", classStrength)
        put("gradingStrength", gradingStrength)
        put("mean", mean)
        put("sd", sd)
        put("sPolicyNote", sPolicyNote ?: JSONObject.NULL)

        val cutoffsArr = JSONArray()
        gradeCutoffs.forEach { c ->
            cutoffsArr.put(JSONObject().apply {
                put("grade", c.grade)
                put("rawText", c.rawText)
                put("minInclusive", c.minInclusive ?: JSONObject.NULL)
                put("maxExclusive", c.maxExclusive ?: JSONObject.NULL)
                put("hasPolicyNote", c.hasPolicyNote)
            })
        }
        put("gradeCutoffs", cutoffsArr)

        val tablesArr = JSONArray()
        componentTables.forEach { t ->
            val rowsArr = JSONArray()
            t.rows.forEach { r ->
                rowsArr.put(JSONObject().apply {
                    put("slNo", r.slNo)
                    put("title", r.title)
                    put("maxMark", r.maxMark)
                    put("weightagePercent", r.weightagePercent)
                    put("status", r.status)
                    put("scoredMark", r.scoredMark)
                    put("weightageMark", r.weightageMark)
                })
            }
            tablesArr.put(JSONObject().apply {
                put("classNumber", t.classNumber)
                put("courseType", t.courseType)
                put("totalWeightageMark", t.totalWeightageMark)
                put("rows", rowsArr)
            })
        }
        put("componentTables", tablesArr)
    }
    return root.toString()
}

private fun parseGradeViewDetailFromJson(raw: String): ParsedGradeViewDetail? {
    return try {
        val root = JSONObject(raw)
        val cutoffs = mutableListOf<GradeRangeCutoff>()
        val cutoffsArr = root.optJSONArray("gradeCutoffs")
        if (cutoffsArr != null) {
            for (i in 0 until cutoffsArr.length()) {
                val obj = cutoffsArr.optJSONObject(i) ?: continue
                cutoffs.add(
                    GradeRangeCutoff(
                        grade = obj.optString("grade"),
                        rawText = obj.optString("rawText"),
                        minInclusive = if (obj.isNull("minInclusive")) null else obj.optDouble("minInclusive"),
                        maxExclusive = if (obj.isNull("maxExclusive")) null else obj.optDouble("maxExclusive"),
                        hasPolicyNote = obj.optBoolean("hasPolicyNote", false)
                    )
                )
            }
        }

        val tables = mutableListOf<GradeDetailComponentTable>()
        val tablesArr = root.optJSONArray("componentTables")
        if (tablesArr != null) {
            for (i in 0 until tablesArr.length()) {
                val tObj = tablesArr.optJSONObject(i) ?: continue
                val rows = mutableListOf<GradeDetailAssessmentRow>()
                val rowsArr = tObj.optJSONArray("rows")
                if (rowsArr != null) {
                    for (j in 0 until rowsArr.length()) {
                        val rObj = rowsArr.optJSONObject(j) ?: continue
                        rows.add(
                            GradeDetailAssessmentRow(
                                slNo = rObj.optString("slNo"),
                                title = rObj.optString("title"),
                                maxMark = rObj.optString("maxMark"),
                                weightagePercent = rObj.optString("weightagePercent"),
                                status = rObj.optString("status"),
                                scoredMark = rObj.optString("scoredMark"),
                                weightageMark = rObj.optString("weightageMark")
                            )
                        )
                    }
                }
                tables.add(
                    GradeDetailComponentTable(
                        classNumber = tObj.optString("classNumber"),
                        courseType = tObj.optString("courseType"),
                        rows = rows,
                        totalWeightageMark = tObj.optString("totalWeightageMark")
                    )
                )
            }
        }

        val title = root.optString("courseTitle")
        if (title.isBlank()) return null

        ParsedGradeViewDetail(
            semesterSubId = root.optString("semesterSubId"),
            semesterName = root.optString("semesterName"),
            courseId = root.optString("courseId"),
            courseCode = root.optString("courseCode"),
            courseTitle = title,
            courseType = root.optString("courseType"),
            gradingType = root.optString("gradingType"),
            grandTotal = root.optDouble("grandTotal", 0.0),
            grandTotalText = root.optString("grandTotalText"),
            grade = root.optString("grade"),
            classStrength = root.optInt("classStrength", 0),
            gradingStrength = root.optInt("gradingStrength", 0),
            mean = root.optDouble("mean", 0.0),
            sd = root.optDouble("sd", 0.0),
            gradeCutoffs = cutoffs,
            sPolicyNote = if (root.isNull("sPolicyNote")) null else root.optString("sPolicyNote").takeIf { it.isNotBlank() },
            componentTables = tables
        )
    } catch (_: Exception) {
        null
    }
}

private fun getGradeColor(grade: String): Color {
    return when (grade.trim().uppercase(Locale.getDefault())) {
        "S" -> MarksColorSGrade
        "A" -> MarksColorAGrade
        "B" -> MarksColorBGrade
        "C" -> MarksColorCGrade
        "D", "E" -> MarksColorCGrade.copy(alpha = 0.7f)
        "F", "N" -> MarksColorFGrade
        else -> Color.Gray
    }
}

private fun getMarksColor(gained: Double, total: Double): Color {
    if (total <= 0.0) return MarksPrimaryAccent
    val pct = (gained / total) * 100
    return when {
        pct > 85.0 -> MarksColorSuccess
        pct >= 50.0 -> MarksPrimaryAccent
        pct >= 25.0 -> MarksColorWarning
        else -> MarksColorDanger
    }
}

private fun formatVal(v: Double): String {
    return if (v % 1.0 == 0.0) {
        v.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", v).removeSuffix("0").removeSuffix(".")
    }
}

private fun cleanMark(value: Any?): String {
    val strVal = value?.toString()?.trim() ?: return "-"
    val d = strVal.toDoubleOrNull() ?: return strVal
    return formatVal(d)
}

private fun getCourseTypePriority(type: String): Int {
    val t = type.uppercase(Locale.getDefault())
    return when {
        t.contains("TH") || t.contains("ETH") || t.contains("THEORY") -> 0
        t.contains("LO") || t.contains("ELA") || t.contains("LAB") -> 1
        t.contains("PJT") || t.contains("EPJ") || t.contains("PROJECT") -> 2
        else -> 3
    }
}

private fun getBestAttemptTotals(mark: CourseMark): Pair<Double, Double> {
    val detailsList = mark.details

    if (detailsList.isEmpty()) {
        return Pair(0.0, 0.0)
    }

    val groups = detailsList.groupBy { detail ->
        detail.title.replace("Re Evaluation ", "", ignoreCase = true).trim().uppercase()
    }

    var totalGained = 0.0
    var totalMax = 0.0

    groups.forEach { (_, detailsInGroup) ->
        val bestAttempt = detailsInGroup.maxByOrNull { it.weightageMark }
        totalGained += bestAttempt?.weightageMark ?: 0.0
        totalMax += bestAttempt?.weightagePercent ?: 0.0
    }

    return Pair(totalGained, totalMax)
}

private fun getGradePoints(grade: String): Int {
    return when (grade.trim().uppercase(Locale.getDefault())) {
        "S" -> 10
        "A" -> 9
        "B" -> 8
        "C" -> 7
        "D" -> 6
        "E" -> 5
        "F", "N" -> 0
        else -> -1 // Ignore "P" or unknown grades for GPA calculation
    }
}

private fun calculateSemesterGPA(courses: List<GradeHistoryItem>): Double {
    var totalPoints = 0.0
    var totalCredits = 0.0
    courses.forEach { course ->
        val pts = getGradePoints(course.grade)
        if (pts >= 0) {
            val cred = course.credits.toDoubleOrNull() ?: 0.0
            totalPoints += (pts * cred)
            totalCredits += cred
        }
    }
    return if (totalCredits > 0) totalPoints / totalCredits else 0.0
}

private fun parseExamMonth(month: String): YearMonth {
    return try {
        val cleanMonth = month.trim().split("-").let {
            if (it.size == 2) {
                it[0].lowercase().replaceFirstChar { c -> c.uppercase() } + "-" + it[1]
            } else month
        }
        val formatter = DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH)
        YearMonth.parse(cleanMonth, formatter)
    } catch (_: Exception) {
        YearMonth.of(1900, 1)
    }
}

private fun inferSemesterName(examMonth: String): String? {
    val parts = examMonth.trim().split("-")
    if (parts.size != 2) return null
    val year = parts[1].trim().toIntOrNull() ?: return null
    val mon = parts[0].trim().lowercase(Locale.ENGLISH).take(3)
    val shortNext = ((year + 1) % 100).toString().padStart(2, '0')
    val shortCurr = (year % 100).toString().padStart(2, '0')
    return when (mon) {
        "oct", "nov", "dec" -> "Fall $year-$shortNext"
        "jan", "feb" -> "Fall ${year - 1}-$shortCurr"
        "mar", "apr", "may" -> "Winter ${year - 1}-$shortCurr"
        "jun" -> "Summer 1 ${year - 1}-$shortCurr"
        "jul", "aug" -> "Summer 2 ${year - 1}-$shortCurr"
        else -> null
    }
}

private fun buildSemesterOptionMap(
    context: Context,
    historyData: List<GradeHistoryItem>,
    attemptsMap: Map<String, List<CourseAttemptRecord>> = emptyMap()
): Map<String, SemesterOption> {
    val allMonths = (historyData.map { it.examMonth.trim() } +
            attemptsMap.values.flatten().map { it.examMonth.trim() })
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedBy { parseExamMonth(it) }

    if (allMonths.isEmpty()) return emptyMap()

    val currentSemId = Vault.getSelectedSemester(context)[0]?.trim().orEmpty()
    val list2 = Vault.getSemesterOptions(context)
        .filter { it.id.trim() != currentSemId }
        .reversed()

    val paired = mutableMapOf<String, SemesterOption>()
    for (i in allMonths.indices) {
        val opt = list2.getOrNull(i)
        if (opt != null) {
            paired[allMonths[i]] = opt
        } else {
            val fallbackName = inferSemesterName(allMonths[i])
            if (fallbackName != null) {
                paired[allMonths[i]] = SemesterOption("", fallbackName)
            }
        }
    }
    return paired
}

private fun buildSemesterNameMap(
    context: Context,
    historyData: List<GradeHistoryItem>,
    attemptsMap: Map<String, List<CourseAttemptRecord>> = emptyMap()
): Map<String, String> {
    return buildSemesterOptionMap(context, historyData, attemptsMap)
        .mapValues { it.value.name.trim() }
        .filterValues { it.isNotEmpty() }
}

private fun buildTranscriptHistoryList(
    historyData: List<GradeHistoryItem>,
    attemptsMap: Map<String, List<CourseAttemptRecord>>
): List<GradeHistoryItem> {
    val result = mutableListOf<GradeHistoryItem>()
    historyData.forEach { effItem ->
        val normCode = effItem.courseCode.trim().uppercase(Locale.ENGLISH)
        val attempts = attemptsMap[normCode]
            ?.sortedBy { parseExamMonth(it.examMonth) }
            .orEmpty()

        if (attempts.size > 1) {
            attempts.forEach { att ->
                if (att.examMonth.equals(effItem.examMonth, ignoreCase = true)) {
                    result.add(effItem)
                } else {
                    result.add(
                        GradeHistoryItem(
                            courseCode = att.courseCode,
                            courseTitle = att.courseTitle.ifBlank { effItem.courseTitle },
                            courseType = att.courseType,
                            credits = att.credits,
                            grade = att.grade,
                            examMonth = att.examMonth,
                            courseDistribution = effItem.courseDistribution
                        )
                    )
                }
            }
        } else {
            result.add(effItem)
        }
    }
    return result
}

private fun parseCourseIdMapFromGradeViewHtml(html: String): Map<String, String> {
    val doc = Jsoup.parse(html)
    val result = mutableMapOf<String, String>()
    val idRegex = Regex("""getGradeViewDetails\s*\(\s*['"]([^'"]+)['"]\s*\)""")

    for (row in doc.select("tr")) {
        val tds = row.select("> td")
        if (tds.size >= 8) {
            val code = tds[1].text().trim().uppercase(Locale.ENGLISH)
            val onclick = tds[7].selectFirst("button")?.attr("onclick").orEmpty()
            val match = idRegex.find(onclick)
            if (code.isNotEmpty() && match != null) {
                result[code] = match.groupValues[1].trim()
            }
        }
    }
    return result
}

private fun parseGradeRangeCutoff(grade: String, rawCellText: String, hasHash: Boolean): GradeRangeCutoff {
    val cleaned = rawCellText.replace("#", "").replace("\\s+".toRegex(), " ").trim()
    val geMatch = Regex(""">=\s*(\d+(?:\.\d+)?)""").find(cleaned)
    val ltMatch = Regex("""<\s*(\d+(?:\.\d+)?)""").find(cleaned)
    return GradeRangeCutoff(
        grade = grade,
        rawText = cleaned,
        minInclusive = geMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
        maxExclusive = ltMatch?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
        hasPolicyNote = hasHash
    )
}

private fun parseGradeViewDetailsHtml(
    html: String,
    semesterSubId: String,
    semesterName: String,
    courseId: String,
    targetCourseCode: String
): ParsedGradeViewDetail? {
    val doc = Jsoup.parse(html)
    val normalizedTarget = targetCourseCode.trim().uppercase(Locale.ENGLISH)

    var courseTitle = ""
    var courseType = ""
    var gradingType = ""
    var grandTotalText = ""
    var grade = ""

    for (row in doc.select("tr")) {
        val tds = row.select("> td")
        if (tds.size >= 7 && tds[1].text().trim().equals(normalizedTarget, ignoreCase = true)) {
            courseTitle = tds[2].text().trim()
            courseType = tds[3].text().trim()
            gradingType = tds[4].text().trim()
            grandTotalText = tds[5].text().trim()
            grade = tds[6].text().trim()
            break
        }
    }

    if (courseTitle.isEmpty()) return null

    var classStrength = 0
    var gradingStrength = 0
    var mean = 0.0
    var sd = 0.0
    val cutoffs = mutableListOf<GradeRangeCutoff>()
    var sPolicyNote: String? = null
    val componentTables = mutableListOf<GradeDetailComponentTable>()

    val nestedTables = doc.select("td[colspan] > table")
    for (tbl in nestedTables) {
        val headerText = tbl.select("tr").firstOrNull()?.text().orEmpty()
        if (headerText.contains("Class Strength", ignoreCase = true) && headerText.contains("Mean", ignoreCase = true)) {
            val rows = tbl.select("tr")
            val dataRow = rows.getOrNull(2)
            val cells = dataRow?.select("td").orEmpty()
            if (cells.size >= 11) {
                classStrength = cells[0].text().trim().toIntOrNull() ?: 0
                gradingStrength = cells[1].text().trim().toIntOrNull() ?: 0
                mean = cells[2].text().trim().toDoubleOrNull() ?: 0.0
                sd = cells[3].text().trim().toDoubleOrNull() ?: 0.0
                val gradeLabels = listOf("S", "A", "B", "C", "D", "E", "F")
                gradeLabels.forEachIndexed { idx, gLabel ->
                    val cell = cells[4 + idx]
                    val hasHash = cell.text().contains("#")
                    cutoffs.add(parseGradeRangeCutoff(gLabel, cell.text(), hasHash))
                }
            }
            val noteRow = rows.getOrNull(3)
            if (noteRow != null && noteRow.text().contains("#")) {
                sPolicyNote = noteRow.text().trim()
            }
        } else if (headerText.contains("Class Number", ignoreCase = true)) {
            val rows = tbl.select("tr")
            val topHeaders = rows.firstOrNull()?.select("th").orEmpty()
            val classNumber = topHeaders.getOrNull(0)?.text()?.substringAfter(":")?.trim().orEmpty()
            val compType = topHeaders.getOrNull(1)?.text()?.substringAfter(":")?.trim().orEmpty()

            val assessmentRows = mutableListOf<GradeDetailAssessmentRow>()
            var totalWgtMark = ""

            for (i in 2 until rows.size) {
                val r = rows[i]
                val ths = r.select("th")
                if (ths.size >= 2 && r.text().contains("Total", ignoreCase = true)) {
                    totalWgtMark = ths.last()?.text()?.trim().orEmpty()
                    continue
                }
                val tds = r.select("td")
                if (tds.size >= 7) {
                    assessmentRows.add(
                        GradeDetailAssessmentRow(
                            slNo = tds[0].text().trim(),
                            title = tds[1].text().trim(),
                            maxMark = cleanMark(tds[2].text()),
                            weightagePercent = cleanMark(tds[3].text()),
                            status = tds[4].text().trim(),
                            scoredMark = cleanMark(tds[5].text()),
                            weightageMark = cleanMark(tds[6].text())
                        )
                    )
                }
            }

            componentTables.add(
                GradeDetailComponentTable(
                    classNumber = classNumber,
                    courseType = compType,
                    rows = assessmentRows,
                    totalWeightageMark = cleanMark(totalWgtMark)
                )
            )
        }
    }

    return ParsedGradeViewDetail(
        semesterSubId = semesterSubId,
        semesterName = semesterName,
        courseId = courseId,
        courseCode = normalizedTarget,
        courseTitle = courseTitle,
        courseType = courseType,
        gradingType = gradingType,
        grandTotal = grandTotalText.toDoubleOrNull() ?: 0.0,
        grandTotalText = cleanMark(grandTotalText),
        grade = grade,
        classStrength = classStrength,
        gradingStrength = gradingStrength,
        mean = mean,
        sd = sd,
        gradeCutoffs = cutoffs,
        sPolicyNote = sPolicyNote,
        componentTables = componentTables
    )
}

private suspend fun loadGradeDetailWithCache(
    context: Context,
    course: GradeHistoryItem,
    historyData: List<GradeHistoryItem>,
    forceRefresh: Boolean
): Result<ParsedGradeViewDetail> = withContext(Dispatchers.IO) {
    try {
        val prefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
        val attemptsMap = CourseAttemptStore.getAll(context)
        val semOptionMap = buildSemesterOptionMap(context, historyData, attemptsMap)
        val semOption = semOptionMap[course.examMonth.trim()]
        val semId = semOption?.id?.trim().orEmpty()
        val semName = semOption?.name?.trim().orEmpty().ifBlank { course.examMonth }

        if (semId.isEmpty()) {
            return@withContext Result.failure(Exception("Semester ID not found for ${course.examMonth}. Sync once to refresh semesters."))
        }

        val normCode = course.courseCode.trim().uppercase(Locale.ENGLISH)
        val detailCacheKey = "GRADE_VIEW_DETAIL_V2_${semId}_$normCode"

        if (!forceRefresh) {
            val cachedJson = prefs.getString(detailCacheKey, null)
            if (!cachedJson.isNullOrBlank()) {
                val cached = parseGradeViewDetailFromJson(cachedJson)
                if (cached != null) return@withContext Result.success(cached)
            }
        }

        val client = SessionManager.getSyncClient()
            ?: return@withContext Result.failure(Exception("Session expired. Pull down to sync first."))

        val regNo = Vault.getRegNo(context)
        if (regNo.isNotBlank() && regNo != "-") {
            client.authorizedId = regNo
        }

        // Step 1: Resolve internal courseId (e.g. AM_MAT1011_00210) from doStudentGradeView
        val semMapKey = "GRADE_VIEW_SEM_IDS_$semId"
        var codeToIdMap: Map<String, String> = if (!forceRefresh) {
            prefs.getString(semMapKey, null)?.let { rawJson ->
                try {
                    val obj = JSONObject(rawJson)
                    val parsedMap = mutableMapOf<String, String>()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        parsedMap[k] = obj.optString(k)
                    }
                    parsedMap
                } catch (_: Exception) { null }
            }.orEmpty()
        } else {
            emptyMap()
        }

        var internalCourseId = codeToIdMap[normCode]
        if (internalCourseId.isNullOrBlank()) {
            val semListHtml = client.fetchGradesRawHtml(semId, null)
                ?: return@withContext Result.failure(Exception("Unable to load semester grade list from VTOP."))
            codeToIdMap = parseCourseIdMapFromGradeViewHtml(semListHtml)
            if (codeToIdMap.isNotEmpty()) {
                prefs.edit { putString(semMapKey, JSONObject(codeToIdMap).toString()) }
            }
            internalCourseId = codeToIdMap[normCode]
        }

        if (internalCourseId.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Course details unavailable for $normCode in $semName."))
        }

        // Step 2: Fetch getGradeViewDetails for the resolved internal courseId
        val detailHtml = client.fetchGradeViewDetailsRawHtml(semId, internalCourseId)
            ?: return@withContext Result.failure(Exception("Failed to fetch grade statistics from VTOP."))

        val parsed = parseGradeViewDetailsHtml(
            html = detailHtml,
            semesterSubId = semId,
            semesterName = semName,
            courseId = internalCourseId,
            targetCourseCode = normCode
        ) ?: return@withContext Result.failure(Exception("Could not parse grade details for $normCode."))

        prefs.edit { putString(detailCacheKey, parsed.toJsonString()) }
        Result.success(parsed)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private suspend fun downloadAndSaveGradeHistoryPdf(context: Context): Result<Uri> =
    withContext(Dispatchers.IO) {
        try {
            val client = SessionManager.getSyncClient()
                ?: return@withContext Result.failure(Exception("Session expired. Sync first."))

            val regNo = Vault.getRegNo(context)
            if (regNo.isNotBlank() && regNo != "-") {
                client.authorizedId = regNo
            }

            val tempFile = File(context.cacheDir, "Grade_History.pdf")
            if (tempFile.exists()) tempFile.delete()

            val downloaded = client.downloadGradeHistoryPdf(tempFile)
            if (!downloaded || !tempFile.exists()) {
                return@withContext Result.failure(Exception("Failed to download Grade History PDF"))
            }

            val fileName = "Grade_History.pdf"
            val savedUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(Exception("Unable to create file in Downloads"))

                resolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { input -> input.copyTo(out) }
                } ?: return@withContext Result.failure(Exception("Unable to write PDF to Downloads"))

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                tempFile.delete()
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val outFile = File(downloadsDir, fileName)
                tempFile.copyTo(outFile, overwrite = true)
                tempFile.delete()
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            }

            Result.success(savedUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

private fun buildCurrentSemesterCourses(
    timetable: TimetableModel?,
    marksData: List<CourseMark>
): List<PlannedCourseEntry> {
    val regList: List<TimetableModel.RegisteredCourse> = timetable?.registeredCourses ?: emptyList()
    if (regList.isNotEmpty()) {
        return regList
            .groupBy { it.courseCode.trim().uppercase(Locale.ENGLISH) }
            .values
            .mapIndexed { idx, components ->
                val first = components.first()
                val totalCredits = components.sumOf { it.credits }
                PlannedCourseEntry(
                    id = "cur_${idx}_${first.courseCode}",
                    courseCode = first.courseCode.trim(),
                    courseTitle = first.courseName.trim().ifBlank { first.courseCode.trim() },
                    credits = totalCredits.takeIf { it > 0.0 } ?: 3.0,
                    grade = "A"
                )
            }
            .sortedBy { it.courseCode }
    }

    return marksData
        .groupBy { it.courseCode.trim().uppercase(Locale.ENGLISH) }
        .values
        .mapIndexed { idx, group ->
            val first = group.first()
            val hasEth = group.any { it.courseType.uppercase(Locale.ENGLISH).contains("ETH") }
            val hasElaOrEpj = group.any {
                val t = it.courseType.uppercase(Locale.ENGLISH)
                t.contains("ELA") || t.contains("EPJ")
            }
            val fallbackCredits = when {
                hasEth && hasElaOrEpj -> 4.0
                group.any { it.courseType.uppercase(Locale.ENGLISH).contains("TH") } -> 3.0
                else -> 2.0
            }
            PlannedCourseEntry(
                id = "cur_${idx}_${first.courseCode}",
                courseCode = first.courseCode.trim(),
                courseTitle = first.courseTitle.trim().ifBlank { first.courseCode.trim() },
                credits = fallbackCredits,
                grade = "A"
            )
        }
        .sortedBy { it.courseCode }
}

@Composable
fun Marks(
    marksData: List<CourseMark>,
    historySummary: CGPASummary?,
    historyData: List<GradeHistoryItem>,
    @Suppress("UNUSED_PARAMETER") timetable: TimetableModel? = null,
    onOpenCalculator: () -> Unit = {},
    onSelectCourse: (GradeHistoryItem) -> Unit = {}
) {
    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Marks_Screen")
    }
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    val mergeMarks = sharedPrefs.getBoolean("MERGE_MARKS", true)

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    val attemptVersion by CourseAttemptStore.version
    val attemptsMap = remember(historyData, attemptVersion) { CourseAttemptStore.getAll(context) }
    val transcriptHistory = remember(historyData, attemptsMap) {
        buildTranscriptHistoryList(historyData, attemptsMap)
    }
    val groupedHistory = remember(transcriptHistory) {
        transcriptHistory.groupBy { it.examMonth.ifEmpty { "Unknown Semester" } }
    }
    val expandedStates = remember(groupedHistory) {
        mutableStateMapOf<String, Boolean>().apply {
            groupedHistory.keys.forEach { this[it] = true }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> CurrentSemesterMarksView(marksData, mergeMarks)
                1 -> AcademicHistoryView(
                    historySummary = historySummary,
                    effectiveHistoryData = historyData,
                    transcriptHistoryData = transcriptHistory,
                    groupedHistory = groupedHistory,
                    expandedStates = expandedStates,
                    onCourseClick = onSelectCourse
                )
            }
        }

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 80.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary,
                    height = 3.dp
                )
            }
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Marks", fontSize = 15.sp, fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Medium) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("History", fontSize = 15.sp, fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Medium) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = pagerState.currentPage == 1 && historyData.isNotEmpty(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 100.dp)
        ) {
            FloatingActionButton(
                onClick = onOpenCalculator,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Lucide.Calculator, contentDescription = "CGPA Calculator", modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun normalCdf(z: Double): Double {
    val sign = if (z < 0.0) -1.0 else 1.0
    val x = kotlin.math.abs(z) / sqrt(2.0)
    val t = 1.0 / (1.0 + 0.3275911 * x)
    val erfApprox = 1.0 - (((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t) * exp(-x * x)
    return 0.5 * (1.0 + sign * erfApprox)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun GradeDetailSubView(
    course: GradeHistoryItem,
    historyData: List<GradeHistoryItem>,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ParsedGradeViewDetail?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = { if (!isLoading) refreshTrigger++ }
    )
    val attemptVersion by CourseAttemptStore.version
    val attemptsMap = remember(historyData, attemptVersion) {
        CourseAttemptStore.getAll(context)
    }
    val semesterNameMap = remember(historyData, attemptsMap) {
        buildSemesterNameMap(context, historyData, attemptsMap)
    }

    LaunchedEffect(course, refreshTrigger) {
        isLoading = true
        errorMessage = null
        val res = loadGradeDetailWithCache(
            context = context,
            course = course,
            historyData = historyData,
            forceRefresh = refreshTrigger > 0
        )
        isLoading = false
        if (res.isSuccess) {
            detail = res.getOrNull()
        } else {
            errorMessage = res.exceptionOrNull()?.message ?: "Failed to load course grade details"
        }
    }

    val gradeColor = getGradeColor(detail?.grade ?: course.grade)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.courseCode,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = detail?.semesterName ?: semesterNameMap[course.examMonth] ?: course.examMonth,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { refreshTrigger++ },
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Grade Details",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pullRefresh(pullRefreshState)
        ) {
            if (isLoading && detail == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Fetching grade breakdown & class stats...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (errorMessage != null && detail == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "Unable to load grade details",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { refreshTrigger++ }) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val data = detail
                if (data != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Course Summary Hero Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                            Text(
                                                text = data.courseTitle,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "${data.courseType} · ${course.credits} Credits",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .background(gradeColor.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                                .border(1.5.dp, gradeColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = data.grade,
                                                fontSize = 22.sp,
                                                fontWeight = FontWeight.Black,
                                                color = gradeColor
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "GRAND TOTAL",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    letterSpacing = 0.8.sp
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "${data.grandTotalText} / 100",
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = "GRADING MODE",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    letterSpacing = 0.8.sp
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = when (data.gradingType.uppercase(Locale.ENGLISH)) {
                                                        "RG" -> "Relative (RG)"
                                                        "AG" -> "Absolute (AG)"
                                                        else -> data.gradingType
                                                    },
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Bell Curve & Class Statistics (shown when Mean & SD are present, i.e. RG)
                        if (data.mean > 0.0 && data.sd > 0.0) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val zScore = (data.grandTotal - data.mean) / data.sd
                                        val percentile = (normalCdf(zScore) * 100.0).coerceIn(0.1, 99.9)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Class Distribution",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    text = "~${String.format(Locale.US, "%.1f", percentile)}th percentile",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        GradeBellCurveChart(
                                            mean = data.mean,
                                            sd = data.sd,
                                            studentScore = data.grandTotal,
                                            studentGrade = data.grade,
                                            cutoffs = data.gradeCutoffs
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            StatPill(label = "Class Size", value = data.classStrength.toString())
                                            StatPill(label = "Graded", value = data.gradingStrength.toString())
                                            StatPill(label = "Mean", value = String.format(Locale.US, "%.2f", data.mean))
                                            StatPill(label = "Std Dev", value = String.format(Locale.US, "%.2f", data.sd))
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Exact Range of Grades Card
                        if (data.gradeCutoffs.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Range of Grades",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        data.gradeCutoffs.forEach { cutoff ->
                                            val isEarned = cutoff.grade.equals(data.grade, ignoreCase = true)
                                            val cColor = getGradeColor(cutoff.grade)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isEarned) cColor.copy(alpha = 0.14f)
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                    )
                                                    .then(
                                                        if (isEarned) Modifier.border(1.dp, cColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                                        else Modifier
                                                    )
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(26.dp)
                                                            .background(cColor.copy(alpha = 0.18f), RoundedCornerShape(7.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = cutoff.grade,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = cColor
                                                        )
                                                    }
                                                    if (isEarned) {
                                                        Text(
                                                            text = "Your Grade",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = cColor
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = if (cutoff.hasPolicyNote) "${cutoff.rawText} #" else cutoff.rawText,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isEarned) FontWeight.Bold else FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        data.sPolicyNote?.let { note ->
                                            Text(
                                                text = note,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Component Marks Breakdown Tables
                        data.componentTables.forEachIndexed { idx, comp ->
                            item(key = "comp_${idx}_${comp.classNumber}") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = comp.courseType.ifBlank { "Assessments" },
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (comp.classNumber.isNotBlank()) {
                                                    Text(
                                                        text = comp.classNumber,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (comp.totalWeightageMark.isNotBlank()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = "Total: ${comp.totalWeightageMark}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Mark Title",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1.2f)
                                                )
                                                Text(
                                                    text = "Scored / Max",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.weight(0.9f)
                                                )
                                                Text(
                                                    text = "Wgt / %",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    textAlign = TextAlign.End,
                                                    modifier = Modifier.weight(0.8f)
                                                )
                                            }

                                            comp.rows.forEach { row ->
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1.2f)) {
                                                        Text(
                                                            text = row.title,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (row.status.isNotBlank() && !row.status.equals("Present", ignoreCase = true)) {
                                                            Text(
                                                                text = row.status,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "${row.scoredMark} / ${row.maxMark}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.weight(0.9f)
                                                    )
                                                    Text(
                                                        text = "${row.weightageMark} / ${row.weightagePercent}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        textAlign = TextAlign.End,
                                                        modifier = Modifier.weight(0.8f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isLoading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GradeBellCurveChart(
    mean: Double,
    sd: Double,
    studentScore: Double,
    studentGrade: String,
    cutoffs: List<GradeRangeCutoff>
) {
    val curveStrokeColor = MaterialTheme.colorScheme.primary
    val meanLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val studentColor = getGradeColor(studentGrade)

    val minX = minOf(0.0, mean - 3.2 * sd, studentScore - 5.0).coerceAtLeast(0.0)
    val maxX = maxOf(100.0, mean + 3.2 * sd, studentScore + 5.0).coerceAtMost(100.0)
    val span = (maxX - minX).coerceAtLeast(1.0)

    fun pdf(x: Double): Double {
        val z = (x - mean) / sd
        return exp(-0.5 * z * z)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
        ) {
            val padX = 12.dp.toPx()
            val padTop = 18.dp.toPx()
            val padBottom = 10.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - padTop - padBottom
            val baseY = padTop + h

            fun xToPx(xVal: Double): Float = padX + (((xVal - minX) / span).toFloat().coerceIn(0f, 1f)) * w
            fun yToPx(yNorm: Double): Float = baseY - (yNorm.toFloat().coerceIn(0f, 1f)) * h

            // Shaded grade intervals under the bell curve
            val orderedGrades = listOf("F", "E", "D", "C", "B", "A", "S")
            orderedGrades.forEach { g ->
                val c = cutoffs.find { it.grade == g } ?: return@forEach
                val segStart = (c.minInclusive ?: minX).coerceIn(minX, maxX)
                val segEnd = (c.maxExclusive ?: maxX).coerceIn(minX, maxX)
                if (segEnd > segStart) {
                    val steps = 28
                    val fillPath = Path().apply {
                        moveTo(xToPx(segStart), baseY)
                        for (s in 0..steps) {
                            val xv = segStart + (segEnd - segStart) * (s.toDouble() / steps)
                            lineTo(xToPx(xv), yToPx(pdf(xv)))
                        }
                        lineTo(xToPx(segEnd), baseY)
                        close()
                    }
                    val alpha = if (g.equals(studentGrade, ignoreCase = true)) 0.34f else 0.14f
                    drawPath(fillPath, color = getGradeColor(g).copy(alpha = alpha))
                }
            }

            // Bell curve outline
            val totalSteps = 120
            val curvePath = Path().apply {
                for (s in 0..totalSteps) {
                    val xv = minX + span * (s.toDouble() / totalSteps)
                    val px = xToPx(xv)
                    val py = yToPx(pdf(xv))
                    if (s == 0) moveTo(px, py) else lineTo(px, py)
                }
            }
            drawPath(
                path = curvePath,
                color = curveStrokeColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Baseline
            drawLine(
                color = meanLineColor.copy(alpha = 0.25f),
                start = Offset(padX, baseY),
                end = Offset(size.width - padX, baseY),
                strokeWidth = 1.dp.toPx()
            )

            // Mean dashed vertical line
            val meanPx = xToPx(mean)
            drawLine(
                color = meanLineColor.copy(alpha = 0.7f),
                start = Offset(meanPx, padTop),
                end = Offset(meanPx, baseY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )

            // Student score vertical marker & dot
            val scorePx = xToPx(studentScore)
            val scorePy = yToPx(pdf(studentScore))
            drawLine(
                color = studentColor,
                start = Offset(scorePx, padTop - 4.dp.toPx()),
                end = Offset(scorePx, baseY),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(
                color = studentColor,
                radius = 5.dp.toPx(),
                center = Offset(scorePx, scorePy)
            )
        }

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).background(meanLineColor, CircleShape))
                Text(
                    text = "Mean: ${String.format(Locale.US, "%.1f", mean)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).background(studentColor, CircleShape))
                Text(
                    text = "You: ${formatVal(studentScore)} ($studentGrade)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = studentColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun CGPACalculatorSubView(
    historySummary: CGPASummary?,
    historyData: List<GradeHistoryItem>,
    marksData: List<CourseMark>,
    timetable: TimetableModel?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var resolvedTimetable by remember(timetable) { mutableStateOf(timetable) }

    val isSyncing by SyncManager.isSyncing.collectAsState()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isSyncing,
        onRefresh = { SyncManager.performSync(context, priorityTab = "MARKS") }
    )

    val attemptVersion by CourseAttemptStore.version
    val attemptsMap = remember(historyData, attemptVersion) {
        CourseAttemptStore.getAll(context)
    }
    val semesterNameMap = remember(historyData, attemptsMap) {
        buildSemesterNameMap(context, historyData, attemptsMap)
    }

    val initialCurrentCourses = remember(resolvedTimetable, marksData) {
        buildCurrentSemesterCourses(resolvedTimetable, marksData)
    }

    var plannedCourses by remember { mutableStateOf(initialCurrentCourses) }
    val reportGradeOverrides = remember { mutableStateMapOf<Int, String>() }
    val otherAttemptGradeOverrides = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(Unit) {
        val hasReg = !resolvedTimetable?.registeredCourses.isNullOrEmpty()
        if (!hasReg) {
            val client = SessionManager.getSyncClient()
            val semId = Vault.getSelectedSemester(context)[0] ?: ""
            if (client != null && semId.isNotBlank()) {
                val parsed = withContext(Dispatchers.IO) {
                    val html = client.fetchTimetableRawHtml(semId, null)
                    if (!html.isNullOrBlank()) TimetableParser.parse(html) else null
                }
                if (parsed != null && !parsed.registeredCourses.isNullOrEmpty()) {
                    TimetableRepository.update(context, parsed)
                    resolvedTimetable = parsed
                    plannedCourses = buildCurrentSemesterCourses(parsed, marksData)
                }
            }
        }
    }

    val scrapedCgpa = remember(historySummary, historyData) {
        historySummary?.cgpa?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: calculateSemesterGPA(historyData)
    }
    val scrapedCompletedCredits = remember(historySummary, historyData) {
        historySummary?.creditsEarned?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: historyData
                .filter { getGradePoints(it.grade) >= 0 }
                .sumOf { it.credits.toDoubleOrNull() ?: 0.0 }
    }

    var currentCgpaText by remember(scrapedCgpa) {
        mutableStateOf(String.format(Locale.US, "%.2f", scrapedCgpa))
    }
    var completedCreditsText by remember(scrapedCompletedCredits) {
        mutableStateOf(formatVal(scrapedCompletedCredits))
    }
    var isEditingBaseline by remember { mutableStateOf(false) }

    var targetCgpa by remember(scrapedCgpa) { mutableDoubleStateOf(scrapedCgpa) }
    var showTargetEditDialog by remember { mutableStateOf(false) }
    var targetInputText by remember { mutableStateOf("") }

    var editingCreditCourse by remember { mutableStateOf<PlannedCourseEntry?>(null) }
    var creditInputText by remember { mutableStateOf("") }

    var showAddCourseDialog by remember { mutableStateOf(false) }
    var newCourseTitle by remember { mutableStateOf("") }
    var newCourseCode by remember { mutableStateOf("") }
    var newCourseCredits by remember { mutableStateOf("3") }
    var newCourseGrade by remember { mutableStateOf("A") }

    val gradeOptions = listOf("S", "A", "B", "C", "D", "E", "F")
    val reportGradeOptions = listOf("S", "A", "B", "C", "D", "E", "F", "P")

    val effectiveBaselineCgpa = currentCgpaText.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: scrapedCgpa
    val effectiveCompletedCredits = completedCreditsText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: scrapedCompletedCredits

    // Build semester-wise transcript rows (every attempt placed into its own examMonth semester)
    val allReportRows = remember(
        historyData,
        attemptsMap,
        reportGradeOverrides.toMap(),
        otherAttemptGradeOverrides.toMap()
    ) {
        val rows = mutableListOf<ReportCourseRow>()
        historyData.forEachIndexed { effIdx, effItem ->
            val normCode = effItem.courseCode.trim().uppercase(Locale.ENGLISH)
            val attempts = attemptsMap[normCode]
                ?.sortedBy { parseExamMonth(it.examMonth) }
                .orEmpty()

            val effectiveGrade = reportGradeOverrides[effIdx] ?: effItem.grade
            val effectiveUpdated = effItem.copy(grade = effectiveGrade)

            if (attempts.size > 1) {
                attempts.forEachIndexed { attIdx, att ->
                    val isEffectiveRow = att.examMonth.equals(effItem.examMonth, ignoreCase = true)
                    if (isEffectiveRow) {
                        rows.add(
                            ReportCourseRow(
                                key = "eff_${effIdx}_$normCode",
                                effectiveIndex = effIdx,
                                item = effectiveUpdated
                            )
                        )
                    } else {
                        val rowKey = "att_${normCode}_${att.examMonth.trim()}_$attIdx"
                        val overriddenGrade = otherAttemptGradeOverrides[rowKey] ?: att.grade
                        rows.add(
                            ReportCourseRow(
                                key = rowKey,
                                effectiveIndex = null,
                                item = GradeHistoryItem(
                                    courseCode = att.courseCode,
                                    courseTitle = att.courseTitle.ifBlank { effItem.courseTitle },
                                    courseType = att.courseType,
                                    credits = att.credits,
                                    grade = overriddenGrade,
                                    examMonth = att.examMonth,
                                    courseDistribution = effItem.courseDistribution
                                )
                            )
                        )
                    }
                }
            } else {
                rows.add(
                    ReportCourseRow(
                        key = "eff_${effIdx}_$normCode",
                        effectiveIndex = effIdx,
                        item = effectiveUpdated
                    )
                )
            }
        }
        rows
    }

    val pastSemesters = remember(allReportRows) {
        allReportRows
            .groupBy { it.item.examMonth.ifBlank { "Unknown" } }
            .entries
            .sortedBy { parseExamMonth(it.key) }
            .map { (sem, list) ->
                val items = list.map { it.item }
                val totalCr = items.sumOf { it.credits.toDoubleOrNull() ?: 0.0 }
                val countedCr = items
                    .filter { getGradePoints(it.grade) >= 0 }
                    .sumOf { it.credits.toDoubleOrNull() ?: 0.0 }
                ReportSemester(
                    semester = sem,
                    courses = list,
                    gpa = calculateSemesterGPA(items),
                    totalCredits = totalCr,
                    countedCredits = countedCr
                )
            }
    }

    // CGPA calculation uses effective historical courses + current planned courses (no double counting)
    val calculation = remember(
        plannedCourses,
        effectiveBaselineCgpa,
        effectiveCompletedCredits,
        reportGradeOverrides.toMap(),
        historyData
    ) {
        var pastPoints = effectiveBaselineCgpa * effectiveCompletedCredits
        var pastGradedCredits = effectiveCompletedCredits

        reportGradeOverrides.forEach { (origIdx, newGrade) ->
            val orig = historyData.getOrNull(origIdx) ?: return@forEach
            val cr = orig.credits.toDoubleOrNull() ?: 0.0
            val oldPts = getGradePoints(orig.grade)
            val newPts = getGradePoints(newGrade)
            if (oldPts >= 0 && newPts >= 0) {
                pastPoints += (newPts - oldPts) * cr
            } else if (oldPts < 0 && newPts >= 0) {
                pastPoints += newPts * cr
                pastGradedCredits += cr
            } else if (oldPts >= 0) {
                pastPoints = (pastPoints - oldPts * cr).coerceAtLeast(0.0)
                pastGradedCredits = (pastGradedCredits - cr).coerceAtLeast(0.0)
            }
        }

        var plannedPoints = 0.0
        var plannedGradedCredits = 0.0

        plannedCourses.forEach { course ->
            val pts = getGradePoints(course.grade)
            if (pts >= 0 && course.credits > 0.0) {
                plannedPoints += pts * course.credits
                plannedGradedCredits += course.credits
            }
        }

        val semSgpa = if (plannedGradedCredits > 0.0) plannedPoints / plannedGradedCredits else 0.0
        val totalPoints = pastPoints + plannedPoints
        val totalCredits = pastGradedCredits + plannedGradedCredits
        val projectedCgpa = if (totalCredits > 0.0) totalPoints / totalCredits else 0.0

        Quadruple(projectedCgpa, semSgpa, totalCredits, Triple(pastPoints, pastGradedCredits, plannedGradedCredits))
    }

    val (projectedCgpa, semesterSgpa, totalGradedCredits, pointStats) = calculation
    val (pastPoints, pastGradedCredits, plannedGradedCredits) = pointStats
    val cgpaDelta = projectedCgpa - effectiveBaselineCgpa

    val chartSemesters = remember(pastSemesters, plannedCourses, semesterSgpa, plannedGradedCredits) {
        val list = pastSemesters.toMutableList()
        if (plannedCourses.isNotEmpty()) {
            val totalPlannedCr = plannedCourses.sumOf { it.credits }
            list.add(
                ReportSemester(
                    semester = "Current",
                    courses = emptyList(),
                    gpa = semesterSgpa,
                    totalCredits = totalPlannedCr,
                    countedCredits = plannedGradedCredits
                )
            )
        }
        list
    }

    val gradeDistribution = remember(allReportRows, plannedCourses) {
        val order = listOf("S", "A", "B", "C", "D", "E", "F", "P")
        val allGrades = allReportRows.map { it.item.grade.trim().uppercase(Locale.ENGLISH) } +
                plannedCourses.map { it.grade.trim().uppercase(Locale.ENGLISH) }
        allGrades
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedBy { order.indexOf(it.key).takeIf { i -> i >= 0 } ?: 99 }
            .map { it.key to it.value }
    }

    val hasChartsSection = chartSemesters.isNotEmpty() || gradeDistribution.isNotEmpty()
    val chartsItemCount = (if (hasChartsSection) 1 else 0) +
            (if (chartSemesters.isNotEmpty()) 2 else 0) +
            (if (gradeDistribution.isNotEmpty()) 1 else 0)
    val pastSemestersHeaderIndex = 2 + chartsItemCount + 1 + plannedCourses.size + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "CGPA Calculator",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    plannedCourses = buildCurrentSemesterCourses(resolvedTimetable, marksData)
                    currentCgpaText = String.format(Locale.US, "%.2f", scrapedCgpa)
                    completedCreditsText = formatVal(scrapedCompletedCredits)
                    isEditingBaseline = false
                    targetCgpa = scrapedCgpa
                    reportGradeOverrides.clear()
                    otherAttemptGradeOverrides.clear()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Reset", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Hero Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                val ringProgress = (semesterSgpa / 10.0).toFloat().coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier.size(96.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { 1f },
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                                        strokeWidth = 7.dp
                                    )
                                    CircularProgressIndicator(
                                        progress = { ringProgress },
                                        modifier = Modifier.fillMaxSize(),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 7.dp
                                    )
                                    Text(
                                        text = if (plannedGradedCredits > 0.0) String.format(Locale.US, "%.2f", semesterSgpa) else "-",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Semester GPA",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        )
                                        Text(
                                            text = "${plannedCourses.size} ${if (plannedCourses.size == 1) "course" else "courses"} · ${formatVal(plannedGradedCredits)} credits",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Projected CGPA (${formatVal(totalGradedCredits)} credits)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = String.format(Locale.US, "%.2f", effectiveBaselineCgpa),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                            )
                                            Text(
                                                text = "→",
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.2f", projectedCgpa),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            if (kotlin.math.abs(cgpaDelta) >= 0.005) {
                                                val isPositive = cgpaDelta > 0
                                                val deltaColor = if (isPositive) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                                Surface(
                                                    color = deltaColor.copy(alpha = 0.18f),
                                                    shape = RoundedCornerShape(50)
                                                ) {
                                                    Text(
                                                        text = String.format(Locale.US, "%+.2f", cgpaDelta),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = deltaColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CGPA ${String.format(Locale.US, "%.2f", effectiveBaselineCgpa)} over ${formatVal(effectiveCompletedCredits)} credits",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (isEditingBaseline) "Done" else "Edit",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isEditingBaseline = !isEditingBaseline }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (isEditingBaseline) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = currentCgpaText,
                                        onValueChange = { raw ->
                                            if (raw.length <= 5 && raw.all { it.isDigit() || it == '.' }) {
                                                currentCgpaText = raw
                                            }
                                        },
                                        label = { Text("Current CGPA", fontSize = 12.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = completedCreditsText,
                                        onValueChange = { raw ->
                                            if (raw.length <= 6 && raw.all { it.isDigit() || it == '.' }) {
                                                completedCreditsText = raw
                                            }
                                        },
                                        label = { Text("Completed credits", fontSize = 12.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Target CGPA Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target CGPA",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            targetInputText = String.format(Locale.US, "%.2f", targetCgpa)
                                            showTargetEditDialog = true
                                        }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.2f", targetCgpa),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Target CGPA",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (plannedGradedCredits > 0.0) {
                                val requiredSgpa = ((targetCgpa * (pastGradedCredits + plannedGradedCredits)) - pastPoints) / plannedGradedCredits
                                val clampedDisplay = if (requiredSgpa <= 0.0) 0.0 else requiredSgpa
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Needs a ",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.2f", clampedDisplay),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (requiredSgpa > 10.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = " semester GPA",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Analytics Charts + "View more" button
                if (hasChartsSection) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Analytics",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (pastSemesters.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(pastSemestersHeaderIndex)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "View more",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Scroll to past courses",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (chartSemesters.isNotEmpty()) {
                        item {
                            ReportChartCard(title = "GPA Trend by Semester") {
                                GpaTrendChart(semesters = chartSemesters)
                            }
                        }
                        item {
                            ReportChartCard(title = "Credits by Semester") {
                                CreditsBySemesterChart(semesters = chartSemesters)
                            }
                        }
                    }

                    if (gradeDistribution.isNotEmpty()) {
                        item {
                            ReportChartCard(title = "Grade Distribution") {
                                GradeDistributionDonutChart(distribution = gradeDistribution)
                            }
                        }
                    }
                }

                // 4. Courses Header
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Courses",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(
                            onClick = {
                                plannedCourses = buildCurrentSemesterCourses(resolvedTimetable, marksData)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Current semester",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 5. Course Planner Cards
                items(plannedCourses, key = { it.id }) { course ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = course.courseTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 10.dp)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            plannedCourses = plannedCourses.filterNot { it.id == course.id }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove course",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = course.courseCode.ifBlank { "Custom" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                val updatedCr = (course.credits - 0.5).coerceAtLeast(0.5)
                                                plannedCourses = plannedCourses.map {
                                                    if (it.id == course.id) it.copy(credits = updatedCr) else it
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "−",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                editingCreditCourse = course
                                                creditInputText = formatVal(course.credits)
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${formatVal(course.credits)} cr",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit credits",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                val updatedCr = (course.credits + 0.5).coerceAtMost(20.0)
                                                plannedCourses = plannedCourses.map {
                                                    if (it.id == course.id) it.copy(credits = updatedCr) else it
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "+",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                gradeOptions.forEach { g ->
                                    val isSelected = course.grade.equals(g, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                            )
                                            .clickable {
                                                plannedCourses = plannedCourses.map {
                                                    if (it.id == course.id) it.copy(grade = g) else it
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = g,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Add Custom Course Button
                item {
                    OutlinedButton(
                        onClick = {
                            newCourseTitle = ""
                            newCourseCode = ""
                            newCourseCredits = "3"
                            newCourseGrade = "A"
                            showAddCourseDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Course", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // 7. Interactive Past Semesters Breakdown (Semester-wise transcript)
                if (pastSemesters.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                        Text(
                            text = "Past Semesters",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    pastSemesters.forEach { sem ->
                        item(key = "rep_sem_${sem.semester}") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val mappedSem = semesterNameMap[sem.semester]
                                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                            if (mappedSem != null) {
                                                Text(
                                                    text = mappedSem,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${sem.semester} · ${sem.courses.size} courses",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                    text = sem.semester,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${sem.courses.size} courses",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = String.format(Locale.US, "%.2f", sem.gpa),
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Semester GPA",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    sem.courses.forEachIndexed { rowIdx, reportRow ->
                                        if (rowIdx > 0) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                        }
                                        val course = reportRow.item
                                        var gradeMenuExpanded by remember { mutableStateOf(false) }
                                        val pts = getGradePoints(course.grade)
                                        val gColor = getGradeColor(course.grade)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = course.courseTitle.ifBlank { course.courseCode },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${course.courseCode} · ${course.credits} cr · ${if (pts < 0) "N/A" else "$pts pts"}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Box {
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(gColor.copy(alpha = 0.14f))
                                                        .border(1.dp, gColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                                        .clickable { gradeMenuExpanded = true }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = course.grade,
                                                        color = gColor,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "Change grade",
                                                        tint = gColor,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = gradeMenuExpanded,
                                                    onDismissRequest = { gradeMenuExpanded = false }
                                                ) {
                                                    reportGradeOptions.forEach { g ->
                                                        val gPts = getGradePoints(g)
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text(
                                                                    text = if (g == "P") "P (Pass)" else "$g ($gPts)",
                                                                    fontWeight = if (g == course.grade) FontWeight.Bold else FontWeight.Normal,
                                                                    color = if (g == course.grade) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                                )
                                                            },
                                                            onClick = {
                                                                val effIdx = reportRow.effectiveIndex
                                                                if (effIdx != null) {
                                                                    if (g == historyData[effIdx].grade) {
                                                                        reportGradeOverrides.remove(effIdx)
                                                                    } else {
                                                                        reportGradeOverrides[effIdx] = g
                                                                    }
                                                                } else {
                                                                    otherAttemptGradeOverrides[reportRow.key] = g
                                                                }
                                                                gradeMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isSyncing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showTargetEditDialog) {
        AlertDialog(
            onDismissRequest = { showTargetEditDialog = false },
            title = { Text("Edit Target CGPA", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = targetInputText,
                    onValueChange = { raw ->
                        if (raw.length <= 5 && raw.all { it.isDigit() || it == '.' }) {
                            targetInputText = raw
                        }
                    },
                    label = { Text("Target CGPA (0.00 - 10.00)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = targetInputText.toDoubleOrNull()
                        if (parsed != null && parsed in 0.0..10.0) {
                            targetCgpa = (parsed * 100.0).roundToInt() / 100.0
                        }
                        showTargetEditDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetEditDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    editingCreditCourse?.let { target ->
        AlertDialog(
            onDismissRequest = { editingCreditCourse = null },
            title = { Text("Edit Credits", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${target.courseCode} · ${target.courseTitle}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = creditInputText,
                        onValueChange = { raw ->
                            if (raw.length <= 5 && raw.all { it.isDigit() || it == '.' }) {
                                creditInputText = raw
                            }
                        },
                        label = { Text("Credits (e.g. 4 or 3.5)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = creditInputText.toDoubleOrNull()
                        if (parsed != null && parsed > 0.0 && parsed <= 20.0) {
                            plannedCourses = plannedCourses.map {
                                if (it.id == target.id) it.copy(credits = parsed) else it
                            }
                        }
                        editingCreditCourse = null
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCreditCourse = null }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showAddCourseDialog) {
        AlertDialog(
            onDismissRequest = { showAddCourseDialog = false },
            title = { Text("Add Course", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCourseTitle,
                        onValueChange = { newCourseTitle = it },
                        label = { Text("Course Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newCourseCode,
                            onValueChange = { newCourseCode = it.uppercase(Locale.ENGLISH) },
                            label = { Text("Code (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newCourseCredits,
                            onValueChange = { raw ->
                                if (raw.length <= 5 && raw.all { it.isDigit() || it == '.' }) {
                                    newCourseCredits = raw
                                }
                            },
                            label = { Text("Credits") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        gradeOptions.forEach { g ->
                            val isSelected = newCourseGrade == g
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { newCourseGrade = g },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = g,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newCourseTitle.isNotBlank() && (newCourseCredits.toDoubleOrNull() ?: 0.0) > 0.0,
                    onClick = {
                        val cr = newCourseCredits.toDoubleOrNull() ?: 3.0
                        plannedCourses = plannedCourses + PlannedCourseEntry(
                            id = "custom_${System.currentTimeMillis()}",
                            courseCode = newCourseCode.trim(),
                            courseTitle = newCourseTitle.trim(),
                            credits = cr,
                            grade = newCourseGrade
                        )
                        showAddCourseDialog = false
                    }
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCourseDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}


@Composable
private fun ReportChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun GpaTrendChart(semesters: List<ReportSemester>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val padX = 16.dp.toPx()
            val padY = 12.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - padY * 2

            listOf(0f, 0.5f, 1f).forEach { frac ->
                val y = padY + h * frac
                drawLine(gridColor, Offset(padX, y), Offset(size.width - padX, y), strokeWidth = 1.dp.toPx())
            }

            val pts = semesters.mapIndexed { idx, sem ->
                val x = if (semesters.size == 1) padX + w / 2f
                else padX + (w * idx / (semesters.size - 1).toFloat())
                val y = padY + h * (1f - (sem.gpa / 10.0).toFloat().coerceIn(0f, 1f))
                Offset(x, y)
            }

            if (pts.size > 1) {
                val path = Path().apply {
                    moveTo(pts.first().x, pts.first().y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
                drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }

            pts.forEach { pt ->
                drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = pt)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            semesters.forEach { sem ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format(Locale.US, "%.2f", sem.gpa),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = sem.semester.substringBefore("-"),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CreditsBySemesterChart(semesters: List<ReportSemester>) {
    val maxCr = (semesters.maxOfOrNull { it.totalCredits } ?: 1.0).coerceAtLeast(1.0)
    val countedColor = Color(0xFF22C55E)
    val totalColor = Color(0xFF06B6D4)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(countedColor, CircleShape))
                Text("Counted", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(totalColor.copy(alpha = 0.7f), CircleShape))
                Text("Total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val groupW = size.width / semesters.size.coerceAtLeast(1)
            val barW = (groupW * 0.28f).coerceAtMost(22.dp.toPx())
            val gap = 4.dp.toPx()

            semesters.forEachIndexed { idx, sem ->
                val centerX = groupW * idx + groupW / 2f
                val countedH = ((sem.countedCredits / maxCr).toFloat().coerceIn(0f, 1f)) * size.height
                val totalH = ((sem.totalCredits / maxCr).toFloat().coerceIn(0f, 1f)) * size.height

                drawRoundRect(
                    color = countedColor,
                    topLeft = Offset(centerX - barW - gap / 2f, size.height - countedH),
                    size = Size(barW, countedH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = totalColor.copy(alpha = 0.7f),
                    topLeft = Offset(centerX + gap / 2f, size.height - totalH),
                    size = Size(barW, totalH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            semesters.forEach { sem ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${formatVal(sem.countedCredits)}/${formatVal(sem.totalCredits)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = sem.semester.substringBefore("-"),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GradeDistributionDonutChart(distribution: List<Pair<String, Int>>) {
    val total = distribution.sumOf { it.second }.coerceAtLeast(1)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val strokeW = 20.dp.toPx()
            val inset = strokeW / 2f
            val arcSize = Size(size.width - strokeW, size.height - strokeW)
            var startAngle = -90f

            distribution.forEach { (grade, count) ->
                val sweep = (count.toFloat() / total.toFloat()) * 360f
                val gap = if (distribution.size > 1) 2.5f else 0f
                drawArc(
                    color = getGradeColor(grade),
                    startAngle = startAngle,
                    sweepAngle = (sweep - gap).coerceAtLeast(1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeW)
                )
                startAngle += sweep
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            distribution.forEach { (grade, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(getGradeColor(grade), CircleShape)
                        )
                        Text(
                            text = "Grade $grade",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "$count (${(count * 100) / total}%)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun CurrentSemesterMarksView(marksData: List<CourseMark>, mergeMarks: Boolean) {
    if (marksData.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No Marks data available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Pull down to refresh",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val uiMarks = remember(marksData, mergeMarks) {
            val list = mutableListOf<UiMark>()
            val grouped = marksData.groupBy { it.courseCode.trim() }

            grouped.forEach { (code, groupMarks) ->
                val eth = groupMarks.find {
                    val t = it.courseType.uppercase(Locale.getDefault())
                    t.contains("ETH") || t.contains("THEORY")
                }
                val elaOrEpj = groupMarks.find {
                    val t = it.courseType.uppercase(Locale.getDefault())
                    t.contains("ELA") || t.contains("EPJ") || t.contains("PJT") || t.contains("LAB") || t.contains("PROJECT")
                }

                if (mergeMarks && eth != null && elaOrEpj != null) {
                    val thTotals = getBestAttemptTotals(eth)
                    val pracTotals = getBestAttemptTotals(elaOrEpj)

                    val finalGained = (thTotals.first * 0.75) + (pracTotals.first * 0.25)
                    val finalMax = (thTotals.second * 0.75) + (pracTotals.second * 0.25)

                    val isLab = elaOrEpj.courseType.uppercase(Locale.getDefault()).contains("LAB") || elaOrEpj.courseType.uppercase(Locale.getDefault()).contains("ELA")
                    val pracName = if (isLab) "Lab" else "Project"

                    val thComponent = UiMarkComponent(
                        name = "Theory",
                        gainedRaw = thTotals.first,
                        totalRaw = thTotals.second,
                        details = eth.details.map { d -> UiMarkDetail(d.title, "${cleanMark(d.scoredMark)} / ${cleanMark(d.maxMark)}", "${cleanMark(d.weightageMark)} / ${cleanMark(d.weightagePercent)}") }
                    )

                    val pracComponent = UiMarkComponent(
                        name = pracName,
                        gainedRaw = pracTotals.first,
                        totalRaw = pracTotals.second,
                        details = elaOrEpj.details.map { d -> UiMarkDetail(d.title, "${cleanMark(d.scoredMark)} / ${cleanMark(d.maxMark)}", "${cleanMark(d.weightageMark)} / ${cleanMark(d.weightagePercent)}") }
                    )

                    list.add(
                        UiMark(
                            courseCode = code,
                            courseType = "Theory + $pracName",
                            courseTitle = eth.courseTitle,
                            gainedRaw = finalGained,
                            totalRaw = finalMax,
                            components = listOf(thComponent, pracComponent)
                        )
                    )

                    groupMarks.filter { it != eth && it != elaOrEpj }.forEach { remaining ->
                        val remTotals = getBestAttemptTotals(remaining)
                        val comp = UiMarkComponent(
                            name = remaining.courseType,
                            gainedRaw = remTotals.first,
                            totalRaw = remTotals.second,
                            details = remaining.details.map { d -> UiMarkDetail(d.title, "${cleanMark(d.scoredMark)} / ${cleanMark(d.maxMark)}", "${cleanMark(d.weightageMark)} / ${cleanMark(d.weightagePercent)}") }
                        )
                        list.add(
                            UiMark(
                                courseCode = code,
                                courseType = remaining.courseType,
                                courseTitle = remaining.courseTitle,
                                gainedRaw = remTotals.first,
                                totalRaw = remTotals.second,
                                components = listOf(comp)
                            )
                        )
                    }
                } else {
                    groupMarks.forEach { mark ->
                        val totals = getBestAttemptTotals(mark)
                        val comp = UiMarkComponent(
                            name = "Assessments",
                            gainedRaw = totals.first,
                            totalRaw = totals.second,
                            details = mark.details.map { d -> UiMarkDetail(d.title, "${cleanMark(d.scoredMark)} / ${cleanMark(d.maxMark)}", "${cleanMark(d.weightageMark)} / ${cleanMark(d.weightagePercent)}") }
                        )
                        list.add(
                            UiMark(
                                courseCode = code,
                                courseType = mark.courseType,
                                courseTitle = mark.courseTitle,
                                gainedRaw = totals.first,
                                totalRaw = totals.second,
                                components = listOf(comp)
                            )
                        )
                    }
                }
            }
            list.sortedWith(compareBy<UiMark> { it.courseCode }.thenBy { getCourseTypePriority(it.courseType) })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 130.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiMarks) { MarksExpandableCard(it) }
        }
    }
}

@Composable
fun MarksExpandableCard(mark: UiMark) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
    val gainedStr = formatVal(mark.gainedRaw)
    val totalStr = formatVal(mark.totalRaw)

    val barColor = getMarksColor(mark.gainedRaw, mark.totalRaw)
    val progress = if (mark.totalRaw > 0) (mark.gainedRaw / mark.totalRaw).toFloat().coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .animateContentSize(animationSpec = tween(300, easing = FastOutSlowInEasing)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${mark.courseCode} - ${mark.courseTitle}",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(gainedStr, color = barColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(" / $totalStr", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
                Box(modifier = Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(barColor))
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(rotation))
            }

            if (expanded) {
                val hasAnyDetails = mark.components.any { it.details.isNotEmpty() }

                if (hasAnyDetails) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(Modifier.height(16.dp))

                    mark.components.forEachIndexed { index, comp ->
                        if (comp.details.isNotEmpty()) {

                            if (mark.components.size > 1) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    Text(comp.name.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    val compColor = getMarksColor(comp.gainedRaw, comp.totalRaw)
                                    Text("${formatVal(comp.gainedRaw)} / ${formatVal(comp.totalRaw)}", color = compColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(6.dp))

                                val compProgress = if (comp.totalRaw > 0) (comp.gainedRaw / comp.totalRaw).toFloat().coerceIn(0f, 1f) else 0f
                                val compBarColor = getMarksColor(comp.gainedRaw, comp.totalRaw)
                                Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
                                    Box(modifier = Modifier.fillMaxWidth(compProgress).height(2.dp).clip(CircleShape).background(compBarColor))
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Mark Title", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("Scored / Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                    Text("Wgt / Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                }

                                comp.details.forEach { detail ->
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(detail.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text(detail.scoredMaxStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                        Text(detail.wgtMaxStr, color = barColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                    }
                                }
                            }

                            if (index < mark.components.lastIndex) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                    Text("No assessment details uploaded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun AcademicHistoryView(
    historySummary: CGPASummary?,
    effectiveHistoryData: List<GradeHistoryItem>,
    transcriptHistoryData: List<GradeHistoryItem>,
    groupedHistory: Map<String, List<GradeHistoryItem>>,
    expandedStates: MutableMap<String, Boolean>,
    onCourseClick: (GradeHistoryItem) -> Unit = {}
) {
    if (transcriptHistoryData.isEmpty()) {
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isDevOptionsEnabled = remember { CourseFileManager.isDevOptionsEnabled(context) }
    var isDownloadingPdf by remember { mutableStateOf(false) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    val listState = rememberLazyListState()

    val attemptVersion by CourseAttemptStore.version
    val attemptsMap = remember(effectiveHistoryData, attemptVersion) {
        CourseAttemptStore.getAll(context)
    }
    val semesterNameMap = remember(effectiveHistoryData, attemptsMap) {
        buildSemesterNameMap(context, effectiveHistoryData, attemptsMap)
    }

    val originalGpas = remember(groupedHistory) {
        groupedHistory.mapValues { calculateSemesterGPA(it.value) }
    }

    val formattedMonthsMap = remember(groupedHistory) {
        groupedHistory.keys.associateWith { examMonth ->
            examMonth.split("-").let { parts ->
                if (parts.size == 2) {
                    "${parts[0].trim().lowercase().replaceFirstChar { it.uppercase() }} ${parts[1].trim()}"
                } else examMonth
            }
        }
    }

    val filterOptions = remember(formattedMonthsMap, groupedHistory) {
        val sortedMonths = groupedHistory.keys.sortedByDescending { parseExamMonth(it) }
        listOf("All") + sortedMonths.mapNotNull { formattedMonthsMap[it] }
    }

    val filteredHistory = remember(transcriptHistoryData, searchQuery, selectedFilter, formattedMonthsMap) {
        transcriptHistoryData.filter { item ->
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isBlank() ||
                    item.courseCode.lowercase().contains(q) ||
                    item.courseTitle.lowercase().contains(q) ||
                    item.courseType.lowercase().contains(q) ||
                    item.grade.lowercase().contains(q) ||
                    item.credits.lowercase().contains(q)

            val itemFormattedMonth = formattedMonthsMap[item.examMonth.ifEmpty { "Unknown Semester" }] ?: ""
            val matchesFilter = selectedFilter == "All" || selectedFilter == itemFormattedMonth

            matchesSearch && matchesFilter
        }
    }

    val sortedAndGroupedHistory = remember(filteredHistory) {
        filteredHistory
            .sortedByDescending { parseExamMonth(it.examMonth) }
            .groupBy { it.examMonth.ifEmpty { "Unknown Semester" } }
    }

    val gradeCounts = remember(effectiveHistoryData) {
        val counts = mutableMapOf<String, Int>()
        effectiveHistoryData.forEach { item ->
            val grade = item.grade.trim().uppercase()
            counts[grade] = (counts[grade] ?: 0) + 1
        }
        val order = listOf("S", "A", "B", "C", "D", "E", "F", "N", "U")
        counts.entries
            .sortedBy { order.indexOf(it.key).takeIf { idx -> idx >= 0 } ?: 99 }
            .associate { it.key to it.value }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 130.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Theme-Aware Compact Dashboard Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("OVERALL CGPA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), letterSpacing = 1.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(historySummary?.cgpa ?: "0.00", fontSize = 36.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("CREDITS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f), letterSpacing = 1.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("${historySummary?.creditsEarned ?: "0"} / 160", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text("GRADE DISTRIBUTION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f), letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gradeCounts.entries.toList()) { (grade, count) ->
                            val gColor = getGradeColor(grade)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .border(1.dp, gColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(grade, color = gColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.width(6.dp))
                                Text(count.toString(), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Search Bar + Dev Options Grade_History.pdf Download Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(text = "Search courses...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )

                if (isDevOptionsEnabled) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !isDownloadingPdf) {
                                isDownloadingPdf = true
                                Toast.makeText(context, "Downloading Grade_History.pdf...", Toast.LENGTH_SHORT).show()
                                coroutineScope.launch {
                                    val result = downloadAndSaveGradeHistoryPdf(context)
                                    isDownloadingPdf = false
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Saved Grade_History.pdf to Downloads", Toast.LENGTH_SHORT).show()
                                        result.getOrNull()?.let { pdfUri ->
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(pdfUri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "No PDF viewer found", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        val err = result.exceptionOrNull()?.message ?: "Download failed"
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isDownloadingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Lucide.ArrowDownToLine,
                                    contentDescription = "Download Grade_History.pdf",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Dynamic Semester Filters
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { option ->
                    val isSelected = selectedFilter == option
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = option },
                        label = { Text(text = option, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.surface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }

        // 4. Grouped Semesters & Enhanced Course Rows
        sortedAndGroupedHistory.forEach { (examMonth, courses) ->
            val isExpanded = expandedStates[examMonth] ?: true
            val semGpa = originalGpas[examMonth] ?: 0.0
            val formattedMonth = formattedMonthsMap[examMonth] ?: examMonth

            item {
                val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "arrow_rotation_$examMonth")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { expandedStates[examMonth] = !isExpanded }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mappedSem = semesterNameMap[examMonth]
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = mappedSem ?: formattedMonth,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (mappedSem != null) {
                            Text(
                                text = formattedMonth,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "SGPA: %.2f", semGpa),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand/Collapse",
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isExpanded) {
                items(
                    items = courses,
                    key = { "${it.courseCode}_${it.examMonth}_${it.grade}" }
                ) { course ->
                    EnhancedHistoryItemCard(
                        course = course,
                        onClick = { onCourseClick(course) }
                    )
                }
            }
        }
    }
}

@Composable
fun EnhancedHistoryItemCard(
    course: GradeHistoryItem,
    onClick: () -> Unit = {}
) {
    val gradeColor = getGradeColor(course.grade)
    val isLabOrProject = course.courseType.contains("Lab", true) || course.courseType.contains("Project", true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLabOrProject) Lucide.Calculator else Lucide.BookOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = course.courseTitle.ifEmpty { course.courseCode },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${course.courseCode} • ${course.credits} Credits",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(gradeColor.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .border(1.5.dp, gradeColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .size(42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = course.grade,
                            color = gradeColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View Grade Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}