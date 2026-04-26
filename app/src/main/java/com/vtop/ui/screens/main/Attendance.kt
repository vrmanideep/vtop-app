package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.models.AttendanceModel
import java.util.Locale
import androidx.compose.foundation.shape.CircleShape

// --- STRICT PREMIUM COLORS (Bypasses Material You Tint) ---
@Composable
fun premiumSurfaceColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF141414) else Color(0xFFFFFFFF)

@Composable
fun premiumBorderColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.08f)

// --- RESTORED BUNK LOGIC ---
sealed class BunkState {
    data class Safe(val canMiss: Int) : BunkState()
    data class AtRisk(val mustAttend: Int) : BunkState()
    object NoData : BunkState()
}

fun calculateBunkBudget(attended: Int, total: Int, target: Float = 0.75f): BunkState {
    if (total == 0) return BunkState.NoData
    val currentPct = attended.toFloat() / total

    return if (currentPct >= target) {
        var canMiss = 0
        while ((attended.toFloat() / (total + canMiss + 1)) >= target) {
            canMiss++
        }
        BunkState.Safe(canMiss)
    } else {
        var mustAttend = 0
        while (((attended + mustAttend).toFloat() / (total + mustAttend)) < target) {
            mustAttend++
        }
        BunkState.AtRisk(mustAttend)
    }
}
// -----------------------------

@Composable
fun AttendanceCard(item: AttendanceModel, onClick: () -> Unit) {
    val pString = item.attendancePercentage ?: "0"
    val percentage = pString.filter { it.isDigit() }.toIntOrNull() ?: 0
    val progress = percentage / 100f

    val statusColor = when {
        percentage < 75 -> MaterialTheme.colorScheme.error
        percentage < 85 -> Color(0xFFF59E0B) // Warning Amber
        else -> Color(0xFF4CAF50) // Success Green
    }

    val attended = item.attendedClasses?.toString()?.toIntOrNull() ?: 0
    val total = item.totalClasses?.toString()?.toIntOrNull() ?: 0
    val bunkState = calculateBunkBudget(attended, total)

    val cType = item.courseType ?: ""
    val categoryLabel = when {
        cType.contains("TH", ignoreCase = true) || cType.contains("ETH", ignoreCase = true) -> "Theory"
        cType.contains("LO", ignoreCase = true) || cType.contains("ELA", ignoreCase = true) -> "Lab"
        cType.contains("PJT", ignoreCase = true) || cType.contains("EPT", ignoreCase = true) -> "Project"
        else -> cType.ifEmpty { "Theory" }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = premiumSurfaceColor()),
        border = BorderStroke(1.dp, premiumBorderColor()),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${item.courseCode} · $categoryLabel",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "$percentage%", color = statusColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(premiumBorderColor())) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(
                                brush = Brush.horizontalGradient(listOf(statusColor.copy(alpha = 0.4f), statusColor)),
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$attended / $total", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                BunkPredictorChip(bunkState)
            }
        }
    }
}

