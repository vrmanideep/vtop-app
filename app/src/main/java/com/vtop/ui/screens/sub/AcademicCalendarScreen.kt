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

data class ParsedCalendarDay(
    val date: LocalDate,
    val day: String,
    val title: String,
    val category: String
)

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
        val headerFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

        rawEvents.mapNotNull { event ->
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
                .replace(Regex("\\(No Instructional Day\\)", RegexOption.IGNORE_CASE), "") // Strips redundant text
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
    }

    var selectedFilter by remember { mutableStateOf("All") }

    // Dynamically generate filter options based on available months in the timeline
    val filterOptions = remember(parsedTimeline) {
        listOf("All") + parsedTimeline.map { it.monthYearHeader }.distinct()
    }

    val filteredEvents = remember(parsedTimeline, selectedFilter) {
        if (selectedFilter == "All") parsedTimeline
        else parsedTimeline.filter { it.monthYearHeader == selectedFilter }
    }

    val groupedByMonth = remember(filteredEvents) { filteredEvents.groupBy { it.monthYearHeader } }

    val nextExam = remember(parsedTimeline) { parsedTimeline.firstOrNull { it.category == "Exam" && !it.endDate.isBefore(todayDate) } }
    val nextHoliday = remember(parsedTimeline) { parsedTimeline.firstOrNull { it.category == "Holiday" && !it.endDate.isBefore(todayDate) } }

    val listState = rememberLazyListState()
    var showFab by remember { mutableStateOf(false) }

    LaunchedEffect(filteredEvents, listState.layoutInfo.totalItemsCount) {
        if (filteredEvents.isNotEmpty()) {
            val todayIndex = filteredEvents.indexOfFirst { !it.endDate.isBefore(todayDate) }
            if (todayIndex != -1) {
                val headerOffset = groupedByMonth.keys.indexOf(filteredEvents[todayIndex].monthYearHeader)
                listState.scrollToItem(index = todayIndex + headerOffset + 2)
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        val todayIndex = filteredEvents.indexOfFirst { !it.endDate.isBefore(todayDate) }
        showFab = todayIndex != -1 && kotlin.math.abs(listState.firstVisibleItemIndex - todayIndex) > 5
    }
    val firstUpcomingEvent = remember(filteredEvents) {
        filteredEvents.firstOrNull { !it.endDate.isBefore(todayDate) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Academic Calendar", fontSize = 20.sp, fontWeight = FontWeight.Black) },
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
            AnimatedVisibility(visible = showFab, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // 1. Reset the filter to "All" so the current date exists in the list
                        selectedFilter = "All"

                        coroutineScope.launch {
                            // 2. Give Compose a fraction of a second to rebuild the full list
                            kotlinx.coroutines.delay(50)

                            val todayIndex = parsedTimeline.indexOfFirst { !it.endDate.isBefore(todayDate) }
                            if (todayIndex != -1) {
                                // Calculate offset based on the newly restored full list
                                val fullGrouped = parsedTimeline.groupBy { it.monthYearHeader }
                                val headerOffset = fullGrouped.keys.indexOf(parsedTimeline[todayIndex].monthYearHeader)
                                listState.scrollToItem(index = todayIndex + headerOffset + 2)
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

                ModalBottomSheet(onDismissRequest = { showSemesterSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                )
                {
                    item {
                        if (nextExam != null || nextHoliday != null) {
                            NextEventDashboard(nextExam, nextHoliday, todayDate)
                        }
                        CategoryFilters(options = filterOptions, selectedOption = selectedFilter, onOptionSelected = { selectedFilter = it })
                    }

                    if (filteredEvents.isEmpty()) {
                        item { Text("No events scheduled for $selectedFilter.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center) }
                    } else {
                        groupedByMonth.forEach { (monthHeader, eventsInMonth) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = monthHeader,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            // Use `items(items = ...)` to safely check against the specific event
                            items(items = eventsInMonth) { event ->
                                if (event == firstUpcomingEvent) {
                                    TodayMarkerPill()
                                }

                                TimelineEventRow(event)
                            }
                        }

                        // If today is past all the events in the list, place it at the very bottom
                        if (firstUpcomingEvent == null && selectedFilter == "All") {
                            item { TodayMarkerPill() }
                        }
                    }
                }
    }
    }
}
}

@Composable
fun CategoryFilters(options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = options) { option ->
            val isSelected = option == selectedOption
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Box(modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(bgColor).clickable { onOptionSelected(option) }.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(option, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NextEventDashboard(nextExam: TimelineEvent?, nextHoliday: TimelineEvent?, today: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (nextExam != null) CountdownCard(Modifier.weight(1f), "NEXT EXAM", nextExam, today, Color(0xFF8B5CF6))
        if (nextHoliday != null) CountdownCard(Modifier.weight(1f), "NEXT HOLIDAY", nextHoliday, today, Color(0xFF4ADE80))
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(timeText, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            val dateDisplay = if (event.startDate == event.endDate) "${event.displayStartDayNum} ${event.monthYearHeader.take(3)}" else "${event.displayStartDayNum}-${event.displayEndDayNum} ${event.monthYearHeader.take(3)}"
            // Removed maxLines and overflow constraints, added lineHeight for readability
            Text("${event.title} · $dateDisplay", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
    }
}
@Composable
fun TodayMarkerPill() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TODAY • ${LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)).uppercase(Locale.getDefault())}",
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer) // Opaque to prevent overlapping ghost text
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TimelineEventRow(event: TimelineEvent) {
    val indicatorColor = when {
        event.category.equals("Exam", true) -> Color(0xFF8B5CF6)
        event.category.equals("Event", true) -> Color(0xFF3B82F6)
        else -> Color(0xFF4ADE80)
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Left Column: Date
        Column(
            modifier = Modifier.width(56.dp).padding(end = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(event.displayStartDayNum, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Text(event.startDay.uppercase(Locale.getDefault()).take(3), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        // Middle Column: Disconnected Node
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(indicatorColor, CircleShape)
        )

        // Right Column: Card
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = event.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(indicatorColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = event.category.uppercase(Locale.getDefault()),
                            color = indicatorColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}