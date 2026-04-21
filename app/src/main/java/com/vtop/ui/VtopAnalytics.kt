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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 1. High-Level Summary
        if (historySummary != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsStatCard(title = "Current CGPA", value = historySummary.cgpa, modifier = Modifier.weight(1f), color = Color(0xFF4CAF50))
                AnalyticsStatCard(title = "Credits Earned", value = historySummary.creditsEarned, modifier = Modifier.weight(1f), color = Color(0xFF29B6F6))
            }
        }

        // 2. The Danger Zone (Bottom 3 Attendance)
        if (attendanceData.isNotEmpty()) {
            val dangerZone = attendanceData
                .mapNotNull {
                    val p = it.attendancePercentage?.filter { char -> char.isDigit() }?.toIntOrNull()
                    if (p != null) Pair(it, p) else null
                }
                .sortedBy { it.second }
                .take(3)

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("The Danger Zone", color = Color(0xFFF44336), fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("Courses closest to the 75% threshold", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))

                    dangerZone.forEach { (course, percentage) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(course.courseCode ?: "N/A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(course.courseName ?: "", color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                "$percentage%",
                                color = if (percentage < 75) Color.Red else if (percentage < 80) Color(0xFFFFC107) else Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        if (course != dangerZone.last().first) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }

        // 3. Grade Distribution Chart (Custom Canvas)
        if (historyData.isNotEmpty()) {
            val gradeCounts = listOf("S", "A", "B", "C", "D", "E", "F", "N").associateWith { grade ->
                historyData.count { it.grade == grade }
            }.filterValues { it > 0 }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Grade Distribution", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Text("Historical performance across all semesters", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(32.dp))

                    val maxCount = gradeCounts.values.maxOrNull() ?: 1

                    Row(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        gradeCounts.forEach { (grade, count) ->
                            val barHeightRatio = count.toFloat() / maxCount.toFloat()
                            val barColor = when (grade) {
                                "S" -> Color(0xFF4CAF50)
                                "A" -> Color(0xFF2196F3)
                                "B" -> Color(0xFF9C27B0)
                                "F", "N" -> Color(0xFFF44336)
                                else -> Color(0xFFFFC107)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text(count.toString(), color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(24.dp)
                                        .fillMaxHeight(barHeightRatio)
                                        .background(barColor, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(grade, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp)) // Bottom padding for dock
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
            Text(value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}