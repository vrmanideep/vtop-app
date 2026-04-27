package com.vtop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.models.AttendanceModel
import com.vtop.models.CGPASummary
import com.vtop.models.GradeHistoryItem

@Composable
fun VtopAnalyticsTab(
    attendanceData: List<AttendanceModel>,
    historySummary: CGPASummary?,
    historyData: List<GradeHistoryItem>
) {
    val scrollState = rememberScrollState()

    fun formatCourseType(type: String?): String {
        val t = type?.uppercase() ?: ""
        return when {
            t.contains("TH") || t.contains("ETH") -> "Theory"
            t.contains("LO") || t.contains("ELA") || t.contains("LAB") -> "Lab"
            t.contains("PJT") || t.contains("EPJ") -> "Project"
            else -> "Theory"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (historySummary != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsStatCard(title = "Current CGPA", value = historySummary.cgpa ?: "--", modifier = Modifier.weight(1f), color = Color(0xFF4CAF50))
                AnalyticsStatCard(title = "Credits Earned", value = historySummary.creditsEarned ?: "--", modifier = Modifier.weight(1f), color = Color(0xFF29B6F6))
            }
        }

        if (attendanceData.isNotEmpty()) {
            val dangerZone = attendanceData
                .mapNotNull {
                    val p = it.attendancePercentage?.filter { char -> char.isDigit() }?.toIntOrNull()
                    if (p != null) Pair(it, p) else null
                }
                .sortedBy { it.second }
                .take(3)

            if (dangerZone.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("The Danger Zone", color = MaterialTheme.colorScheme.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Courses closest to the 75% threshold", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))

                        dangerZone.forEach { (course, percentage) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${course.courseCode} - ${formatCourseType(course.courseType)}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(course.courseName ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    "$percentage%",
                                    color = if (percentage < 75) MaterialTheme.colorScheme.error else if (percentage < 80) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            if (course != dangerZone.last().first) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        }
                    }
                }
            }
        }

        if (historyData.isNotEmpty()) {
            val gradeOrder = listOf("S", "A", "B", "C", "D", "E", "P", "F", "N")

            // STRICT FILTERING: Only grades with a count > 0 will exist here.
            val gradeCounts = remember(historyData) {
                gradeOrder.mapNotNull { grade ->
                    val count = historyData.count { it.grade?.trim()?.uppercase() == grade }
                    if (count > 0) grade to count else null
                }
            }

            if (gradeCounts.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Grade Distribution", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Historical performance across all semesters", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(32.dp))

                        val maxCount = gradeCounts.maxOf { it.second }.coerceAtLeast(1)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp) // Generous, stable fixed height
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            gradeCounts.forEach { (grade, count) ->
                                val barColor = when (grade) {
                                    "S" -> Color(0xFF4CAF50)
                                    "A" -> Color(0xFF2196F3)
                                    "B" -> Color(0xFF9C27B0)
                                    "P" -> Color(0xFF00BCD4)
                                    "F", "N" -> MaterialTheme.colorScheme.error
                                    else -> Color(0xFFFFC107)
                                }

                                // Proportional math for perfect rendering
                                val barHeightRatio = count.toFloat() / maxCount.toFloat()
                                val effectiveRatio = barHeightRatio.coerceAtLeast(0.05f)
                                val spaceRatio = 1f - effectiveRatio

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(1f) // Distributes horizontal space perfectly
                                ) {
                                    // 1. The Dynamic Drawing Area
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f) // Takes up all space EXCEPT the bottom label
                                            .fillMaxWidth()
                                    ) {
                                        // Invisible spacer pushes the bar down perfectly
                                        if (spaceRatio > 0f) {
                                            Spacer(modifier = Modifier.weight(spaceRatio))
                                        }

                                        // The Count number (floats right above the bar)
                                        Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(4.dp))

                                        // The Bar itself
                                        Box(
                                            modifier = Modifier
                                                .width(28.dp)
                                                .weight(effectiveRatio)
                                                .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        )
                                    }

                                    // 2. The Bottom Label Area (Isolated to prevent clipping)
                                    Spacer(Modifier.height(8.dp))
                                    Text(grade, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun AnalyticsStatCard(title: String, value: String, modifier: Modifier = Modifier, color: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}