package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.models.ExamScheduleModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import com.composables.icons.lucide.*

// Extracts a safe parsable time, falling back to reporting time or a default to prevent ParseExceptions
private fun getSafeStartTime(exam: ExamScheduleModel): String {
    val eTime = exam.examTime.trim()
    if (eTime.isNotBlank() && eTime != "-") return eTime.split("-")[0].trim()
    val rTime = exam.reportingTime.trim()
    if (rTime.isNotBlank() && rTime != "-") return rTime.split("-")[0].trim()
    return "12:00 AM"
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NewApi")
@Composable
fun Exams(exams: List<ExamScheduleModel>) {
    var selectedExam by remember { mutableStateOf<ExamScheduleModel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (exams.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(bottom = 80.dp), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Lucide.CalendarDays, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("No exams found for this semester", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Sync to load exams", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        return
    }

    ExamListScreen(exams = exams, onExamClick = { selectedExam = it })

    if (selectedExam != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedExam = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ExamBottomSheetContent(exam = selectedExam!!)
        }
    }
}

@Composable
private fun ExamListScreen(exams: List<ExamScheduleModel>, onExamClick: (ExamScheduleModel) -> Unit) {
    val sdfFull = remember { SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH) }
    var currentTime by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { delay(60000); currentTime = Date() }
    }

    // Safely find the next exam only if it actually has a valid scheduled date from VTOP
    val nextExam = remember(exams, currentTime) {
        exams.filter { it.examDate.isNotBlank() && it.examDate != "-" }
            .mapNotNull { exam ->
                try {
                    val date = sdfFull.parse("${exam.examDate} ${getSafeStartTime(exam)}")
                    if (date != null && date.after(currentTime)) Pair(exam, date) else null
                } catch (_: Exception) { null }
            }.minByOrNull { it.second }?.first
    }

    // Only flag a clash if the exam has a genuine date and time assigned
    val clashingExams = remember(exams) {
        exams.filter { it.examDate.isNotBlank() && it.examDate != "-" && getSafeStartTime(it) != "12:00 AM" }
            .groupBy { "${it.examDate} ${getSafeStartTime(it)}" }
            .filter { it.value.size > 1 }.flatMap { it.value }.toSet()
    }

    val coreFilters = listOf("All", "FAT")
    val dynamicFilters = remember(exams) { (coreFilters + exams.map { it.examType }).distinct() }
    var selectedFilter by remember { mutableStateOf("All") }

    val filteredExams = remember(exams, selectedFilter) {
        if (selectedFilter == "All") exams else exams.filter { it.examType.replace("-", " ").contains(selectedFilter.replace("-", " "), true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(dynamicFilters) { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(24.dp))
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha=0.2f), RoundedCornerShape(24.dp))
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (filteredExams.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Lucide.CalendarDays, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No exams found", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                if (selectedFilter == "All" && nextExam != null) {
                    item {
                        Text("NEXT UP", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))
                        NextUpCard(exam = nextExam, currentTime = currentTime, onClick = { onExamClick(nextExam) })
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
                    }
                }
                items(filteredExams) { exam ->
                    val hasValidDate = exam.examDate.isNotBlank() && exam.examDate != "-"

                    val urgencyColor = if (!hasValidDate) {
                        Color.Transparent
                    } else {
                        val targetDate = try {
                            sdfFull.parse("${exam.examDate} ${getSafeStartTime(exam)}") ?: currentTime
                        } catch (e: Exception) { currentTime }

                        val diffDays = ((targetDate.time - currentTime.time) / (1000 * 60 * 60 * 24)).toInt()
                        when {
                            diffDays < 0 -> Color.Transparent
                            diffDays <= 3 -> Color(0xFFF59E0B) // Swapped to a cleaner Amber/Orange
                            diffDays <= 7 -> Color(0xFF60A5FA)
                            else -> Color.Transparent
                        }
                    }

                    StandardExamCard(exam = exam, urgencyColor = urgencyColor, isClashing = clashingExams.contains(exam), onClick = { onExamClick(exam) })
                }
            }
        }
    }
}

@Composable
private fun NextUpCard(exam: ExamScheduleModel, currentTime: Date, onClick: () -> Unit) {
    val sdfFull = remember { SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH) }
    val targetDate = remember(exam) { try { sdfFull.parse("${exam.examDate} ${getSafeStartTime(exam)}") } catch(e:Exception){ Date() } } ?: Date()
    val diffMs = maxOf(0L, targetDate.time - currentTime.time)
    val days = diffMs / (1000 * 60 * 60 * 24)
    val hours = (diffMs % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)
    val mins = (diffMs % (1000 * 60 * 60)) / (1000 * 60)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.1f)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(text = "${exam.examType} · ${exam.courseCode}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = exam.courseTitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CountdownBox(value = days.toString(), label = "days")
                    CountdownBox(value = hours.toString(), label = "hrs")
                    CountdownBox(value = mins.toString(), label = "min")
                }
            }
            ExamDetailsGrid(exam)
        }
    }
}

@Composable
private fun CountdownBox(value: String, label: String) {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun StandardExamCard(exam: ExamScheduleModel, urgencyColor: Color, isClashing: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.1f)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(urgencyColor))
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "${exam.examType} · ${exam.courseCode}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = exam.courseTitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    }
                    if (isClashing) {
                        Row(
                            modifier = Modifier.border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Lucide.TriangleAlert, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("clash", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                ExamDetailsGrid(exam)
            }
        }
    }
}

@Composable
private fun ExamDetailsGrid(exam: ExamScheduleModel) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailBlock(label = "DATE", value = exam.examDate, modifier = Modifier.weight(1f))
            DetailBlock(label = "TIME", value = getSafeStartTime(exam), modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            DetailBlock(label = "VENUE", value = exam.venue, modifier = Modifier.weight(1f))
            DetailBlock(label = "SEAT", value = "${exam.seatLocation} (${exam.seatNumber})", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExamBottomSheetContent(exam: ExamScheduleModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = exam.examType, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text = exam.courseTitle, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Black)

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.1f), modifier = Modifier.padding(vertical = 8.dp))

        val details = listOf(
            "Course Code" to exam.courseCode, "Course Type" to exam.courseType, "Class ID" to exam.classId,
            "Reporting Time" to exam.reportingTime, "Exam Date" to exam.examDate, "Exam Time" to exam.examTime, "Venue" to exam.venue
        )

        details.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Seat Location", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Seat No: ${exam.seatNumber}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(text = exam.seatLocation, color = Color(0xFF4ADE80), fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}