@Composable
private fun BunkPredictorChip(bunkState: BunkState) {
    val (statusText, isDangerous) = when (bunkState) {
        is BunkState.Safe -> {
            val dangerText = if (bunkState.canMiss == 0) " · Danger" else ""
            "Can skip ${bunkState.canMiss} more$dangerText" to (bunkState.canMiss == 0)
        }
        is BunkState.AtRisk -> "Must attend ${bunkState.mustAttend} more" to true
        BunkState.NoData -> "" to false
    }

    if (statusText.isEmpty()) return
    val chipStatusColor = if (isDangerous) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)

    Box(
        modifier = Modifier.background(chipStatusColor.copy(alpha = 0.1f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = statusText, color = chipStatusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("NewApi")
@Composable
fun Attendance(attendanceData: List<AttendanceModel>, onLaunchSimulator: () -> Unit = {}) {
    var selectedCourse by remember { mutableStateOf<AttendanceModel?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (attendanceData.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No Attendance Data Found", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }

    val atRiskCount = attendanceData.count {
        val p = it.attendancePercentage?.filter { char -> char.isDigit() }?.toIntOrNull() ?: 100
        p < 75
    }

    val sortedCourses = remember(attendanceData) {
        attendanceData.sortedWith(
            compareBy<AttendanceModel> { it.courseCode ?: "" }.thenBy {
                val type = it.courseType?.uppercase(Locale.getDefault()) ?: ""
                when {
                    type.contains("TH") || type.contains("ETH") -> 0
                    type.contains("LO") || type.contains("ELA") || type.contains("LAB") -> 1
                    type.contains("PJT") || type.contains("EPJ") -> 2
                    else -> 3
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { onLaunchSimulator() },
                    colors = CardDefaults.cardColors(containerColor = premiumSurfaceColor()),
                    border = BorderStroke(1.dp, premiumBorderColor()),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DateRange, contentDescription = "Simulator", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(text = "Bunk Simulator", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = if (atRiskCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else premiumSurfaceColor()),
                    border = BorderStroke(1.dp, if (atRiskCount > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else premiumBorderColor()),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = atRiskCount.toString(), color = if (atRiskCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text(text = "Courses at Risk", color = if (atRiskCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        item { Text(text = "Detailed Breakdown", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(sortedCourses) { course -> AttendanceCard(item = course, onClick = { selectedCourse = course }) }
    }

    if (selectedCourse != null) {
        val bottomSheetBg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF111111) else Color(0xFFFFFFFF)
        ModalBottomSheet(onDismissRequest = { selectedCourse = null }, containerColor = bottomSheetBg, sheetState = sheetState) {
            AttendanceBottomSheetContent(course = selectedCourse!!, onSimulateClick = { selectedCourse = null; onLaunchSimulator() }, onBack = { selectedCourse = null })
        }
    }
}

@Composable
fun AttendanceBottomSheetContent(course: AttendanceModel, onSimulateClick: () -> Unit, onBack: () -> Unit) {
    val cType = course.courseType ?: ""
    val categoryLabel = when {
        cType.contains("TH", ignoreCase = true) || cType.contains("ETH", ignoreCase = true) -> "Theory"
        cType.contains("LO", ignoreCase = true) || cType.contains("ELA", ignoreCase = true) -> "Lab"
        cType.contains("PJT", ignoreCase = true) || cType.contains("EPT", ignoreCase = true) -> "Project"
        else -> cType.ifEmpty { "Theory" }
    }

    val scrollState = rememberScrollState()
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Column(modifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.85f).navigationBarsPadding().verticalScroll(scrollState).padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
            Text(text = "${course.courseCode} · $categoryLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Text(text = course.courseName ?: "N/A", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(Modifier.height(16.dp))

        AttendanceDetailCore(course = course, onSimulateClick = onSimulateClick)
    }
}

// --- SHARED CORE EXPORTED FOR TIMETABLE ---
@Composable
fun AttendanceDetailCore(course: AttendanceModel, onSimulateClick: (() -> Unit)? = null) {
    val pString = course.attendancePercentage ?: "0"
    val percentage = pString.filter { it.isDigit() }.toIntOrNull() ?: 0
    val isSafe = percentage >= 75
    val statusColor = if (isSafe) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    val attended = course.attendedClasses?.toString()?.toIntOrNull() ?: 0
    val total = course.totalClasses?.toString()?.toIntOrNull() ?: 0
    val bunkState = calculateBunkBudget(attended, total)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.fillMaxSize(),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                strokeWidth = 8.dp,
                strokeCap = StrokeCap.Round
            )
            Text(text = "$percentage%", color = statusColor, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(attended.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text("Attended", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text("Total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth()
                .background(premiumSurfaceColor(), RoundedCornerShape(12.dp))
                .border(1.dp, premiumBorderColor(), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            when (bunkState) {
                is BunkState.Safe -> Column {
                    Text("Bunk Budget", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "You can safely skip the next ${bunkState.canMiss} classes and stay above 75%.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
                is BunkState.AtRisk -> Column {
                    Text("Recovery Path", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "If you attend the next ${bunkState.mustAttend} consecutive classes, you will reach 75%.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, lineHeight = 18.sp)
                }
                is BunkState.NoData -> Text("Not enough data to calculate predictions.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }

        if (onSimulateClick != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSimulateClick, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp)
            ) { Text("Open Simulator", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
        }

        if (!course.history.isNullOrEmpty()) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
            Spacer(Modifier.height(16.dp))

            var showHistory by remember { mutableStateOf(false) }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showHistory = !showHistory }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Full History", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Icon(imageVector = if (showHistory) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Toggle History", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(visible = showHistory) {
                    Column { Spacer(Modifier.height(8.dp)); DetailedAttendanceTable(course) }
                }
            }
        }
    }
}

@Composable
private fun DetailedAttendanceTable(item: AttendanceModel) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(text = "DATE", modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Text(text = "TIME/SLOT", modifier = Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            Text(text = "STATUS", modifier = Modifier.weight(0.25f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.End)
        }
        item.history?.forEach { h ->
            val statusUpper = h.status?.uppercase(Locale.getDefault()) ?: ""
            val (bgColor, textColor) = when {
                statusUpper.contains("PRESENT") -> Color(0xFF4CAF50).copy(alpha = 0.2f) to Color(0xFF4CAF50)
                statusUpper.contains("DUTY") -> Color(0xFF2196F3).copy(alpha = 0.2f) to Color(0xFF2196F3)
                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f) to MaterialTheme.colorScheme.error
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = h.date ?: "", modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                Text(text = "${h.time ?: "--"} / ${h.slot ?: "--"}", modifier = Modifier.weight(0.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                Box(modifier = Modifier.weight(0.25f), contentAlignment = Alignment.CenterEnd) {
                    Box(modifier = Modifier.background(bgColor, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = h.status ?: "", color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}