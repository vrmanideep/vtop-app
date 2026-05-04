package com.vtop.ui.screens.main

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.utils.Vault
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.ui.text.style.TextAlign

data class AcademicEvent(
    val startDate: Date,
    val endDate: Date,
    val displayStartDate: String,
    val displayEndDate: String,
    val title: String,
    val category: String,
    val sortIndex: Long
)

data class SemesterCalendar(
    val semesterName: String,
    val startDateFormatted: String,
    val lastInstructionalDayFormatted: String,
    val weekOffs: String,
    val events: List<AcademicEvent>,
    val isCurrent: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SimpleDateFormat")
@Composable
fun AcademicCalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val todayDate = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    val semesters = remember {
        val list = mutableListOf<SemesterCalendar>()
        try {
            val jsonString = context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val activeSemName = Vault.getSelectedSemester(context)[1] ?: "Current Semester"

            val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "dd-MM", "dd/MM/yyyy")
            fun parseDateSafely(dateStr: String): Date? {
                for (f in formats) {
                    try {
                        val parsed = SimpleDateFormat(f, Locale.ENGLISH).parse(dateStr)
                        if (parsed != null) {
                            val cal = Calendar.getInstance().apply { time = parsed }
                            if (cal.get(Calendar.YEAR) == 1970) cal.set(Calendar.YEAR, 2026)
                            return cal.time
                        }
                    } catch (_: Exception) {}
                }
                return null
            }

            fun parseCategory(categoryObj: JSONObject, categoryName: String): List<AcademicEvent> {
                val rawList = mutableListOf<Pair<Date, String>>()
                val keys = categoryObj.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = categoryObj.get(key)

                    if (value is JSONArray) {
                        val title = key
                        for (i in 0 until value.length()) {
                            val dateStr = value.getString(i)
                            val date = parseDateSafely(dateStr)
                            if (date != null) rawList.add(Pair(date, title))
                        }
                    } else if (value is String) {
                        val title = value
                        val isGenericWeekend = title.equals("Sunday", true) || title.equals("Monday", true) || title.startsWith("Sunday (", true)

                        if (!isGenericWeekend) {
                            val date = parseDateSafely(key)
                            if (date != null) rawList.add(Pair(date, title))
                        }
                    }
                }

                rawList.sortBy { it.first.time }

                val groupedEvents = mutableListOf<AcademicEvent>()
                val sdfDisplay = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)

                var currentStart: Date? = null
                var currentEnd: Date? = null
                var currentTitle = ""

                for ((date, title) in rawList) {
                    if (currentTitle == title) {
                        currentEnd = date
                    } else {
                        if (currentStart != null) {
                            val finalEnd = currentEnd ?: currentStart
                            groupedEvents.add(
                                AcademicEvent(
                                    startDate = currentStart,
                                    endDate = finalEnd,
                                    displayStartDate = sdfDisplay.format(currentStart),
                                    displayEndDate = sdfDisplay.format(finalEnd),
                                    title = currentTitle,
                                    category = categoryName,
                                    sortIndex = currentStart.time
                                )
                            )
                        }
                        currentStart = date
                        currentEnd = date
                        currentTitle = title
                    }
                }

                if (currentStart != null) {
                    val finalEnd = currentEnd ?: currentStart
                    groupedEvents.add(
                        AcademicEvent(
                            startDate = currentStart,
                            endDate = finalEnd,
                            displayStartDate = sdfDisplay.format(currentStart),
                            displayEndDate = sdfDisplay.format(finalEnd),
                            title = currentTitle,
                            category = categoryName,
                            sortIndex = currentStart.time
                        )
                    )
                }
                return groupedEvents
            }

            val semesterKeys = root.keys()
            val sdfDisplay = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            val sdfParse = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

            while (semesterKeys.hasNext()) {
                val semKey = semesterKeys.next()
                if (semKey == "blocked_dates") continue // Ignore legacy array structure

                val semObj = root.optJSONObject(semKey)

                if (semObj != null) {
                    val semesterEvents = mutableListOf<AcademicEvent>()
                    if (semObj.has("holidays")) semesterEvents.addAll(parseCategory(semObj.getJSONObject("holidays"), "Holiday"))
                    if (semObj.has("exams")) semesterEvents.addAll(parseCategory(semObj.getJSONObject("exams"), "Exam"))
                    if (semObj.has("events")) semesterEvents.addAll(parseCategory(semObj.getJSONObject("events"), "Event"))

                    semesterEvents.sortBy { it.sortIndex }
                    val isCurrent = semKey.equals(activeSemName, ignoreCase = true) || semObj.optString("is_current") == "true"

                    // Extract and format new metadata fields
                    val startDateRaw = semObj.optString("start_date", "")
                    val lastDayRaw = semObj.optString("last_instructional_day", "")

                    val startDateFmt = try { sdfDisplay.format(sdfParse.parse(startDateRaw)!!) } catch(e: Exception) { startDateRaw }
                    val lastDayFmt = try { sdfDisplay.format(sdfParse.parse(lastDayRaw)!!) } catch(e: Exception) { lastDayRaw }

                    val weekOffArr = semObj.optJSONArray("week_off")
                    val weekOffs = if (weekOffArr != null) {
                        (0 until weekOffArr.length()).map { weekOffArr.getString(it) }.joinToString(", ")
                    } else {
                        "Sunday" // Fallback
                    }

                    list.add(SemesterCalendar(semKey, startDateFmt, lastDayFmt, weekOffs, semesterEvents, isCurrent))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    val nextExam = remember(semesters) {
        semesters.flatMap { it.events }
            .filter { it.category.equals("Exam", true) && !it.endDate.before(todayDate) }
            .minByOrNull { it.startDate.time }
    }

    val nextHoliday = remember(semesters) {
        semesters.flatMap { it.events }
            .filter { it.category.equals("Holiday", true) && !it.endDate.before(todayDate) }
            .minByOrNull { it.startDate.time }
    }

    var selectedFilter by remember { mutableStateOf("All") }
    val filterOptions = listOf("All", "Exams", "Holidays", "Events")

    // Filter the semesters based on chip selection
    val filteredSemesters = remember(semesters, selectedFilter) {
        semesters.mapNotNull { sem ->
            val filteredEvents = if (selectedFilter == "All") {
                sem.events
            } else {
                val targetCategory = if (selectedFilter == "Exams") "Exam" else if (selectedFilter == "Holidays") "Holiday" else "Event"
                sem.events.filter { it.category.equals(targetCategory, ignoreCase = true) }
            }
            if (filteredEvents.isEmpty() && selectedFilter != "All") null else sem.copy(events = filteredEvents)
        }
    }

    // Find the index of the "Current" semester for dimming calculations
    val currentSemIndex = remember(filteredSemesters) {
        val idx = filteredSemesters.indexOfFirst { it.isCurrent }
        if (idx >= 0) idx else 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Academic Calendar", fontSize = 20.sp, fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (semesters.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Event, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text("No calendar data found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (nextExam != null || nextHoliday != null) {
                    item {
                        NextEventDashboard(nextExam, nextHoliday, todayDate)
                    }
                }

                item {
                    CategoryFilters(
                        options = filterOptions,
                        selectedOption = selectedFilter,
                        onOptionSelected = { selectedFilter = it }
                    )
                }

                if (filteredSemesters.isEmpty()) {
                    item {
                        Text(
                            text = "No ${selectedFilter.lowercase()} scheduled.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    itemsIndexed(filteredSemesters) { index, semester ->
                        // Calculate opacity based on distance from current semester
                        val distance = kotlin.math.abs(index - currentSemIndex)
                        val baseOpacity = (1f - (distance * 0.25f)).coerceIn(0.35f, 1f)

                        SemesterTimelineCard(semester, todayDate, baseOpacity)
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
        items(options) { option ->
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
                Text(
                    text = option,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun NextEventDashboard(nextExam: AcademicEvent?, nextHoliday: AcademicEvent?, today: Date) {
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
                accentColor = Color(0xFF8B5CF6) // Sleek Purple
            )
        }
        if (nextHoliday != null) {
            CountdownCard(
                modifier = Modifier.weight(1f),
                label = "NEXT HOLIDAY",
                event = nextHoliday,
                today = today,
                accentColor = Color(0xFF4ADE80) // Green
            )
        }
    }
}

@Composable
fun CountdownCard(modifier: Modifier, label: String, event: AcademicEvent, today: Date, accentColor: Color) {
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
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = timeText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${event.title} · ${event.displayStartDate.take(6)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SemesterTimelineCard(semester: SemesterCalendar, todayDate: Date, baseOpacity: Float = 1f) {
    var expanded by remember { mutableStateOf(semester.isCurrent) }

    // If user expands a past/dimmed semester, animate it to full opacity (1f) so it's readable
    val targetOpacity = if (expanded) 1f else baseOpacity
    val animatedOpacity by animateFloatAsState(targetValue = targetOpacity, label = "opacityAnim")

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // When the card expands, wait a split second for the animation to finish, then auto-scroll to the TODAY marker
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(350)
            try {
                bringIntoViewRequester.bringIntoView()
            } catch (e: Exception) {
                // Ignore if the user interrupts the scroll manually
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedOpacity) // Applied the dimming/fade effect here!
            .animateContentSize(animationSpec = tween(300))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = semester.semesterName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (semester.isCurrent) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF4ADE80).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("CURRENT", color = Color(0xFF4ADE80), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                if (semester.startDateFormatted.isNotBlank() && semester.lastInstructionalDayFormatted.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("COMMENCEMENT", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(semester.startDateFormatted, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("LAST INSTRUCTIONAL DAY", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(semester.lastInstructionalDayFormatted, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (semester.weekOffs.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Regular Week Offs: ${semester.weekOffs}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    var todayMarkerPlaced = false

                    if (semester.events.isEmpty()) {
                        Text(
                            text = "No events matched the filter for this semester.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        )
                    } else {
                        semester.events.forEachIndexed { index, event ->
                            if (!todayMarkerPlaced && !event.startDate.before(todayDate)) {
                                // Attach the scroll requester to the TODAY marker
                                TodayDividerMarker(Modifier.bringIntoViewRequester(bringIntoViewRequester))
                                todayMarkerPlaced = true
                            }

                            val isLast = index == semester.events.lastIndex && todayMarkerPlaced
                            TimelineEventRow(event, isLast)
                        }

                        if (!todayMarkerPlaced) {
                            // If all events are in the past, place TODAY at the very end
                            TodayDividerMarker(Modifier.bringIntoViewRequester(bringIntoViewRequester))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodayDividerMarker(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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

@Composable
fun TimelineEventRow(event: AcademicEvent, isLast: Boolean) {
    val indicatorColor = when {
        event.category.equals("Exam", ignoreCase = true) -> Color(0xFF8B5CF6) // Purple
        event.category.equals("Event", ignoreCase = true) -> Color(0xFF3B82F6) // Blue
        else -> Color(0xFF4ADE80) // Green
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Left Column: Dates
        Column(
            modifier = Modifier
                .width(80.dp)
                .padding(end = 12.dp, top = 2.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = event.displayStartDate.take(6), // e.g. "14-Apr"
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (event.displayStartDate != event.displayEndDate) {
                Text(
                    text = "to",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
                Text(
                    text = event.displayEndDate.take(6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Center Column: The Timeline Line & Dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(indicatorColor.copy(alpha = 0.2f), CircleShape)
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().background(indicatorColor, CircleShape))
            }

            // Continuous vertical line bridging down to the next item
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Right Column: Title and Category Tags
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, bottom = 24.dp)
        ) {
            Text(
                text = event.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(indicatorColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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