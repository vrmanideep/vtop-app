package com.vtop.ui.screens.main

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.*
import com.google.gson.Gson
import com.vtop.models.*
import com.vtop.utils.AnalyticsManager
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration

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

private fun getGradeColor(grade: String?): Color {
    return when (grade?.trim()?.uppercase(Locale.getDefault())) {
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

private fun getCourseTypePriority(type: String?): Int {
    val t = type?.uppercase(Locale.getDefault()) ?: ""
    return when {
        t.contains("TH") || t.contains("ETH") || t.contains("THEORY") -> 0
        t.contains("LO") || t.contains("ELA") || t.contains("LAB") -> 1
        t.contains("PJT") || t.contains("EPJ") || t.contains("PROJECT") -> 2
        else -> 3
    }
}

private fun getBestAttemptTotals(mark: CourseMark): Pair<Double, Double> {
    val detailsList = mark.details
    if (detailsList.isNullOrEmpty()) {
        return Pair(mark.totalWeightageMark ?: 0.0, mark.totalWeightagePercent ?: 0.0)
    }
    val groups = detailsList.groupBy { detail ->
        detail.title?.replace("Re Evaluation ", "", ignoreCase = true)?.trim()?.uppercase() ?: ""
    }
    var totalGained = 0.0
    var totalMax = 0.0
    groups.forEach { (_, detailsInGroup) ->
        val bestAttempt = detailsInGroup.maxByOrNull { it.weightageMark ?: 0.0 }
        totalGained += bestAttempt?.weightageMark ?: 0.0
        totalMax += bestAttempt?.weightagePercent ?: 0.0
    }
    return Pair(totalGained, totalMax)
}

private fun getGradePoints(grade: String?): Int {
    return when (grade?.trim()?.uppercase(Locale.getDefault())) {
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
            val cred = course.credits?.toDoubleOrNull() ?: 0.0
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
    } catch (e: Exception) {
        YearMonth.of(1900, 1)
    }
}

@Composable
fun Marks(
    marksData: List<CourseMark>,
    historySummary: CGPASummary?,
    historyData: List<GradeHistoryItem>,
    onHistoryLoad: () -> Unit
) {
    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Marks_Screen")
    }
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    val mergeMarks = sharedPrefs.getBoolean("MERGE_MARKS", true)

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    val groupedHistory = remember(historyData) { historyData.groupBy { it.examMonth ?: "Unknown Semester" } }
    val expandedStates = remember(groupedHistory) {
        androidx.compose.runtime.mutableStateMapOf<String, Boolean>().apply {
            groupedHistory.keys.forEach { this[it] = true }
        }
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
                onClick = {
                    try {
                        val payload = mapOf(
                            "credits_registered" to (historySummary?.creditsRegistered ?: "0"),
                            "credits_earned" to (historySummary?.creditsEarned ?: "0"),
                            "cgpa" to (historySummary?.cgpa ?: "0"),
                            "courses" to historyData.mapIndexed { index, item ->
                                mapOf(
                                    "id" to index,
                                    "course_code" to (item.courseCode ?: ""),
                                    "course_title" to (item.courseTitle ?: ""),
                                    "course_type" to (item.courseType ?: ""),
                                    "credits" to (item.credits?.toString()?.toDoubleOrNull()?.toInt() ?: 0),
                                    "grade" to (item.grade ?: ""),
                                    "exam_month" to (item.examMonth ?: ""),
                                    "course_distribution" to (item.courseDistribution ?: "")
                                )
                            }
                        )

                        val jsonString = Gson().toJson(payload)
                        val bytes = jsonString.toByteArray(Charsets.UTF_8)
                        val baos = ByteArrayOutputStream()
                        GZIPOutputStream(baos).use { gzip -> gzip.write(bytes) }

                        val encodedData = Base64.encodeToString(
                            baos.toByteArray(),
                            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                        )

                        val url = "https://cgpa-calculator-vitap.vercel.app/api/app?data=$encodedData"

                        val customTabsIntent = CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()

                        customTabsIntent.launchUrl(context, Uri.parse(url))

                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to launch calculator", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                },
                containerColor = MarksPrimaryAccent,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Lucide.Calculator, contentDescription = "CGPA Calculator", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun CurrentSemesterMarksView(marksData: List<CourseMark>, mergeMarks: Boolean) {
    if (marksData.isEmpty()) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Text("No Marks Data Available", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    } else {
        val uiMarks = remember(marksData, mergeMarks) {
            val list = mutableListOf<UiMark>()
            val grouped = marksData.groupBy { it.courseCode?.trim() ?: "" }

            grouped.forEach { (code, groupMarks) ->
                val eth = groupMarks.find {
                    val t = it.courseType?.uppercase(Locale.getDefault()) ?: ""
                    t.contains("ETH") || t.contains("THEORY")
                }
                val elaOrEpj = groupMarks.find {
                    val t = it.courseType?.uppercase(Locale.getDefault()) ?: ""
                    t.contains("ELA") || t.contains("EPJ") || t.contains("PJT") || t.contains("LAB") || t.contains("PROJECT")
                }

                if (mergeMarks && eth != null && elaOrEpj != null) {
                    val thTotals = getBestAttemptTotals(eth)
                    val pracTotals = getBestAttemptTotals(elaOrEpj)

                    val finalGained = (thTotals.first * 0.75) + (pracTotals.first * 0.25)
                    val finalMax = (thTotals.second * 0.75) + (pracTotals.second * 0.25)

                    val isLab = elaOrEpj.courseType?.uppercase(Locale.getDefault())?.contains("LAB") == true || elaOrEpj.courseType?.uppercase(Locale.getDefault())?.contains("ELA") == true
                    val pracName = if (isLab) "Lab" else "Project"

                    val thComponent = UiMarkComponent(
                        name = "Theory",
                        gainedRaw = thTotals.first,
                        totalRaw = thTotals.second,
                        details = eth.details?.map { d -> UiMarkDetail(d.title ?: "", "${d.scoredMark ?: "--"} / ${d.maxMark ?: "--"}", "${d.weightageMark ?: "--"} / ${d.weightagePercent ?: "--"}") } ?: emptyList()
                    )

                    val pracComponent = UiMarkComponent(
                        name = pracName,
                        gainedRaw = pracTotals.first,
                        totalRaw = pracTotals.second,
                        details = elaOrEpj.details?.map { d -> UiMarkDetail(d.title ?: "", "${d.scoredMark ?: "--"} / ${d.maxMark ?: "--"}", "${d.weightageMark ?: "--"} / ${d.weightagePercent ?: "--"}") } ?: emptyList()
                    )

                    list.add(
                        UiMark(
                            courseCode = code,
                            courseType = "Theory + $pracName",
                            courseTitle = eth.courseTitle ?: "",
                            gainedRaw = finalGained,
                            totalRaw = finalMax,
                            components = listOf(thComponent, pracComponent)
                        )
                    )

                    groupMarks.filter { it != eth && it != elaOrEpj }.forEach { remaining ->
                        val remTotals = getBestAttemptTotals(remaining)
                        val comp = UiMarkComponent(
                            name = remaining.courseType ?: "Assessments",
                            gainedRaw = remTotals.first,
                            totalRaw = remTotals.second,
                            details = remaining.details?.map { d -> UiMarkDetail(d.title ?: "", "${d.scoredMark ?: "--"} / ${d.maxMark ?: "--"}", "${d.weightageMark ?: "--"} / ${d.weightagePercent ?: "--"}") } ?: emptyList()
                        )
                        list.add(
                            UiMark(
                                courseCode = code,
                                courseType = remaining.courseType ?: "",
                                courseTitle = remaining.courseTitle ?: "",
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
                            details = mark.details?.map { d -> UiMarkDetail(d.title ?: "", "${d.scoredMark ?: "--"} / ${d.maxMark ?: "--"}", "${d.weightageMark ?: "--"} / ${d.weightagePercent ?: "--"}") } ?: emptyList()
                        )
                        list.add(
                            UiMark(
                                courseCode = code,
                                courseType = mark.courseType ?: "",
                                courseTitle = mark.courseTitle ?: "",
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

    val gainedRounded = mark.gainedRaw.roundToInt()
    val totalRounded = mark.totalRaw.roundToInt()

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(mark.courseCode, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(mark.courseType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(gainedRounded.toString(), color = barColor, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(" / $totalRounded", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
                Box(modifier = Modifier.fillMaxWidth(progress).height(3.dp).clip(CircleShape).background(barColor))
            }
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(mark.courseTitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
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
                                    Text("${comp.gainedRaw.roundToInt()} / ${comp.totalRaw.roundToInt()}", color = compColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(6.dp))

                                val compProgress = if (comp.totalRaw > 0) (comp.gainedRaw / comp.totalRaw).toFloat().coerceIn(0f, 1f) else 0f
                                val compBarColor = getMarksColor(comp.gainedRaw, comp.totalRaw)
                                Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
                                    Box(modifier = Modifier.fillMaxWidth(compProgress).height(2.dp).clip(CircleShape).background(compBarColor))
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Assessment", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Scored / Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("Wgt / Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                            }
                            Spacer(Modifier.height(8.dp))

                            comp.details.forEach { detail ->
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(detail.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(detail.scoredMaxStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                    Text(detail.wgtMaxStr, color = barColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
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

    val filterOptions = remember(formattedMonthsMap) {
        listOf("All") + groupedHistory.keys.mapNotNull { formattedMonthsMap[it] }.sortedDescending()
    }

    val filteredHistory = remember(historyData, searchQuery, selectedFilter, formattedMonthsMap) {
        historyData.filter { item ->
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isBlank() ||
                    item.courseCode.orEmpty().lowercase().contains(q) ||
                    item.courseTitle.orEmpty().lowercase().contains(q) ||
                    item.courseType.orEmpty().lowercase().contains(q) ||
                    item.grade.orEmpty().lowercase().contains(q) ||
                    item.credits.orEmpty().lowercase().contains(q)

            val itemFormattedMonth = formattedMonthsMap[item.examMonth] ?: ""
            val matchesFilter = selectedFilter == "All" || selectedFilter == itemFormattedMonth

            matchesSearch && matchesFilter
        }
    }

    val sortedAndGroupedHistory = remember(filteredHistory) {
        filteredHistory
            .sortedByDescending { parseExamMonth(it.examMonth ?: "Jan-1900") }
            .groupBy { it.examMonth ?: "Unknown Semester" }
    }

    // --- GRADE COUNTING LOGIC ---
    val gradeCounts = remember(historyData) {
        val counts = mutableMapOf<String, Int>()
        historyData.forEach { item ->
            val grade = item.grade?.trim()?.uppercase() ?: return@forEach
            counts[grade] = (counts[grade] ?: 0) + 1
        }
        // Sort in logical academic order
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
    )
    {
        // 1. Theme-Aware Compact Dashboard Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Top Row: CGPA and Credits
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

                    // Bottom Row: Grade Pill Ribbon
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
                    val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                    val border = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    val fg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .border(1.dp, border, RoundedCornerShape(12.dp))
                            .clickable { selectedFilter = option }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = option, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
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
    val isLabOrProject = course.courseType?.contains("Lab", true) == true || course.courseType?.contains("Project", true) == true

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
                            text = course.courseTitle ?: course.courseCode ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${course.courseCode} • ${course.credits ?: "0"} Credits",
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
                            text = course.grade ?: "-",
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