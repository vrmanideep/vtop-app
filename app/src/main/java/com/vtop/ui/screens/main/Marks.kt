@file:Suppress("SpellCheckingInspection")
package com.vtop.ui.screens.main

import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import com.vtop.core.SessionManager
import com.vtop.core.TimetableRepository
import com.vtop.logic.TimetableParser
import com.vtop.models.*
import com.vtop.utils.AnalyticsManager
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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

private fun buildCurrentSemesterCourses(
    timetable: TimetableModel?,
    marksData: List<CourseMark>
): List<PlannedCourseEntry> {
    // Note: Gson can leave new Java List fields null on legacy cached JSON
    val regList: List<TimetableModel.RegisteredCourse> = timetable?.registeredCourses ?: emptyList()
    if (regList.isNotEmpty()) {
        return regList
            .groupBy { it.courseCode.trim().uppercase(Locale.ENGLISH) }
            .values
            .mapIndexed { idx, components ->
                val first = components.first()
                // In VTOP's L T P J C table, C already holds exact component credits
                // (e.g. ETH = 3.0, ELA = 1.0 -> 4.0; PJT = 2.0 -> 2.0)
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

    // Fallback before timetable is re-synced with registeredCourses
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
    timetable: TimetableModel? = null,
    onOpenCalculator: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Marks_Screen")
    }
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    val mergeMarks = sharedPrefs.getBoolean("MERGE_MARKS", true)

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    var showCalculator by remember { mutableStateOf(false) }

    val groupedHistory = remember(historyData) { historyData.groupBy { it.examMonth.ifEmpty { "Unknown Semester" } } }
    val expandedStates = remember(groupedHistory) {
        mutableStateMapOf<String, Boolean>().apply {
            groupedHistory.keys.forEach { this[it] = true }
        }
    }

    if (showCalculator) {
        CGPACalculatorSubView(
            historySummary = historySummary,
            historyData = historyData,
            marksData = marksData,
            timetable = timetable,
            onBack = { showCalculator = false }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> CurrentSemesterMarksView(marksData, mergeMarks)
                1 -> AcademicHistoryView(historySummary, historyData, groupedHistory, expandedStates)
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
    var resolvedTimetable by remember(timetable) { mutableStateOf(timetable) }

    val initialCurrentCourses = remember(resolvedTimetable, marksData) {
        buildCurrentSemesterCourses(resolvedTimetable, marksData)
    }

    var plannedCourses by remember { mutableStateOf(initialCurrentCourses) }

    // Fetch & parse timetable once if cached model predates registeredCourses
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

    // Scraped baseline CGPA & completed credits from VTOP summary
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

    // Target CGPA defaults to current CGPA scraped from VTOP
    var targetCgpa by remember(scrapedCgpa) { mutableDoubleStateOf(scrapedCgpa) }
    var showTargetEditDialog by remember { mutableStateOf(false) }
    var targetInputText by remember { mutableStateOf("") }

    // Manual credit edit dialog state
    var editingCreditCourse by remember { mutableStateOf<PlannedCourseEntry?>(null) }
    var creditInputText by remember { mutableStateOf("") }

    // Add custom course dialog state
    var showAddCourseDialog by remember { mutableStateOf(false) }
    var newCourseTitle by remember { mutableStateOf("") }
    var newCourseCode by remember { mutableStateOf("") }
    var newCourseCredits by remember { mutableStateOf("3") }
    var newCourseGrade by remember { mutableStateOf("A") }

    val gradeOptions = listOf("S", "A", "B", "C", "D", "E", "F")

    val effectiveBaselineCgpa = currentCgpaText.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: scrapedCgpa
    val effectiveCompletedCredits = completedCreditsText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: scrapedCompletedCredits

    // Live GPA & CGPA calculations
    val calculation = remember(
        plannedCourses,
        effectiveBaselineCgpa,
        effectiveCompletedCredits
    ) {
        val pastPoints = effectiveBaselineCgpa * effectiveCompletedCredits
        val pastGradedCredits = effectiveCompletedCredits

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

    val projectedCgpa = calculation.first
    val semesterSgpa = calculation.second
    val totalGradedCredits = calculation.third
    val pastPoints = calculation.fourth.first
    val pastGradedCredits = calculation.fourth.second
    val plannedGradedCredits = calculation.fourth.third
    val cgpaDelta = projectedCgpa - effectiveBaselineCgpa

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

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Card (Semester GPA Ring + Projected CGPA + Editable Baseline)
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
                            // Circular Semester GPA Gauge
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

                            // Right Column: Semester GPA & Projected CGPA
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

                        // Baseline Row: CGPA X over Y credits + Edit / Done toggle
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

            // 2. Target CGPA Card (defaults to current VTOP CGPA + pencil edit icon, no +/- buttons)
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

            // 3. Courses Header + "Current semester" Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

            // 4. Course Planner Cards
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
                        // Top Row: Course Title + Remove (×) button
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
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
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

                        // Middle Row: Course Code + Credit Stepper (- | 4 cr [edit] | +)
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
                                // Minus (-) button
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

                                // Credits label + Edit pencil icon
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

                                // Plus (+) button
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

                        // Bottom Row: S A B C D E F Grade Pills
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

            // 5. Add Custom Course Button
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
        }
    }

    // Manual Target CGPA Edit Dialog
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

    // Manual Credit Edit Dialog
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

    // Add Custom Course Dialog
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
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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
    historyData: List<GradeHistoryItem>,
    groupedHistory: Map<String, List<GradeHistoryItem>>,
    expandedStates: MutableMap<String, Boolean>
) {
    if (historyData.isEmpty()) {
        return
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    val listState = rememberLazyListState()

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

    val filteredHistory = remember(historyData, searchQuery, selectedFilter, formattedMonthsMap) {
        historyData.filter { item ->
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

    val gradeCounts = remember(historyData) {
        val counts = mutableMapOf<String, Int>()
        historyData.forEach { item ->
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

        // 2. Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
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
                    Text(text = formattedMonth, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)

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
                items(courses) { course ->
                    EnhancedHistoryItemCard(course)
                }
            }
        }
    }
}

@Composable
fun EnhancedHistoryItemCard(course: GradeHistoryItem) {
    val gradeColor = getGradeColor(course.grade)
    val isLabOrProject = course.courseType.contains("Lab", true) || course.courseType.contains("Project", true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
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
                    modifier = Modifier.weight(1f).padding(end = 16.dp)
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

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                }
            }
        }
    }
}