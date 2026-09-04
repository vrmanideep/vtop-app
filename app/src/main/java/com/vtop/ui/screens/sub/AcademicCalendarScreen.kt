@file:Suppress("SpellCheckingInspection")

package com.vtop.ui.screens.sub

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.core.AppState
import com.vtop.core.SessionManager
import com.vtop.logic.AcademicCalendarSyncEngine
import com.vtop.models.AcademicCalendarEvent
import com.vtop.sync.SyncManager
import com.vtop.utils.AnalyticsManager
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Pre-compiled regex for performance
private val semIdRegex = Regex("(\\d{4})(\\d{2})")

fun extractYearFromSemId(semId: String): String {
    val match = semIdRegex.find(semId)
    return if (match != null) "${match.groupValues[1]}-${match.groupValues[2]}" else "Legacy"
}

data class TimelineEvent(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val displayStartDayNum: String,
    val displayEndDayNum: String,
    val startDay: String,
    val endDay: String,
    val title: String,
    val category: String,
    val monthYearHeader: String
)

// ====================================================================
// --- TOGGLE EVENT MERGING HERE ---
// Set `enableMerging = true` to combine consecutive days with the same
// description (e.g., "Instructional Day" from the 4th to the 10th).
// Set to `false` to show every single day individually.
// ====================================================================
private fun mergeConsecutiveEvents(events: List<TimelineEvent>, enableMerging: Boolean = true): List<TimelineEvent> {
    if (!enableMerging || events.isEmpty()) return events
    val merged = mutableListOf<TimelineEvent>()
    var current = events.first()

    for (i in 1 until events.size) {
        val next = events[i]

        val isAdjacent = ChronoUnit.DAYS.between(current.endDate, next.startDate) == 1L
        val isSameTitle = current.title.equals(next.title, ignoreCase = true)
        val isSameMonth = current.startDate.monthValue == next.startDate.monthValue // Keeps tab filters clean

        if (isAdjacent && isSameTitle && isSameMonth) {
            current = current.copy(
                endDate = next.endDate,
                displayEndDayNum = next.displayEndDayNum,
                endDay = next.endDay
            )
        } else {
            merged.add(current)
            current = next
        }
    }
    merged.add(current)
    return merged
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@SuppressLint("NewApi")
@Composable
fun AcademicCalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Academic_Calendar_Screen") }

    var availableSemesters by remember { mutableStateOf(Vault.getCalendarSemesterOptions(context).toList()) }
    var isFetchingSemesters by remember { mutableStateOf(false) }

    var selectedSemId by remember { mutableStateOf(Vault.getSelectedSemester(context)[0] ?: "") }
    var selectedSemName by remember { mutableStateOf(Vault.getSelectedSemester(context)[1] ?: "") }
    var showSemesterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (availableSemesters.isEmpty()) {
            isFetchingSemesters = true
            withContext(Dispatchers.IO) {
                try {
                    val client = SessionManager.getSyncClient()
                    if (client != null) {
                        val html = client.fetchCalendarSemestersRawHtml()
                        val fetched = com.vtop.logic.AcademicCalendarParser.parseSemesters(html).toList()
                        if (fetched.isNotEmpty()) {
                            Vault.saveCalendarSemesterOptions(context, fetched)
                            withContext(Dispatchers.Main) { availableSemesters = fetched }
                        }
                    }
                } catch (_: Exception) {}
                finally { isFetchingSemesters = false }
            }
        }
    }

    var rawEvents by remember { mutableStateOf(emptyList<AcademicCalendarEvent>()) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncTotalSteps by remember { mutableIntStateOf(0) }
    var syncCompletedSteps by remember { mutableIntStateOf(0) }
    var syncError by remember { mutableStateOf<String?>(null) }
    val globalSyncStatus = AppState.syncStatus.value

    val fetchCalendar = { semIdToFetch: String, forceFull: Boolean ->
        coroutineScope.launch(Dispatchers.IO) {
            isSyncing = true
            syncError = null
            syncTotalSteps = 0
            syncCompletedSteps = 0

            try {
                if (SessionManager.getSyncClient() == null) {
                    withContext(Dispatchers.Main) { syncError = "RECONNECTING" }
                    if (!SyncManager.isSyncing.value) {
                        SyncManager.performSync(context, forceNewSession = true)
                    }
                    SyncManager.isSyncing.first { !it }
                    if (SessionManager.getSyncClient() == null) throw Exception("Failed to re-establish VTOP session.")
                }

                withContext(Dispatchers.Main) { syncError = null }
                val client = SessionManager.getSyncClient()!!

                val events = AcademicCalendarSyncEngine.sync(
                    context = context,
                    client = client,
                    semId = semIdToFetch,
                    forceFullSync = forceFull,
                    onProgress = { completed, total ->
                        syncCompletedSteps = completed
                        syncTotalSteps = total
                    }
                )

                withContext(Dispatchers.Main) {
                    if (selectedSemId == semIdToFetch) rawEvents = events.toList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { syncError = e.message ?: "Sync Failed" }
            } finally {
                withContext(Dispatchers.Main) { isSyncing = false }
            }
        }
    }

    LaunchedEffect(selectedSemId) {
        val cachedEvents = Vault.getAcademicCalendar(context, selectedSemId).toList()
        rawEvents = cachedEvents
        if (cachedEvents.isEmpty() && selectedSemId.isNotBlank()) fetchCalendar(selectedSemId, false)
    }

    val todayDate = remember { LocalDate.now() }

    val parsedTimeline = remember(rawEvents) {
        if (rawEvents.isEmpty()) return@remember emptyList()

        val parseFormatter = java.time.format.DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d-MMM-yyyy")
            .toFormatter(Locale.ENGLISH)
        val dayNumFormatter = DateTimeFormatter.ofPattern("dd", Locale.ENGLISH)
        val headerFormatter = DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH)

        val unmerged = rawEvents.mapNotNull { event ->
            val safeDateStr = event.date.trim()
                .uppercase(Locale.ENGLISH)
                .replace(Regex("\\s+"), "")
                .replace(Regex("-([A-Z]{3})[A-Z]*-"), "-$1-")

            val dateObj = try {
                LocalDate.parse(safeDateStr, parseFormatter)
            } catch (e: Exception) {
                return@mapNotNull null
            }

            var cleanTitle = event.particulars
                .replace(" - General (Semester)", "")
                .replace(" - Combined", "")
                .replace(Regex("\\(Holiday\\)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\(No Instructional Day\\)", RegexOption.IGNORE_CASE), "")
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanTitle.contains("VITOPIA", ignoreCase = true)) cleanTitle = "VITOPIA"
            else if (cleanTitle.contains("CAT", ignoreCase = true)) cleanTitle = if (cleanTitle.contains("II")) "CAT - II (Exam)" else "CAT - I (Exam)"
            else if (cleanTitle.contains("Lab FAT", ignoreCase = true) || cleanTitle.contains("Laboratory FAT", ignoreCase = true)) cleanTitle = "Lab FAT (Exam)"
            else if (cleanTitle.contains("FAT", ignoreCase = true) || cleanTitle.contains("Final Assessment Test", ignoreCase = true)) cleanTitle = "FAT (Exam)"
            else if (cleanTitle.contains("Instructional Day", ignoreCase = true) && !cleanTitle.contains("No ", ignoreCase = true) && !cleanTitle.contains("Non ", ignoreCase = true)) cleanTitle = "Instructional Day"

            val category = when {
                cleanTitle.contains("Exam", true) || cleanTitle.contains("CAT", true) || cleanTitle.contains("FAT", true) -> "Exam"
                cleanTitle.contains("Holiday", true) || cleanTitle.contains("no instructional", true) || cleanTitle.contains("non instructional", true) || cleanTitle.contains("VITOPIA", true) -> "Holiday"
                else -> "Event"
            }

            TimelineEvent(
                startDate = dateObj,
                endDate = dateObj,
                displayStartDayNum = dateObj.format(dayNumFormatter),
                displayEndDayNum = dateObj.format(dayNumFormatter),
                startDay = event.day.trim(),
                endDay = event.day.trim(),
                title = cleanTitle,
                category = category,
                monthYearHeader = dateObj.format(headerFormatter).uppercase(Locale.getDefault())
            )
        }.sortedBy { it.startDate }

        // Apply dynamic merging
        mergeConsecutiveEvents(unmerged, enableMerging = true)
    }

    val currentMonthHeader = remember(todayDate) { todayDate.format(DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH)).uppercase(Locale.getDefault()) }

    val filterOptions = remember(parsedTimeline) {
        parsedTimeline.map { it.monthYearHeader }.distinct()
    }

    var selectedFilter by remember(filterOptions) {
        mutableStateOf(
            if (filterOptions.contains(currentMonthHeader)) currentMonthHeader
            else filterOptions.firstOrNull() ?: ""
        )
    }

    val filteredEvents = remember(parsedTimeline, selectedFilter) {
        parsedTimeline.filter { it.monthYearHeader == selectedFilter }
    }

    val nextExam = remember(parsedTimeline) { parsedTimeline.firstOrNull { it.category == "Exam" && !it.endDate.isBefore(todayDate) } }
    val nextHoliday = remember(parsedTimeline) { parsedTimeline.firstOrNull { it.category == "Holiday" && !it.endDate.isBefore(todayDate) } }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Academic Calendar", fontSize = 20.sp, fontWeight = FontWeight.Black)
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { showSemesterSheet = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = extractYearFromSemId(selectedSemId),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Semester", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    IconButton(onClick = { fetchCalendar(selectedSemId, true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Force Full Sync")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            val showFab = remember(listState.firstVisibleItemIndex, selectedFilter, filterOptions) {
                if (selectedFilter == currentMonthHeader) {
                    val todayIndex = filteredEvents.indexOfFirst { !it.endDate.isBefore(todayDate) }
                    todayIndex != -1 && kotlin.math.abs(listState.firstVisibleItemIndex - (todayIndex + 1)) > 5
                } else {
                    filterOptions.contains(currentMonthHeader)
                }
            }

            AnimatedVisibility(visible = showFab, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (filterOptions.contains(currentMonthHeader)) {
                            selectedFilter = currentMonthHeader
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(50)
                                val todayIndex = parsedTimeline.filter { it.monthYearHeader == currentMonthHeader }
                                    .indexOfFirst { !it.endDate.isBefore(todayDate) }
                                if (todayIndex != -1) {
                                    listState.scrollToItem(index = todayIndex + 1)
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Today, "Today") },
                    text = { Text("Jump to Today", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (showSemesterSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                val groupedSemesters = remember(availableSemesters) { availableSemesters.groupBy { extractYearFromSemId(it.id) }.toSortedMap(compareByDescending { it }) }
                val yearTabs = groupedSemesters.keys.toList()
                var selectedYearTab by remember { mutableStateOf(extractYearFromSemId(selectedSemId).takeIf { yearTabs.contains(it) } ?: yearTabs.firstOrNull() ?: "") }

                ModalBottomSheet(onDismissRequest = { showSemesterSheet = false }, sheetState = sheetState, containerColor = premiumSurfaceColor()) {
                    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                        Text("Select Semester", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp))
                        if (isFetchingSemesters) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        } else if (yearTabs.isEmpty()) {
                            Text("No semesters available.", modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            ScrollableTabRow(
                                selectedTabIndex = yearTabs.indexOf(selectedYearTab).coerceAtLeast(0),
                                containerColor = Color.Transparent, edgePadding = 24.dp,
                                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)) },
                                indicator = { tabPositions ->
                                    val index = yearTabs.indexOf(selectedYearTab).coerceAtLeast(0)
                                    if (index < tabPositions.size) TabRowDefaults.SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[index]), color = MaterialTheme.colorScheme.primary)
                                }
                            ) {
                                yearTabs.forEach { year ->
                                    Tab(selected = year == selectedYearTab, onClick = { selectedYearTab = year }, text = { Text(year, fontWeight = if (year == selectedYearTab) FontWeight.Bold else FontWeight.Medium, color = if (year == selectedYearTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) })
                                }
                            }
                            val semestersInActiveYear = groupedSemesters[selectedYearTab] ?: emptyList()
                            LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(items = semestersInActiveYear) { option ->
                                    val isSelected = option.id == selectedSemId
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { selectedSemId = option.id; selectedSemName = option.name; showSemesterSheet = false }.padding(horizontal = 24.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(option.name, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                        if (isSelected) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (rawEvents.isEmpty() || isSyncing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        if (syncError == "RECONNECTING") {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Reconnecting to VTOP...\n$globalSyncStatus", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
                        } else if (isSyncing) {
                            if (syncTotalSteps > 0) {
                                val progress = syncCompletedSteps.toFloat() / syncTotalSteps.toFloat()
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.8f).height(8.dp).clip(RoundedCornerShape(4.dp)), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round)
                                Spacer(Modifier.height(16.dp))
                                Text("Syncing $selectedSemName\nMonth $syncCompletedSteps of $syncTotalSteps", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
                            } else {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text("Initializing calendar sync...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
                            }
                        } else if (syncError != null) {
                            Icon(Icons.Default.Event, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text("Sync Failed", color = MaterialTheme.colorScheme.error, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(syncError!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { fetchCalendar(selectedSemId, false) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Retry Sync", fontWeight = FontWeight.Bold) }
                        } else {
                            Icon(Icons.Default.Event, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text("No calendar events found.\nTap the refresh icon at the top to sync.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            else {
                if (filterOptions.isNotEmpty()) {
                    CategoryFilters(
                        options = filterOptions,
                        selectedOption = selectedFilter,
                        onOptionSelected = { selectedFilter = it }
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        if (nextExam != null || nextHoliday != null) {
                            NextEventDashboard(nextExam, nextHoliday, todayDate)
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    if (filteredEvents.isEmpty()) {
                        item {
                            Text("No events scheduled for $selectedFilter.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center)
                        }
                    } else {
                        items(items = filteredEvents) { event ->
                            TimelineEventRow(event = event, today = todayDate)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryFilters(options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items = options) { option ->
            val isSelected = option == selectedOption
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else premiumSurfaceColor()
            val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else premiumBorderColor()
            val textWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor)
                    .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                    .clickable { onOptionSelected(option) }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(option, color = textColor, fontSize = 13.sp, fontWeight = textWeight)
            }
        }
    }
}

@Composable
fun NextEventDashboard(nextExam: TimelineEvent?, nextHoliday: TimelineEvent?, today: LocalDate) {
    // Modifier.height(IntrinsicSize.Max) forces both cards to match the height of the taller one
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (nextExam != null) CountdownCard(Modifier.weight(1f).fillMaxHeight(), nextExam, today, Color(0xFF8B5CF6))
        if (nextHoliday != null) CountdownCard(Modifier.weight(1f).fillMaxHeight(), nextHoliday, today, Color(0xFF4ADE80))
    }
}

@Composable
fun CountdownCard(modifier: Modifier, event: TimelineEvent, today: LocalDate, accentColor: Color) {
    val days = ChronoUnit.DAYS.between(today, event.startDate)
    val timeText = when {
        days < 0L -> "Ongoing"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = premiumSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, premiumBorderColor())
    ) {
        // Use Arrangement.SpaceBetween to distribute content evenly so cards match height
        Column(modifier = Modifier.fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(timeText, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                val dateDisplay = if (event.startDate == event.endDate) "${event.displayStartDayNum} ${event.monthYearHeader.take(3)}" else "${event.displayStartDayNum}-${event.displayEndDayNum} ${event.monthYearHeader.take(3)}"
                Text(event.title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Text(event.monthYearHeader, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
fun CountdownCard(modifier: Modifier, label: String, event: TimelineEvent, today: LocalDate, accentColor: Color) {
    val days = ChronoUnit.DAYS.between(today, event.startDate)
    val timeText = when {
        days < 0L -> "Ongoing"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = premiumSurfaceColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, premiumBorderColor())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(timeText, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            val dateDisplay = if (event.startDate == event.endDate) "${event.displayStartDayNum} ${event.monthYearHeader.take(3)}" else "${event.displayStartDayNum}-${event.displayEndDayNum} ${event.monthYearHeader.take(3)}"
            Text("${event.title} · $dateDisplay", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}

@Composable
fun TimelineEventRow(event: TimelineEvent, today: LocalDate) {
    val isToday = !today.isBefore(event.startDate) && !today.isAfter(event.endDate)

    val isHoliday = event.category.equals("Holiday", true)
    val isNoInstructional = event.title.contains("No Instructional", true)
    val isInstructional = event.title.contains("Instructional Day", true) && !isNoInstructional
    val isExam = event.category.equals("Exam", true)

    val cardBg = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else premiumSurfaceColor()
    val cardBorder = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else premiumBorderColor()

    val dotColor = when {
        isHoliday -> Color(0xFFF59E0B) // Orange
        isExam -> Color(0xFF8B5CF6) // Purple
        isInstructional -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Gray for No Instructional Day/Misc
    }

    val isMultiDay = event.startDate != event.endDate

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flexible width container to prevent line-wrapping on double digits
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(min = 48.dp)
            ) {
                if (isMultiDay) {
                    Text(
                        text = "${event.displayStartDayNum.toInt()}-${event.displayEndDayNum.toInt()}",
                        fontSize = 17.sp, // Scaled down slightly to fit cleanly
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    val dayRange = "${event.startDay.uppercase(Locale.getDefault()).take(3)}-${event.endDay.uppercase(Locale.getDefault()).take(3)}"
                    Text(
                        text = dayRange,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = event.displayStartDayNum,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = event.startDay.uppercase(Locale.getDefault()).take(3),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = event.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (dotColor != Color.Transparent) {
                Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
            }
        }
    }
}