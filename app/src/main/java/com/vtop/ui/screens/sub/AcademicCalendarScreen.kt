package com.vtop.ui.screens.sub

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
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
import com.vtop.ui.core.AppBridge
import com.vtop.ui.core.GlobalSyncer
import com.vtop.utils.AnalyticsManager
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class TimelineEvent(
    val startDate: Date,
    val endDate: Date,
    val displayStartDate: String,
    val displayEndDate: String,
    val startDay: String,
    val endDay: String,
    val title: String,
    val category: String
)

data class ParsedCalendarDay(
    val date: Date,
    val day: String,
    val title: String,
    val category: String
)

// Helper function to extract "2025-26" from "AP2025267" or "AMR2017182"
fun extractYearFromSemId(semId: String): String {
    val regex = Regex("(\\d{4})(\\d{2})")
    val match = regex.find(semId)
    return if (match != null) {
        "${match.groupValues[1]}-${match.groupValues[2]}"
    } else {
        "Legacy"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SimpleDateFormat")
@Composable
fun AcademicCalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { AnalyticsManager.logScreenView("Academic_Calendar_Screen") }

    // -- SEPARATED SEMESTER SELECTION STATE --
    var availableSemesters by remember { mutableStateOf(Vault.getCalendarSemesterOptions(context).toList()) }
    var isFetchingSemesters by remember { mutableStateOf(false) }

    // Default to the App's Active Profile Semester
    var selectedSemId by remember { mutableStateOf(Vault.getSelectedSemester(context)[0]) }
    var selectedSemName by remember { mutableStateOf(Vault.getSelectedSemester(context)[1]) }

    // Bottom Sheet State
    var showSemesterSheet by remember { mutableStateOf(false) }

    // Fetch the dedicated calendar semesters in the background if missing
    LaunchedEffect(Unit) {
        if (availableSemesters.isEmpty()) {
            isFetchingSemesters = true
            withContext(Dispatchers.IO) {
                try {
                    val client = AppBridge.activeClient
                    if (client != null) {
                        val fetched = client.fetchCalendarSemesters().toList()
                        if (fetched.isNotEmpty()) {
                            Vault.saveCalendarSemesterOptions(context, fetched)
                            withContext(Dispatchers.Main) { availableSemesters = fetched }
                        }
                    }
                } catch (ignored: Exception) {
                } finally {
                    isFetchingSemesters = false
                }
            }
        }
    }

    // -- CALENDAR DATA STATE --
    var rawEvents by remember { mutableStateOf(emptyList<com.vtop.models.AcademicCalendarEvent>()) }

    // Progress UI State
    var isSyncing by remember { mutableStateOf(false) }
    var syncTotalSteps by remember { mutableIntStateOf(0) }
    var syncCompletedSteps by remember { mutableIntStateOf(0) }
    var syncError by remember { mutableStateOf<String?>(null) }

    // Live Sync Status from GlobalSyncer for UI feedback
    val globalSyncStatus = AppBridge.syncStatus.value

    // Reusable sync function
    val fetchCalendar = { semIdToFetch: String ->
        coroutineScope.launch(Dispatchers.IO) {
            isSyncing = true
            syncError = null
            syncTotalSteps = 0
            syncCompletedSteps = 0

            try {
                // 1. AUTO-HEAL DEAD SESSIONS (FAST-TRACK)
                if (AppBridge.activeClient == null) {
                    withContext(Dispatchers.Main) { syncError = "RECONNECTING" }

                    // Trigger GlobalSyncer silently in the background
                    if (!GlobalSyncer.isSyncing.value) {
                        GlobalSyncer.performSync(context, forceNewSession = true)
                    }

                    // Suspend execution ONLY until the login phase is complete
                    while (GlobalSyncer.isSyncing.value) {
                        val status = AppBridge.syncStatus.value
                        // As soon as it starts fetching actual modules, authentication is done
                        if (status.startsWith("Syncing") || status == "Finishing up...") {
                            break
                        }
                        delay(300L) // Fast polling
                    }

                    // Check if resurrection was successful
                    if (AppBridge.activeClient == null) {
                        throw Exception("Failed to re-establish VTOP session. Please check credentials.")
                    }
                }

                withContext(Dispatchers.Main) { syncError = null }
                val client = AppBridge.activeClient!!

                // 2. FETCH CALENDAR DATA
                val availableDates = client.fetchCalendarMonths(semIdToFetch, "ALL")

                if (availableDates.isNotEmpty()) {
                    withContext(Dispatchers.Main) { syncTotalSteps = availableDates.size }

                    val allEvents = mutableListOf<com.vtop.models.AcademicCalendarEvent>()
                    for (dateStr in availableDates) {
                        val html = client.fetchCalendarRawHtml(semIdToFetch, dateStr, "ALL")

                        if (!html.isNullOrBlank()) {
                            val monthlyEvents = com.vtop.logic.CalendarParser.parseCalendarHtml(html)
                            allEvents.addAll(monthlyEvents)
                        }
                        delay(250L) // Small breather for WAF
                        withContext(Dispatchers.Main) { syncCompletedSteps++ }
                    }

                    if (allEvents.isNotEmpty()) {
                        Vault.saveAcademicCalendar(context, semIdToFetch, allEvents)
                        withContext(Dispatchers.Main) {
                            // Ensure the user hasn't switched tabs while this was loading
                            if (selectedSemId == semIdToFetch) {
                                rawEvents = allEvents.toList()
                            }
                        }
                    } else {
                        throw Exception("Failed to extract calendar data.")
                    }
                } else {
                    throw Exception("No valid calendar dates found for this semester.")
                }
            } catch (ignored: Exception) {
                withContext(Dispatchers.Main) { syncError = ignored.message ?: "Sync Failed" }
            } finally {
                withContext(Dispatchers.Main) { isSyncing = false }
            }
        }
    }

    // Auto-load or Auto-sync when the selected semester changes
    LaunchedEffect(selectedSemId) {
        val cachedEvents = Vault.getAcademicCalendar(context, selectedSemId).toList()
        rawEvents = cachedEvents

        // If the vault is empty, trigger the sync automatically
        if (cachedEvents.isEmpty()) {
            fetchCalendar(selectedSemId)
        }
    }

    val todayDate = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    // Convert raw data into clean Timeline Events
    val parsedTimeline = remember(rawEvents) {
        if (rawEvents.isEmpty()) return@remember emptyList()

        val sdfParse = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val sdfDisplay = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)

        val validDays = rawEvents.mapNotNull { event ->
            val dateObj = try { sdfParse.parse(event.date) } catch (ignored: Exception) { null } ?: return@mapNotNull null

            var cleanTitle = event.particulars
                .replace(" - General (Semester)", "")
                .replace(" - Combined", "")
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val cal = Calendar.getInstance().apply { time = dateObj }

            // --- NEW SUNDAY LOGIC: Guilty until proven innocent ---
            val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
            var dropSunday = false

            if (isSunday) {
                dropSunday = true // Default: Ignore all Sundays

                // Exception 1: Real Instructional Day (Safeguarded against "No" and "Non")
                val isRealInstructional = cleanTitle.contains("Instructional", ignoreCase = true) &&
                        !cleanTitle.contains("No ", ignoreCase = true) &&
                        !cleanTitle.contains("Non", ignoreCase = true)

                // Exception 2: Specific Holiday or Event (Ugadi, VITOPIA, Exams)
                // We keep it as long as it's NOT a generic VTOP placeholder
                val isGenericPlaceholder = cleanTitle.equals("Holiday", ignoreCase = true) ||
                        cleanTitle.equals("Holiday (Holiday)", ignoreCase = true) ||
                        cleanTitle.contains("Sunday", ignoreCase = true) ||
                        cleanTitle.contains("Instructional Day (Holiday)", ignoreCase = true) ||
                        cleanTitle.contains("No Instructional", ignoreCase = true)

                if (isRealInstructional || !isGenericPlaceholder) {
                    dropSunday = false // Save it!
                }
            }

            // --- MONDAY LOGIC ---
            val isMonday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY
            val dropGenericMonday = isMonday && (
                    cleanTitle.equals("No Instructional Day (Non Instructional Day)", ignoreCase = true) ||
                            cleanTitle.equals("No Instructional Day (No Instructional Day)", ignoreCase = true)
                    )

            // If it failed the Sunday or Monday checks, throw it in the trash
            if (dropSunday || dropGenericMonday) return@mapNotNull null

            // --- Normalize Titles for Perfect Grouping ---
            if (cleanTitle.contains("VITOPIA", ignoreCase = true)) {
                cleanTitle = "VITOPIA"
            } else if (cleanTitle.contains("CAT - I", ignoreCase = true) || cleanTitle.contains("Continuous Assessment Test - I", ignoreCase = true)) {
                cleanTitle = "CAT - I (Exam)"
            } else if (cleanTitle.contains("CAT - II", ignoreCase = true) || cleanTitle.contains("Continuous Assessment Test - II", ignoreCase = true)) {
                cleanTitle = "CAT - II (Exam)"
            } else if (cleanTitle.contains("Lab FAT", ignoreCase = true) || cleanTitle.contains("Laboratory FAT", ignoreCase = true)) {
                cleanTitle = "Lab FAT (Exam)"
            } else if (cleanTitle.contains("FAT", ignoreCase = true) || cleanTitle.contains("Final Assessment Test", ignoreCase = true)) {
                cleanTitle = "FAT (Exam)"
            } else if (cleanTitle.contains("Instructional Day", ignoreCase = true) && !cleanTitle.contains("No Instructional", ignoreCase = true) && !cleanTitle.contains("Non Instructional", ignoreCase = true)) {
                cleanTitle = "Instructional Day"
            }

            val category = when {
                cleanTitle.contains("Exam", true) || cleanTitle.contains("CAT", true) || cleanTitle.contains("FAT", true) -> "Exam"
                cleanTitle.contains("Holiday", true) || cleanTitle.contains("no instructional", true) || cleanTitle.contains("non instructional", true) || cleanTitle.contains("VITOPIA", true) -> "Holiday"
                else -> "Event"
            }
            ParsedCalendarDay(
                date = dateObj,
                day = event.day.trim(),
                title = cleanTitle,
                category = category
            )
        }.sortedBy { it.date.time }

        val groupedEvents = mutableListOf<TimelineEvent>()
        var currentStart: Date? = null
        var currentEnd: Date? = null
        var currentStartDay = ""
        var currentEndDay = ""
        var currentTitle = ""
        var currentCategory = ""

        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        for (item in validDays) {
            val date = item.date
            val day = item.day
            val title = item.title
            val category = item.category

            val isConsecutive = currentEnd != null && (date.time - currentEnd.time) <= 350_000_000L
            val crossesTodayBoundary = currentEnd != null && currentEnd.before(todayMidnight) && !date.before(todayMidnight)

            if (currentTitle == title && isConsecutive && !crossesTodayBoundary) {
                currentEnd = date
                currentEndDay = day
            } else {
                if (currentStart != null) {
                    val finalEnd = currentEnd ?: currentStart
                    groupedEvents.add(
                        TimelineEvent(
                            startDate = currentStart,
                            endDate = finalEnd,
                            displayStartDate = sdfDisplay.format(currentStart),
                            displayEndDate = sdfDisplay.format(finalEnd),
                            startDay = currentStartDay,
                            endDay = currentEndDay,
                            title = currentTitle,
                            category = currentCategory
                        )
                    )
                }
                currentStart = date
                currentEnd = date
                currentStartDay = day
                currentEndDay = day
                currentTitle = title
                currentCategory = category
            }
        }

        if (currentStart != null) {
            val finalEnd = currentEnd ?: currentStart
            groupedEvents.add(
                TimelineEvent(
                    startDate = currentStart,
                    endDate = finalEnd,
                    displayStartDate = sdfDisplay.format(currentStart),
                    displayEndDate = sdfDisplay.format(finalEnd),
                    startDay = currentStartDay,
                    endDay = currentEndDay,
                    title = currentTitle,
                    category = currentCategory
                )
            )
        }
        groupedEvents
    }

    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Exams", "Holidays", "Events")

    val filteredEvents = remember(parsedTimeline, selectedFilter) {
        if (selectedFilter == "All") parsedTimeline
        else parsedTimeline.filter { it.category.equals(selectedFilter.trimEnd('s'), ignoreCase = true) }
    }

    val nextExam = remember(parsedTimeline) {
        parsedTimeline.filter { it.category == "Exam" && !it.endDate.before(todayDate) }.minByOrNull { it.startDate.time }
    }

    val nextHoliday = remember(parsedTimeline) {
        parsedTimeline.filter { it.category == "Holiday" && !it.endDate.before(todayDate) }.minByOrNull { it.startDate.time }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Academic Calendar", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { fetchCalendar(selectedSemId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Calendar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // --- BOTTOM SHEET SELECTOR TRIGGER ---
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .clickable { showSemesterSheet = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedSemName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select Semester",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- BOTTOM SHEET IMPLEMENTATION ---
            if (showSemesterSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

                val groupedSemesters = remember(availableSemesters) {
                    availableSemesters.groupBy { extractYearFromSemId(it.id) }
                        .toSortedMap(compareByDescending { it })
                }
                val yearTabs = groupedSemesters.keys.toList()

                var selectedYearTab by remember {
                    mutableStateOf(extractYearFromSemId(selectedSemId).takeIf { yearTabs.contains(it) } ?: yearTabs.firstOrNull() ?: "")
                }

                ModalBottomSheet(
                    onDismissRequest = { showSemesterSheet = false },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    dragHandle = { BottomSheetDefaults.DragHandle() }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                        Text(
                            text = "Select Semester",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                        )

                        if (isFetchingSemesters) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (yearTabs.isEmpty()) {
                            Text(
                                "No semesters available.",
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ScrollableTabRow(
                                selectedTabIndex = yearTabs.indexOf(selectedYearTab).coerceAtLeast(0),
                                containerColor = Color.Transparent,
                                edgePadding = 24.dp,
                                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)) },
                                indicator = { tabPositions ->
                                    val index = yearTabs.indexOf(selectedYearTab).coerceAtLeast(0)
                                    if (index < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[index]),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            ) {
                                yearTabs.forEach { year ->
                                    Tab(
                                        selected = year == selectedYearTab,
                                        onClick = { selectedYearTab = year },
                                        text = {
                                            Text(
                                                text = year,
                                                fontWeight = if (year == selectedYearTab) FontWeight.Bold else FontWeight.Medium,
                                                color = if (year == selectedYearTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }

                            val semestersInActiveYear = groupedSemesters[selectedYearTab] ?: emptyList()

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(items = semestersInActiveYear) { option ->
                                    val isSelected = option.id == selectedSemId

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedSemId = option.id
                                                selectedSemName = option.name
                                                showSemesterSheet = false
                                            }
                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.name,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- MAIN CONTENT ---
            if (rawEvents.isEmpty() || isSyncing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        if (syncError == "RECONNECTING") {
                            // SHOW LIVE LOGIN STATUS
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Reconnecting to VTOP...\n$globalSyncStatus",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        } else if (isSyncing) {
                            if (syncTotalSteps > 0) {
                                val progress = syncCompletedSteps.toFloat() / syncTotalSteps.toFloat()
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth(0.8f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Syncing $selectedSemName\nMonth $syncCompletedSteps of $syncTotalSteps",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Initializing calendar sync...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (syncError != null) {
                            Icon(Icons.Default.Event, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Sync Failed",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = syncError!!,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(
                                onClick = { fetchCalendar(selectedSemId) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Retry Sync", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(Icons.Default.Event, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No calendar events found.\nTap the refresh icon at the top to sync.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 40.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (nextExam != null || nextHoliday != null) {
                        item { NextEventDashboard(nextExam, nextHoliday, todayDate) }
                    }

                    item {
                        CategoryFilters(
                            options = filterOptions,
                            selectedOption = selectedFilter,
                            onOptionSelected = { selectedFilter = it }
                        )
                    }

                    if (filteredEvents.isEmpty()) {
                        item {
                            Text(
                                text = "No ${selectedFilter.lowercase()} scheduled.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                                    var todayMarkerPlaced = false

                                    filteredEvents.forEachIndexed { index, event ->
                                        // FIX: Check the endDate instead of startDate so ongoing merged blocks are caught!
                                        if (!todayMarkerPlaced && !event.endDate.before(todayDate)) {
                                            TodayDividerMarker()
                                            todayMarkerPlaced = true
                                        }

                                        val isLast = index == filteredEvents.lastIndex && todayMarkerPlaced
                                        TimelineEventRow(event, isLast)
                                    }

                                    if (!todayMarkerPlaced) {
                                        TodayDividerMarker()
                                    }
                                }
                            }
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = options) { option ->
            val isSelected = option == selectedOption
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor)
                    .clickable { onOptionSelected(option) }
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(text = option, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NextEventDashboard(nextExam: TimelineEvent?, nextHoliday: TimelineEvent?, today: Date) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (nextExam != null) {
            CountdownCard(
                modifier = Modifier.weight(1f),
                label = "NEXT EXAM",
                event = nextExam,
                today = today,
                accentColor = Color(0xFF8B5CF6)
            )
        }
        if (nextHoliday != null) {
            CountdownCard(
                modifier = Modifier.weight(1f),
                label = "NEXT HOLIDAY",
                event = nextHoliday,
                today = today,
                accentColor = Color(0xFF4ADE80)
            )
        }
    }
}

@Composable
fun CountdownCard(modifier: Modifier, label: String, event: TimelineEvent, today: Date, accentColor: Color) {
    val diff = event.startDate.time - today.time
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    val timeText = when {
        days < 0L -> "Ongoing"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        else -> "In $days days"
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(text = timeText, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(text = "${event.title} · ${event.displayStartDate.take(6)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TodayDividerMarker(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        Text(
            text = "TODAY",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    }
}

@SuppressLint("SimpleDateFormat")
@Composable
fun TimelineEventRow(event: TimelineEvent, isLast: Boolean) {
    val indicatorColor = when {
        event.category.equals("Exam", ignoreCase = true) -> Color(0xFF8B5CF6)
        event.category.equals("Event", ignoreCase = true) -> Color(0xFF3B82F6)
        else -> Color(0xFF4ADE80)
    }

    val sdfDay = remember { SimpleDateFormat("EEE", Locale.ENGLISH) }

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(80.dp).padding(end = 12.dp, top = 2.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = event.displayStartDate.take(6),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = sdfDay.format(event.startDate).uppercase(Locale.getDefault()),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (event.displayStartDate != event.displayEndDate) {
                Text(
                    text = "to",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                Text(
                    text = event.displayEndDate.take(6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = sdfDay.format(event.endDate).uppercase(Locale.getDefault()),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.size(14.dp).background(indicatorColor.copy(alpha = 0.2f), CircleShape).padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().background(indicatorColor, CircleShape))
            }

            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        Column(modifier = Modifier.weight(1f).padding(start = 16.dp, bottom = 24.dp)) {
            Text(text = event.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier.background(indicatorColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = event.category.uppercase(Locale.getDefault()),
                    color = indicatorColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}