package com.vtop.ui.screens.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.models.*
import kotlinx.coroutines.launch
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

// --- UI Models to handle Separated Component Data ---
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

// Helper to filter out Re-Evaluations and sum up the highest marks
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

@Composable
fun Marks(
    marksData: List<CourseMark>,
    historySummary: CGPASummary?,
    historyData: List<GradeHistoryItem>,
    onHistoryLoad: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            onHistoryLoad()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(50))
                .padding(4.dp)
        ) {
            TabPill("Marks", pagerState.currentPage == 0, Modifier.weight(1f)) {
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
            }
            TabPill("History", pagerState.currentPage == 1, Modifier.weight(1f)) {
                coroutineScope.launch { pagerState.animateScrollToPage(1) }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> CurrentSemesterMarksView(marksData)
                1 -> AcademicHistoryView(historyData, historySummary, onHistoryLoad)
            }
        }
    }
}

@Composable
fun TabPill(text: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent, label = "tabBg")
    val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabText")

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HeroSummaryCard(summary: CGPASummary) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = "CGPA", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(text = summary.cgpa ?: "--", color = MaterialTheme.colorScheme.onSurface, fontSize = 36.sp, fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "REGISTERED", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = summary.creditsRegistered ?: "--", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "EARNED", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = summary.creditsEarned ?: "--", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun CurrentSemesterMarksView(marksData: List<CourseMark>) {
    if (marksData.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Marks Data Available", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    } else {
        // --- Core Merging Logic (75% Theory + 25% Lab/Project) ---
        val uiMarks = remember(marksData) {
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

                if (eth != null && elaOrEpj != null) {
                    val thTotals = getBestAttemptTotals(eth)
                    val pracTotals = getBestAttemptTotals(elaOrEpj)

                    // The actual Math! Theory*0.75 + Lab/Proj*0.25
                    val finalGained = (thTotals.first * 0.75) + (pracTotals.first * 0.25)
                    val finalMax = (thTotals.second * 0.75) + (pracTotals.second * 0.25)

                    val isLab = elaOrEpj.courseType?.uppercase(Locale.getDefault())?.contains("LAB") == true || elaOrEpj.courseType?.uppercase(Locale.getDefault())?.contains("ELA") == true
                    val pracName = if (isLab) "Lab" else "Project"

                    // Create separate components for UI rendering
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

                    // Add any other anomalies directly (if a course somehow has ETH, ELA, *and* EPJ)
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
                    // Standard processing for non-embedded or standalone courses
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
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
            // Header
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

            // Expanded Assessment List broken down by Components
            if (expanded) {
                val hasAnyDetails = mark.components.any { it.details.isNotEmpty() }

                if (hasAnyDetails) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Spacer(Modifier.height(16.dp))

                    mark.components.forEachIndexed { index, comp ->
                        if (comp.details.isNotEmpty()) {

                            // Component Header (e.g. "Theory" or "Lab")
                            if (mark.components.size > 1) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    Text(comp.name.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    val compColor = getMarksColor(comp.gainedRaw, comp.totalRaw)
                                    Text("${comp.gainedRaw.roundToInt()} / ${comp.totalRaw.roundToInt()}", color = compColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(6.dp))

                                // Mini Progress bar for the specific component
                                val compProgress = if (comp.totalRaw > 0) (comp.gainedRaw / comp.totalRaw).toFloat().coerceIn(0f, 1f) else 0f
                                val compBarColor = getMarksColor(comp.gainedRaw, comp.totalRaw)
                                Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))) {
                                    Box(modifier = Modifier.fillMaxWidth(compProgress).height(2.dp).clip(CircleShape).background(compBarColor))
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            // Assessment Table Headers
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Assessment", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Scored / Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("Wgt / Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                            }
                            Spacer(Modifier.height(8.dp))

                            // Assessment Rows
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
fun AcademicHistoryView(historyData: List<GradeHistoryItem>, historySummary: CGPASummary?, onSyncClick: () -> Unit) {
    if (historyData.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No Academic History Available", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSyncClick, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync History", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val groupedHistory = remember(historyData) { historyData.groupBy { it.examMonth ?: "Unknown Semester" } }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (historySummary != null) item { HeroSummaryCard(historySummary) }
        groupedHistory.forEach { (examMonth, courses) ->
            item {
                Text(
                    text = examMonth.uppercase(Locale.getDefault()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(courses) { HistoryItemCard(it) }
        }
    }
}

@Composable
fun HistoryItemCard(course: GradeHistoryItem) {
    val gradeColor = getGradeColor(course.grade)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(course.courseCode ?: "", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(course.courseTitle ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Credits: ${course.credits ?: "--"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    Text(course.courseType ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .background(gradeColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, gradeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(course.grade ?: "--", color = gradeColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}