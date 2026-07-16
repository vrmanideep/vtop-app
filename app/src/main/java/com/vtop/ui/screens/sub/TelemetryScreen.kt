package com.vtop.ui.screens.sub

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vtop.telemetry.viewer.TelemetryViewModel
import com.vtop.telemetry.viewer.TelemetrySessionInfo
import com.vtop.telemetry.model.TelemetryEvent
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- Theme & Colors ---
// 1. AMOLED Black Background
private val BgDark = Color(0xFF000000)
private val SurfaceDark = Color(0xFF0A0A0A)
private val SurfaceVariant = Color(0xFF171717)
private val OutlineDark = Color(0xFF333333)
private val TextMain = Color(0xFFDAE2FD)
private val TextMuted = Color(0xFF8C909F)

private val LogInfo = Color(0xFF3B82F6)
private val LogWarn = Color(0xFFF59E0B)
private val LogError = Color(0xE6E74B4B)
private val LogSuccess = Color(0xFF10B981)
private val LogDebug = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelemetryScreen(
    vm: TelemetryViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    BackHandler {
        onBack()
    }

    val context = LocalContext.current
    val sessions by vm.sessions.collectAsState()
    val selectedSession by vm.selectedSession.collectAsState()
    val statistics by vm.statistics.collectAsState()
    val events by vm.filteredEvents.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var selectedEvent by remember { mutableStateOf<TelemetryEvent?>(null) }

    // 4. State for active filter chip tracking
    var activeFilter by remember { mutableStateOf("ALL") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BgDark,
            surface = BgDark,
            onSurface = TextMain,
            onSurfaceVariant = TextMuted
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = SurfaceDark,
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(Modifier.fillMaxSize().statusBarsPadding()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceVariant)
                                .padding(16.dp)
                        ) {
                            Text(
                                "TELEMETRY SESSIONS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = TextMuted
                            )
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active & Recent", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Text("${sessions.size} TOTAL", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                        }

                        HorizontalDivider(color = OutlineDark)

                        LazyColumn {
                            items(sessions) { session ->
                                CompactSessionItem(
                                    session = session,
                                    isSelected = selectedSession?.sessionId == session.sessionId,
                                    onClick = {
                                        vm.openSession(session)
                                        scope.launch { drawerState.close() }
                                    },
                                    onDelete = {
                                        vm.deleteSession(session)
                                    }
                                )
                                HorizontalDivider(color = OutlineDark)
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = BgDark,
                topBar = {
                    Column {
                        Row(
                            modifier = Modifier
                                .background(SurfaceDark)
                                .statusBarsPadding()
                                .fillMaxWidth()
                                .border(1.dp, OutlineDark)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextMain, modifier = Modifier.size(20.dp))
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { scope.launch { drawerState.open() } }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedSession?.sessionId ?: "Select Session",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LogInfo,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(Icons.Default.ArrowDropDown, null, tint = LogInfo)
                            }

                            // Share Button
                            IconButton(onClick = {
                                selectedSession?.let { session ->
                                    try {
                                        val logFile = File(context.filesDir, "telemetry/${session.sessionId}.jsonl")
                                        if (logFile.exists()) {
                                            val authority = "${context.packageName}.provider"
                                            Log.d("TELEMETRY", "Path = ${logFile.absolutePath}")
                                            Log.d("TELEMETRY", "Exists = ${logFile.exists()}")
                                            Log.d("TELEMETRY", "Length = ${logFile.length()}")
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                authority,
                                                logFile
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "*/*" // Use application/json or octet-stream
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, "Telemetry File: ${session.sessionId}.jsonl")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Telemetry File"))
                                        } else {
                                            Toast.makeText(context, "Log file not found.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Error sharing file. Ensure FileProvider is setup.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Share, "Share", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }

                            IconButton(onClick = { vm.refreshSessions() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Refresh, "Refresh", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { vm.deleteSelectedSession() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = LogError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    if (statistics == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No telemetry session selected.", color = TextMuted, fontFamily = FontFamily.Monospace)
                        }
                        return@Column
                    }

                    val stats = statistics!!

                    // Search Bar
                    Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it; vm.setSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextMain),
                            placeholder = { Text("Search events...", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = OutlineDark,
                                focusedBorderColor = LogInfo,
                                unfocusedContainerColor = SurfaceDark,
                                focusedContainerColor = SurfaceDark
                            ),
                            shape = RoundedCornerShape(4.dp)
                        )
                    }

                    // Dense Stats Table
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .border(1.dp, OutlineDark, RoundedCornerShape(4.dp))
                            .background(SurfaceDark)
                    ) {
                        Row(Modifier.fillMaxWidth().border(0.5.dp, OutlineDark)) {
                            StatCell("Events", stats.totalEvents.toString(), Modifier.weight(1f))
                            StatCell("Success", stats.successCount.toString(), Modifier.weight(1f), LogSuccess)
                        }
                        Row(Modifier.fillMaxWidth().border(0.5.dp, OutlineDark)) {
                            StatCell("Errors", stats.errorCount.toString(), Modifier.weight(1f), LogError)
                            StatCell("Warnings", stats.warningCount.toString(), Modifier.weight(1f), LogWarn)
                        }
                        Row(Modifier.fillMaxWidth().border(0.5.dp, OutlineDark)) {
                            StatCell("Duration", formatDuration(stats.durationMillis), Modifier.weight(1f))
                            StatCell("", "", Modifier.weight(1f))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Mock Timeline Visualizer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .border(1.dp, OutlineDark, RoundedCornerShape(4.dp))
                            .background(SurfaceDark)
                            .padding(8.dp)
                    ) {
                        Text("Execution Timeline", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))

                        stats.moduleCounts.entries.take(4).forEachIndexed { index, entry ->
                            val colors = listOf(LogInfo, LogSuccess, LogWarn, Color(0xFFA855F7))
                            val color = colors[index % colors.size]
                            val percentage = (entry.value.toFloat() / stats.totalEvents.coerceAtLeast(1))

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Text(entry.key.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted, modifier = Modifier.width(60.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Box(modifier = Modifier.weight(1f).height(12.dp).background(SurfaceVariant, RoundedCornerShape(6.dp))) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(percentage.coerceAtLeast(0.05f)).background(color, RoundedCornerShape(6.dp)))
                                }
                                Text("${entry.value} evts", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMuted, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                        }
                    }

                    // Compact Filters
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            FilterChipBtn("ALL", activeFilter == "ALL") {
                                activeFilter = "ALL"
                                vm.setFilter(com.vtop.telemetry.viewer.TelemetryFilter.NONE)
                            }
                        }
                        items(stats.moduleCounts.keys.toList()) { module ->
                            FilterChipBtn(module.name, activeFilter == module.name) {
                                activeFilter = module.name
                                vm.setFilter(com.vtop.telemetry.viewer.TelemetryFilter(module = module))
                            }
                        }
                    }

                    // Dense Event List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .border(1.dp, OutlineDark, RoundedCornerShape(4.dp))
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        items(events, key = { it.id }) { event ->
                            CompactEventRow(event = event) {
                                selectedEvent = event
                            }
                        }
                    }
                }
            }
        }

        // 5. Normal Modal Bottom Sheet
        selectedEvent?.let { event ->
            EventDetailSheet(event = event, onDismiss = { selectedEvent = null })
        }
    }
}

// --- Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventDetailSheet(event: TelemetryEvent, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandJson by remember { mutableStateOf(false) }
    val levelColor = getLevelColor(event.level.name)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title Block
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(2.dp)).background(levelColor.copy(alpha = 0.2f)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(event.level.name, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = levelColor, fontWeight = FontWeight.Bold)
                    }
                    Text(event.module.name, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                    Spacer(Modifier.weight(1f))
                    Text(formatTimestampFull(event.timestamp), fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
                }
                Spacer(Modifier.height(8.dp))
                Text(event.message, fontSize = 18.sp, fontFamily = FontFamily.Monospace, color = levelColor, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = OutlineDark)
            }

            // Context Metrics Grid
            item {
                Text("Context Metrics", fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                DetailRowDense("Tag", event.tag)
                DetailRowDense("Thread", event.thread)
                DetailRowDense("PID", event.pid.toString())

                event.metadata.forEach { (key, value) ->
                    DetailRowDense(key, value?.toString() ?: "null")
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = OutlineDark)
            }

            // Raw Payload
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expandJson = !expandJson }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (expandJson) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextMain)
                    Text("Raw Payload", fontSize = 14.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = TextMain)
                }

                if (expandJson) {
                    Box(modifier = Modifier.fillMaxWidth().background(BgDark, RoundedCornerShape(4.dp)).border(1.dp, OutlineDark, RoundedCornerShape(4.dp)).padding(12.dp)) {
                        Text(
                            text = com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(event),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LogSuccess
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSessionItem(
    session: TelemetrySessionInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor = if (isSelected) SurfaceVariant else Color.Transparent
    val dotColor = if (isSelected) LogSuccess else LogInfo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dotColor))
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.sessionId,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (isSelected) LogInfo else TextMain,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Events: ${session.eventCount}", fontSize = 11.sp, color = TextMuted)
                Text(session.readableSize, fontSize = 11.sp, color = TextMuted)
                Text(formatTimestampOnly(session.lastModified), fontSize = 11.sp, color = TextMuted)
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Delete, "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier, valueColor: Color = TextMain) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FilterChipBtn(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) LogInfo else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (selected) BgDark else TextMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactEventRow(event: TelemetryEvent, onClick: () -> Unit) {
    val levelColor = getLevelColor(event.level.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .border(0.5.dp, OutlineDark.copy(alpha=0.5f))
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(formatTimestampOnly(event.timestamp), fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted, modifier = Modifier.width(75.dp))

        Box(
            modifier = Modifier.width(36.dp).clip(RoundedCornerShape(2.dp)).background(levelColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(event.level.name.take(4), fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = levelColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 2.dp))
        }

        Text(event.module.name, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted, modifier = Modifier.width(45.dp).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)

        Text(event.message, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))

        if (event.tag.isNotEmpty()) {
            Text(event.tag, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = TextMuted, modifier = Modifier.width(70.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun DetailRowDense(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextMuted)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextMain, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

private fun getLevelColor(level: String): Color = when(level.uppercase()) {
    "INFO" -> LogInfo
    "WARN", "WARNING" -> LogWarn
    "ERROR", "FATAL" -> LogError
    "SUCCESS", "SUCC" -> LogSuccess
    else -> LogDebug
}

private fun formatTimestampOnly(millis: Long): String {
    return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))
}

private fun formatTimestampFull(millis: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(millis))
}

private fun formatDuration(millis: Long): String {
    if (millis < 1000) return "${millis}ms"
    val seconds = millis / 1000
    if (seconds < 60) return "${seconds}.${millis % 1000}s"
    val minutes = seconds / 60
    return "${minutes}m ${seconds % 60}s"
}