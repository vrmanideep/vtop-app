package com.vtop.ui.screens.sub

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.logic.CoursePageParser
import com.vtop.models.CourseSection
import com.vtop.models.GroupedCourseOption
import com.vtop.models.SemesterOption
import com.vtop.models.TimetableModel
import com.vtop.network.VtopClient
import com.vtop.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursePageSettingsSheet(
    vtopClient: VtopClient?,
    timetable: TimetableModel?,
    semesters: List<SemesterOption>,
    currentSem: SemesterOption,
    isDevOptionsEnabled: Boolean,
    isRunningAutoDownload: Boolean = false,
    runProgressText: String = "",
    onRunNow: () -> Unit = {},
    onCancelRun: () -> Unit = {},
    onCheckDeletedFiles: () -> Unit = {},
    onSettingsChanged: (AutoDownloadSettings) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    var saveToDisk by remember { mutableStateOf(CourseFileManager.isSaveToDiskEnabled(context)) }
    var folderSizeLabel by remember { mutableStateOf("") }
    var autoOpenFiles by remember { mutableStateOf(CourseFileManager.isAutoOpenEnabled(context)) }
    var settings by remember {
        val loaded = CourseAutoDownloadConfig.load(context)
        val withDefaultSem = if (loaded.activeSemId.isBlank() && currentSem.id.isNotBlank()) {
            loaded.copy(activeSemId = currentSem.id)
        } else loaded
        mutableStateOf(withDefaultSem)
    }
    var showDeletedFilesDialog by remember { mutableStateOf(false) }
    var showForgottenFilesView by remember { mutableStateOf(false) }
    var isScanningDeleted by remember { mutableStateOf(false) }
    var deletedRecords by remember { mutableStateOf<List<DownloadedFileRecord>>(emptyList()) }
    var isRedownloadingDeleted by remember { mutableStateOf(false) }
    var redownloadProgress by remember { mutableStateOf("") }

    LaunchedEffect(saveToDisk, isRunningAutoDownload, isRedownloadingDeleted) {
        if (saveToDisk && !isRunningAutoDownload && !isRedownloadingDeleted) {
            val bytes = withContext(Dispatchers.IO) {
                CourseFileManager.computeCourseFilesFolderSizeBytes(context)
            }
            folderSizeLabel = CourseFileManager.formatFileSize(bytes)
        }
    }

    var isAutoDownloadExpanded by remember { mutableStateOf(settings.enabled) }
    var showDisableDiskWarning by remember { mutableStateOf(false) }

    // Semester dropdown & extra semester picker
    var semDropdownExpanded by remember { mutableStateOf(false) }
    var showAddSemDialog by remember { mutableStateOf(false) }

    // Material 3 TimePicker dialog
    var showTimePickerDialog by remember { mutableStateOf(false) }

    // Loading courses per semester
    var isLoadingCourses by remember { mutableStateOf(false) }

    // Multi-faculty picker state
    var editingCourseConfig by remember { mutableStateOf<AutoDownloadCourseConfig?>(null) }
    var pickerSections by remember { mutableStateOf<List<CourseSection>>(emptyList()) }
    var isLoadingPicker by remember { mutableStateOf(false) }

    BackHandler(enabled = editingCourseConfig == null && !showDeletedFilesDialog && !showForgottenFilesView) { onDismiss() }
    // Custom Class ID state (Dev Options)
    var customClassIdInput by remember { mutableStateOf("") }
    var isAddingCustomClass by remember { mutableStateOf(false) }
    var customClassError by remember { mutableStateOf<String?>(null) }

    // Interval input text
    var intervalText by remember { mutableStateOf(settings.intervalHours.toString()) }

    fun persistSettings(updated: AutoDownloadSettings) {
        val prev = settings
        settings = updated
        CourseAutoDownloadConfig.save(context, updated)

        val scheduleChanged = prev.enabled != updated.enabled ||
                prev.intervalEnabled != updated.intervalEnabled ||
                prev.intervalHours != updated.intervalHours ||
                prev.specificTimeEnabled != updated.specificTimeEnabled ||
                prev.specificTimes != updated.specificTimes ||
                prev.wifiOnly != updated.wifiOnly

        if (scheduleChanged) {
            onSettingsChanged(updated)
        }
    }

    fun syncCoursesForSemester(semId: String) {
        if (vtopClient == null || semId.isBlank()) return
        scope.launch {
            isLoadingCourses = true
            val html = withContext(Dispatchers.IO) { vtopClient.fetchCoursesForCoursePageRawHtml(semId) }
            val parsed: List<GroupedCourseOption> = html?.let { CoursePageParser.parseCourseOptions(it) } ?: emptyList()
            if (parsed.isNotEmpty()) {
                val existingByKey = settings.courseConfigs.associateBy { it.configKey }
                val mergedForSem = mutableListOf<AutoDownloadCourseConfig>()
                for (group in parsed) {
                    for (opt in group.types) {
                        val key = "${semId}_${group.courseCode}_${opt.courseType.trim().uppercase()}"
                        val existing = existingByKey[key]
                        if (existing != null) {
                            mergedForSem.add(existing)
                        } else {
                            mergedForSem.add(
                                AutoDownloadCourseConfig(
                                    semId = semId,
                                    courseCode = group.courseCode,
                                    courseTitle = group.courseTitle,
                                    courseType = opt.courseType,
                                    defaultClassId = opt.classId,
                                    enabled = true,
                                    faculties = emptyList()
                                )
                            )
                        }
                    }
                }
                val otherSemConfigs = settings.courseConfigs.filter { it.semId != semId }
                persistSettings(settings.copy(courseConfigs = otherSemConfigs + mergedForSem))
            }
            isLoadingCourses = false
        }
    }

    LaunchedEffect(settings.enabled, settings.activeSemId) {
        if (settings.enabled && settings.activeSemId.isNotBlank()) {
            val hasForSem = settings.courseConfigs.any { it.semId == settings.activeSemId }
            if (!hasForSem) {
                syncCoursesForSemester(settings.activeSemId)
            }
        }
    }

    val containerBg = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF18181B)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val trackedSemIds = remember(settings.activeSemId, settings.extraSemIds) {
        (listOf(settings.activeSemId) + settings.extraSemIds).filter { it.isNotBlank() }.distinct()
    }

    val userClassIds = remember(timetable) {
        timetable?.scheduleMap?.values?.flatten()?.mapNotNull { it.classId?.trim() }?.toSet() ?: emptySet()
    }

    // Map classId -> Pair(combinedSlots, facultyName)
    val defaultRegInfoByClassId = remember(timetable) {
        timetable?.scheduleMap?.values?.flatten()
            ?.filter { !it.classId.isNullOrBlank() }
            ?.groupBy { it.classId!!.trim() }
            ?.mapValues { (_, entries) ->
                val combinedSlots = entries
                    .asSequence()
                    .flatMap { it.slot.split("+").asSequence() }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedWith(
                        compareBy(
                            { it.uppercase().startsWith("L") && it.drop(1).toIntOrNull() != null },
                            { it.uppercase().startsWith("T") },
                            { it.length },
                            { it.drop(1).toIntOrNull() ?: Int.MAX_VALUE },
                            { it }
                        )
                    )
                    .joinToString("+")
                val fac = entries.firstOrNull { it.faculty.isNotBlank() }?.faculty?.trim() ?: ""
                combinedSlots to fac
            }
            ?: emptyMap()
    }

    val missingRegisteredFaculty = remember { mutableStateMapOf<String, Boolean>() }
    val resolvedRegLabelByKey = remember { mutableStateMapOf<String, String>() }

    // Check if registered faculty (by both slot and faculty name) exists on the course page
    LaunchedEffect(settings.enabled, settings.courseConfigs, vtopClient) {
        if (!settings.enabled || vtopClient == null) return@LaunchedEffect
        val toCheck = settings.courseConfigs.filter {
            it.faculties.isEmpty() && !missingRegisteredFaculty.containsKey(it.configKey)
        }
        for (cfg in toCheck) {
            val (regSlot, regFac) = defaultRegInfoByClassId[cfg.defaultClassId.trim()] ?: ("" to "")

            val html = withContext(Dispatchers.IO) {
                vtopClient.fetchSlotSectionsRawHtml(cfg.semId, cfg.defaultClassId)
            }
            val parsed = html?.let { CoursePageParser.parseSlotSections(it) } ?: emptyList()

            val regSlotTokens = regSlot.split("+")
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .toSet()

            val cleanRegFac = regFac.replace(Regex("(?i)\\b(dr|prof|mr|ms|mrs)\\.?\\s*"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            // First try exact classId match (works for any semester, including past/extra semesters)
            val matchedSection = parsed.firstOrNull { sec ->
                sec.classId.equals(cfg.defaultClassId.trim(), ignoreCase = true)
            } ?: parsed.firstOrNull { sec ->
                val secSlotTokens = sec.slot.split("+")
                    .map { it.trim().uppercase() }
                    .filter { it.isNotBlank() }
                    .toSet()
                val slotMatches = regSlotTokens.isNotEmpty() &&
                        secSlotTokens.isNotEmpty() &&
                        regSlotTokens.intersect(secSlotTokens).isNotEmpty()

                val cleanSecFac = sec.facultyName.replace(Regex("(?i)\\b(dr|prof|mr|ms|mrs)\\.?\\s*"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val facMatches = cleanRegFac.isNotBlank() && cleanSecFac.isNotBlank() && (
                        cleanSecFac.contains(cleanRegFac, ignoreCase = true) ||
                                cleanRegFac.contains(cleanSecFac, ignoreCase = true)
                        )

                regSlotTokens.isNotEmpty() && cleanRegFac.isNotBlank() && slotMatches && facMatches
            }

            if (matchedSection != null) {
                missingRegisteredFaculty[cfg.configKey] = false
                val label = listOf(matchedSection.slot.trim(), matchedSection.facultyName.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" - ")
                if (label.isNotBlank()) {
                    resolvedRegLabelByKey[cfg.configKey] = label
                }
            } else {
                missingRegisteredFaculty[cfg.configKey] = true
            }
        }
    }

    // --- FULL-SCREEN SUB-VIEW: MULTI-FACULTY PICKER ---
    editingCourseConfig?.let { targetCfg ->
        BackHandler { editingCourseConfig = null }

        var searchQuery by remember { mutableStateOf("") }
        val selectedMap = remember(targetCfg) {
            mutableStateMapOf<String, AutoDownloadFacultyTarget>().apply {
                targetCfg.faculties.forEach { put("${it.classId}_${it.erpId}", it) }
            }
        }
        val registeredSlot = remember(pickerSections, targetCfg, userClassIds) {
            pickerSections.firstOrNull {
                it.classId.equals(targetCfg.defaultClassId, ignoreCase = true) || userClassIds.contains(it.classId)
            }?.slot
        }
        val expandedSlots = remember(registeredSlot) {
            mutableStateMapOf<String, Boolean>().apply {
                if (registeredSlot != null) put(registeredSlot, true)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
                .imePadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { editingCourseConfig = null }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${targetCfg.courseCode} · Select Faculties",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Choose one or more faculties for auto-download",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            if (isLoadingPicker) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (pickerSections.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No faculties found for this course.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val filtered = remember(pickerSections, searchQuery) {
                    val q = searchQuery.trim()
                    if (q.isEmpty()) pickerSections
                    else pickerSections.filter {
                        it.facultyName.contains(q, ignoreCase = true) ||
                                it.slot.contains(q, ignoreCase = true) ||
                                it.erpId.contains(q, ignoreCase = true)
                    }
                }
                val grouped = remember(filtered) { filtered.groupBy { it.slot } }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search faculty or slot...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                if (selectedMap.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedMap.entries.toList().forEach { (key, fac) ->
                            InputChip(
                                selected = true,
                                onClick = { selectedMap.remove(key) },
                                label = {
                                    Text(
                                        text = "${fac.slot} · ${fac.facultyName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove faculty",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (slot, slotSections) ->
                        val isExpanded = searchQuery.isNotBlank() || expandedSlots[slot] == true
                        item(key = "pick_slot_$slot") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = containerBg),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (slot == registeredSlot) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedSlots[slot] = !isExpanded }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("$slot (${slotSections.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Icon(
                                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (isExpanded) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                        slotSections.forEach { sec ->
                                            val key = "${sec.classId}_${sec.erpId}"
                                            val isChecked = selectedMap.containsKey(key)
                                            val isUserFaculty = sec.classId.equals(targetCfg.defaultClassId, ignoreCase = true) ||
                                                    userClassIds.contains(sec.classId)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isUserFaculty) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        if (isChecked) selectedMap.remove(key)
                                                        else selectedMap[key] = AutoDownloadFacultyTarget(
                                                            erpId = sec.erpId,
                                                            classId = sec.classId,
                                                            facultyName = sec.facultyName,
                                                            slot = sec.slot
                                                        )
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            selectedMap[key] = AutoDownloadFacultyTarget(
                                                                erpId = sec.erpId,
                                                                classId = sec.classId,
                                                                facultyName = sec.facultyName,
                                                                slot = sec.slot
                                                            )
                                                        } else {
                                                            selectedMap.remove(key)
                                                        }
                                                    }
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = sec.facultyName,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isUserFaculty) FontWeight.Black else FontWeight.SemiBold,
                                                        color = if (isUserFaculty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${sec.erpId} · ${sec.classId}",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { editingCourseConfig = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val updated = settings.courseConfigs.map {
                                if (it.configKey == targetCfg.configKey) {
                                    it.copy(faculties = selectedMap.values.toList())
                                } else it
                            }
                            persistSettings(settings.copy(courseConfigs = updated))
                            editingCourseConfig = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (selectedMap.isEmpty()) "Save" else "Save (${selectedMap.size})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        return
    }

    // --- FULL-SCREEN SUB-VIEW: DELETED FILES CHECK & SELECTIVE RE-DOWNLOAD / FORGET ---
    if (showDeletedFilesDialog) {
        BackHandler(enabled = !isRedownloadingDeleted) { showDeletedFilesDialog = false }

        val selectedKeys = remember(deletedRecords) {
            mutableStateMapOf<String, Boolean>().apply {
                deletedRecords.forEach { put(it.key, true) }
            }
        }
        val selectedCount = selectedKeys.values.count { it }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = !isRedownloadingDeleted,
                    onClick = { showDeletedFilesDialog = false }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deleted Files",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!isScanningDeleted && deletedRecords.isNotEmpty()) {
                        Text(
                            text = "${deletedRecords.size} missing file${if (deletedRecords.size == 1) "" else "s"} found",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            when {
                isScanningDeleted -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text("Scanning disk for missing files...", fontSize = 13.sp)
                        }
                    }
                }
                isRedownloadingDeleted -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            Text(
                                text = redownloadProgress.ifBlank { "Re-downloading..." },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                deletedRecords.isEmpty() -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "All previously downloaded files are present on disk.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val selectAll = selectedCount < deletedRecords.size
                                deletedRecords.forEach { selectedKeys[it.key] = selectAll }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (selectedCount < deletedRecords.size) "Select All" else "Deselect All",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$selectedCount selected",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val groupedDeletedBySem = remember(deletedRecords, settings.courseConfigs, defaultRegInfoByClassId) {
                        deletedRecords
                            .map { rec ->
                                val cfgMatch = settings.courseConfigs.firstOrNull { cfg ->
                                    cfg.defaultClassId.equals(rec.classId, ignoreCase = true) ||
                                            cfg.faculties.any { it.classId.equals(rec.classId, ignoreCase = true) }
                                }
                                val facMatch = cfgMatch?.faculties?.firstOrNull {
                                    it.classId.equals(rec.classId, ignoreCase = true)
                                }
                                val (regSlot, regFac) = defaultRegInfoByClassId[rec.classId.trim()] ?: ("" to "")
                                val semId = rec.semSubId.ifBlank { cfgMatch?.semId ?: "" }
                                val slot = facMatch?.slot?.ifBlank { regSlot } ?: regSlot
                                val fac = facMatch?.facultyName?.ifBlank { regFac } ?: regFac
                                Triple(rec, semId, slot to fac)
                            }
                            .sortedWith(
                                compareByDescending<Triple<DownloadedFileRecord, String, Pair<String, String>>> { it.second }
                                    .thenBy { it.first.courseCode.uppercase() }
                                    .thenBy { if (formatSettingsTypeLabel(it.first.courseType) == "Theory") 0 else 1 }
                                    .thenBy { it.first.courseType.uppercase() }
                                    .thenBy { it.third.first.uppercase() }
                                    .thenBy { it.third.second.uppercase() }
                                    .thenBy { it.first.fileName.lowercase() }
                            )
                            .groupBy { it.second.ifBlank { "Other" } }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedDeletedBySem.forEach { (semId, semEntries) ->
                            val semName = semesters.find { it.id == semId }?.name ?: semId
                            item(key = "del_sem_$semId") {
                                Text(
                                    text = semName.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            val byCourse = semEntries.groupBy { it.first.courseCode.ifBlank { "Other" } }
                            byCourse.forEach { (courseCode, courseEntries) ->
                                val courseTitle = courseEntries.firstOrNull { it.first.courseTitle.isNotBlank() }?.first?.courseTitle ?: ""
                                item(key = "del_course_${semId}_$courseCode") {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = containerBg),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = courseCode,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (courseTitle.isNotBlank()) {
                                                    Text(
                                                        text = courseTitle,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            val byType = courseEntries.groupBy { formatSettingsTypeLabel(it.first.courseType) }
                                            byType.forEach { (typeLabel, typeEntries) ->
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                                Text(
                                                    text = typeLabel.uppercase(),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    letterSpacing = 0.8.sp
                                                )

                                                val bySlotFac = typeEntries.groupBy { it.third }
                                                bySlotFac.forEach { (slotFac, facEntries) ->
                                                    val (slot, faculty) = slotFac
                                                    val slotFacHeader = listOf(slot, faculty)
                                                        .filter { it.isNotBlank() }
                                                        .joinToString(" · ")

                                                    if (slotFacHeader.isNotBlank()) {
                                                        Text(
                                                            text = slotFacHeader,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        facEntries.forEach { (rec, _, _) ->
                                                            val checked = selectedKeys[rec.key] == true
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                                    .clickable { selectedKeys[rec.key] = !checked }
                                                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = checked,
                                                                    onCheckedChange = { selectedKeys[rec.key] = it }
                                                                )
                                                                Spacer(Modifier.width(4.dp))
                                                                Text(
                                                                    text = rec.fileName,
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onSurface,
                                                                    modifier = Modifier.weight(1f)
                                                                )
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
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            enabled = selectedCount > 0,
                            onClick = {
                                val toForgetRecords = deletedRecords.filter { selectedKeys[it.key] == true }
                                val toForgetKeys = toForgetRecords.map { it.key }
                                val metaToSave = toForgetRecords.map { rec ->
                                    val cfgMatch = settings.courseConfigs.firstOrNull { cfg ->
                                        cfg.defaultClassId.equals(rec.classId, ignoreCase = true) ||
                                                cfg.faculties.any { it.classId.equals(rec.classId, ignoreCase = true) }
                                    }
                                    val facMatch = cfgMatch?.faculties?.firstOrNull {
                                        it.classId.equals(rec.classId, ignoreCase = true)
                                    }
                                    val (regSlot, regFac) = defaultRegInfoByClassId[rec.classId.trim()] ?: ("" to "")
                                    ForgottenFileMeta(
                                        key = rec.key,
                                        semId = rec.semSubId.ifBlank { cfgMatch?.semId ?: "" },
                                        courseCode = rec.courseCode.ifBlank { cfgMatch?.courseCode ?: "Unknown" },
                                        courseTitle = rec.courseTitle.ifBlank { cfgMatch?.courseTitle ?: "" },
                                        courseType = rec.courseType.ifBlank { cfgMatch?.courseType ?: "" },
                                        slot = facMatch?.slot?.ifBlank { regSlot } ?: regSlot.ifBlank { "Unknown Slot" },
                                        facultyName = facMatch?.facultyName?.ifBlank { regFac } ?: regFac.ifBlank { "Registered Faculty" },
                                        lectureLabel = extractMaterialLabel(rec.key, rec.isSyllabus, rec.fileName),
                                        fileName = rec.fileName
                                    )
                                }
                                CourseAutoDownloadConfig.saveForgottenMeta(context, metaToSave)
                                CourseAutoDownloadConfig.addForgottenKeys(context, toForgetKeys)
                                toForgetKeys.forEach { CourseFileManager.removeDownloadRecord(context, it) }
                                persistSettings(settings.copy(forgottenKeys = settings.forgottenKeys + toForgetKeys))
                                deletedRecords = deletedRecords.filter { it.key !in toForgetKeys }
                                Toast.makeText(context, "Forgot ${toForgetKeys.size} file${if (toForgetKeys.size == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
                                if (deletedRecords.isEmpty()) showDeletedFilesDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Forget", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            enabled = selectedCount > 0,
                            onClick = {
                                if (vtopClient == null) {
                                    Toast.makeText(context, "Session expired. Sync first.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val targets = deletedRecords.filter { selectedKeys[it.key] == true }
                                scope.launch {
                                    isRedownloadingDeleted = true
                                    val (ok, fail) = com.vtop.sync.CourseAutoDownloadEngine.redownloadSelectedRecords(
                                        context = context,
                                        client = vtopClient,
                                        records = targets,
                                        onProgress = { redownloadProgress = it }
                                    )
                                    isRedownloadingDeleted = false
                                    redownloadProgress = ""
                                    Toast.makeText(
                                        context,
                                        if (fail == 0) "Re-downloaded $ok file${if (ok == 1) "" else "s"}"
                                        else "Re-downloaded $ok · $fail failed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    deletedRecords = withContext(Dispatchers.IO) {
                                        com.vtop.sync.CourseAutoDownloadEngine.findDeletedRecords(context)
                                    }
                                    if (deletedRecords.isEmpty()) showDeletedFilesDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Re-download ($selectedCount)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    // --- FULL-SCREEN SUB-VIEW: MANAGE FORGOTTEN FILES ---
    if (showForgottenFilesView) {
        BackHandler { showForgottenFilesView = false }

        val savedMeta = remember(settings.forgottenKeys) {
            CourseAutoDownloadConfig.loadForgottenMeta(context)
        }

        // Resolve metadata for every forgotten key (with fallback for legacy keys)
        val resolvedItems = remember(settings.forgottenKeys, savedMeta, settings.courseConfigs, defaultRegInfoByClassId) {
            settings.forgottenKeys.map { key ->
                savedMeta[key] ?: buildFallbackForgottenMeta(
                    key = key,
                    courseConfigs = settings.courseConfigs,
                    defaultRegInfoByClassId = defaultRegInfoByClassId
                )
            }
        }

        // Sort: Semester (newest first) -> Course -> Course Type (Theory before Lab) -> Slot -> Faculty -> Date/Lecture
        val groupedBySemester = remember(resolvedItems) {
            resolvedItems
                .sortedWith(
                    compareByDescending<ForgottenFileMeta> { it.semId }
                        .thenBy { it.courseCode.uppercase() }
                        .thenBy { if (formatSettingsTypeLabel(it.courseType) == "Theory") 0 else 1 }
                        .thenBy { it.courseType.uppercase() }
                        .thenBy { it.slot.uppercase() }
                        .thenBy { it.facultyName.uppercase() }
                        .thenBy { extractMaterialSortKey(it.key, it.lectureLabel) }
                )
                .groupBy { it.semId.ifBlank { "Other" } }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showForgottenFilesView = false }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Forgotten Files",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${resolvedItems.size} file${if (resolvedItems.size == 1) "" else "s"} ignored by auto-download",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

            if (resolvedItems.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No forgotten files.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedBySemester.forEach { (semId, semItems) ->
                        val semName = semesters.find { it.id == semId }?.name ?: semId
                        item(key = "fg_sem_$semId") {
                            Text(
                                text = semName.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        val byCourse = semItems.groupBy { it.courseCode.ifBlank { "Other" } }
                        byCourse.forEach { (courseCode, courseItems) ->
                            val courseTitle = courseItems.firstOrNull { it.courseTitle.isNotBlank() }?.courseTitle ?: ""
                            item(key = "fg_course_${semId}_$courseCode") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = containerBg),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = courseCode,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (courseTitle.isNotBlank()) {
                                                Text(
                                                    text = courseTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        val byType = courseItems.groupBy { formatSettingsTypeLabel(it.courseType) }
                                        byType.forEach { (typeLabel, typeItems) ->
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                            Text(
                                                text = typeLabel.uppercase(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                letterSpacing = 0.8.sp
                                            )

                                            val bySlotFaculty = typeItems.groupBy { it.slot to it.facultyName }
                                            bySlotFaculty.forEach { (slotFac, facItems) ->
                                                val (slot, faculty) = slotFac
                                                val slotFacHeader = listOf(slot, faculty)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" · ")

                                                if (slotFacHeader.isNotBlank()) {
                                                    Text(
                                                        text = slotFacHeader,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    facItems.forEach { item ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                                                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = item.lectureLabel.ifBlank { "Course Material" },
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (item.fileName.isNotBlank()) {
                                                                    Text(
                                                                        text = item.fileName,
                                                                        fontSize = 11.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    CourseAutoDownloadConfig.removeForgottenMeta(context, listOf(item.key))
                                                                    val updatedKeys = settings.forgottenKeys - item.key
                                                                    persistSettings(settings.copy(forgottenKeys = updatedKeys))
                                                                    Toast.makeText(context, "Removed from forgotten list", Toast.LENGTH_SHORT).show()
                                                                    if (updatedKeys.isEmpty()) showForgottenFilesView = false
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    Icons.Default.DeleteOutline,
                                                                    contentDescription = "Remove from forgotten",
                                                                    tint = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
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
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            CourseAutoDownloadConfig.clearForgottenMeta(context)
                            persistSettings(settings.copy(forgottenKeys = emptySet()))
                            Toast.makeText(context, "Cleared all forgotten files", Toast.LENGTH_SHORT).show()
                            showForgottenFilesView = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Restore All", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Course Page Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- 1. SAVE TO DISK CARD ---
            Card(
                colors = CardDefaults.cardColors(containerColor = containerBg),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Save downloaded course files to disk",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (saveToDisk) "Saved to Downloads/vtop_course_files" else "Saved to temporary cache (auto-clears)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = saveToDisk,
                            onCheckedChange = { enabled ->
                                if (!enabled && settings.enabled) {
                                    showDisableDiskWarning = true
                                } else {
                                    saveToDisk = enabled
                                    CourseFileManager.setSaveToDiskEnabled(context, enabled)
                                }
                            }
                        )
                    }

                    if (saveToDisk) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { CourseFileManager.openDownloadsFolder(context) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Open course files folder",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = if (folderSizeLabel.isNotBlank()) {
                                        "Downloads/vtop_course_files · $folderSizeLabel"
                                    } else {
                                        "Downloads/vtop_course_files"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Open",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-open files on download",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Automatically open files once download finishes",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoOpenFiles,
                            onCheckedChange = { enabled ->
                                autoOpenFiles = enabled
                                CourseFileManager.setAutoOpenEnabled(context, enabled)
                            }
                        )
                    }
                }
            }

            // --- 2. AUTO-DOWNLOAD CARD ---
            Card(
                colors = CardDefaults.cardColors(containerColor = containerBg),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .alpha(if (saveToDisk) 1f else 0.5f)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!saveToDisk) {
                                    Toast.makeText(context, "Turn on \"Save downloaded course files to disk\" first", Toast.LENGTH_SHORT).show()
                                } else if (settings.enabled) {
                                    isAutoDownloadExpanded = !isAutoDownloadExpanded
                                }
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-download course files",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (!saveToDisk) "Requires saving files to disk"
                                else "Automatically download new materials from selected faculties",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.enabled && saveToDisk,
                            onCheckedChange = { enabled ->
                                if (!saveToDisk) {
                                    Toast.makeText(context, "Turn on \"Save downloaded course files to disk\" first", Toast.LENGTH_SHORT).show()
                                    return@Switch
                                }
                                persistSettings(settings.copy(enabled = enabled))
                                isAutoDownloadExpanded = enabled
                            }
                        )
                    }

                    if (saveToDisk && settings.enabled && isAutoDownloadExpanded) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // --- SEMESTER DROPDOWN ---
                            val activeSemName = semesters.find { it.id == settings.activeSemId }?.name
                                ?: currentSem.name.ifBlank { settings.activeSemId }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "SEMESTER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )
                                Box {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable { semDropdownExpanded = true }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = activeSemName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    DropdownMenu(
                                        expanded = semDropdownExpanded,
                                        onDismissRequest = { semDropdownExpanded = false },
                                        modifier = Modifier.heightIn(max = 280.dp)
                                    ) {
                                        semesters.forEach { sem ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        sem.name,
                                                        fontWeight = if (sem.id == settings.activeSemId) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (sem.id == settings.activeSemId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    semDropdownExpanded = false
                                                    val cleanedExtras = settings.extraSemIds.filter { it != sem.id }
                                                    persistSettings(settings.copy(activeSemId = sem.id, extraSemIds = cleanedExtras))
                                                    syncCoursesForSemester(sem.id)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // --- SCHEDULE (INTERVAL + SPECIFIC TIMES) ---
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Text(
                                text = "SCHEDULE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Every", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    OutlinedTextField(
                                        value = intervalText,
                                        onValueChange = { raw ->
                                            val digits = raw.filter { it.isDigit() }.take(3)
                                            intervalText = digits
                                            digits.toIntOrNull()?.takeIf { it >= 1 }?.let { hrs ->
                                                persistSettings(settings.copy(intervalHours = hrs))
                                            }
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(68.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Text("hrs", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Switch(
                                    checked = settings.intervalEnabled,
                                    onCheckedChange = { persistSettings(settings.copy(intervalEnabled = it)) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("At specific times", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Runs daily at chosen times", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = settings.specificTimeEnabled,
                                    onCheckedChange = { persistSettings(settings.copy(specificTimeEnabled = it)) }
                                )
                            }

                            if (settings.specificTimeEnabled) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    settings.specificTimes.forEach { timeStr ->
                                        InputChip(
                                            selected = true,
                                            onClick = {
                                                persistSettings(settings.copy(specificTimes = settings.specificTimes - timeStr))
                                            },
                                            label = { Text(format24To12(timeStr), fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            trailingIcon = {
                                                Icon(Icons.Default.Close, contentDescription = "Remove time", modifier = Modifier.size(14.dp))
                                            }
                                        )
                                    }
                                    AssistChip(
                                        onClick = { showTimePickerDialog = true },
                                        label = { Text("Add time", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                    )
                                }
                            }

                            // --- OPTIONS (WI-FI ONLY & RE-DOWNLOAD DELETED) ---
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Wi-Fi only", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Pause scheduled downloads on mobile data", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = settings.wifiOnly,
                                    onCheckedChange = { persistSettings(settings.copy(wifiOnly = it)) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Re-download deleted files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Re-fetch missing files during auto-download", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = settings.redownloadDeleted,
                                    onCheckedChange = { persistSettings(settings.copy(redownloadDeleted = it)) }
                                )
                            }

                            if (settings.forgottenKeys.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        .clickable { showForgottenFilesView = true }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Forgotten files (${settings.forgottenKeys.size})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Ignored by auto-download · Tap to restore",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Manage",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // --- ACTION BUTTONS (FULL WIDTH, NO TRUNCATION) ---
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isRunningAutoDownload) {
                                    OutlinedButton(
                                        onClick = onCancelRun,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Cancel", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    Button(
                                        onClick = onRunNow,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Run Now", fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        showDeletedFilesDialog = true
                                        isScanningDeleted = true
                                        scope.launch {
                                            deletedRecords = withContext(Dispatchers.IO) {
                                                com.vtop.sync.CourseAutoDownloadEngine.findDeletedRecords(context)
                                            }
                                            isScanningDeleted = false
                                        }
                                        onCheckDeletedFiles()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Re-download Deleted Files Right Now", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Live progress or Last run status
                            if (isRunningAutoDownload && runProgressText.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Text(runProgressText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                }
                            } else if (settings.lastRun.timestamp > 0L) {
                                val timeFormatted = remember(settings.lastRun.timestamp) {
                                    SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(settings.lastRun.timestamp))
                                }
                                Text(
                                    text = "Last run: $timeFormatted · ${settings.lastRun.summary.ifBlank { "Completed" }}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // --- COURSES LIST (ACTIVE + EXTRA SEMESTERS) ---
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "COURSES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 1.sp
                                )
                                if (isLoadingCourses) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                }
                            }

                            trackedSemIds.forEach { semId ->
                                val isExtra = semId != settings.activeSemId
                                val semName = semesters.find { it.id == semId }?.name ?: semId
                                val semCourses = settings.courseConfigs.filter { it.semId == semId }

                                if (isExtra) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = semName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            onClick = {
                                                persistSettings(
                                                    settings.copy(
                                                        extraSemIds = settings.extraSemIds - semId,
                                                        courseConfigs = settings.courseConfigs.filter { it.semId != semId }
                                                    )
                                                )
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remove semester", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                if (semCourses.isEmpty() && !isLoadingCourses) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("No courses loaded for this semester.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(onClick = { syncCoursesForSemester(semId) }) {
                                            Text("Load", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    val groupedSemCourses = semCourses.groupBy { it.courseCode }
                                    groupedSemCourses.forEach { (courseCode, components) ->
                                        val courseTitle = components.firstOrNull()?.courseTitle ?: ""
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Column {
                                                    Text(
                                                        text = courseCode,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (courseTitle.isNotBlank()) {
                                                        Text(
                                                            text = courseTitle,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                components.forEachIndexed { idx, cfg ->
                                                    if (idx > 0) {
                                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                                    }
                                                    val regInfo = defaultRegInfoByClassId[cfg.defaultClassId.trim()]
                                                    val regLabel = regInfo?.let { (slot, fac) ->
                                                        listOf(slot, fac).filter { it.isNotBlank() }.joinToString(" - ")
                                                    }?.takeIf { it.isNotBlank() }
                                                        ?: resolvedRegLabelByKey[cfg.configKey]
                                                    val isRegMissing = cfg.faculties.isEmpty() && missingRegisteredFaculty[cfg.configKey] == true

                                                    val facSummary = when {
                                                        cfg.faculties.isNotEmpty() ->
                                                            cfg.faculties.joinToString("\n") { "${it.slot} - ${it.facultyName}" }
                                                        isRegMissing ->
                                                            "Please change the faculty — your registered faculty did not update the course page."
                                                        else ->
                                                            regLabel ?: "Default registered faculty"
                                                    }

                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = formatSettingsTypeLabel(cfg.courseType),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                            Spacer(Modifier.height(2.dp))
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = facSummary,
                                                                    fontSize = 11.sp,
                                                                    color = if (isRegMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    lineHeight = 15.sp,
                                                                    modifier = Modifier.weight(1f, fill = false)
                                                                )
                                                                IconButton(
                                                                    onClick = {
                                                                        if (vtopClient == null) {
                                                                            Toast.makeText(context, "Session expired. Sync first.", Toast.LENGTH_SHORT).show()
                                                                            return@IconButton
                                                                        }
                                                                        editingCourseConfig = cfg
                                                                        scope.launch {
                                                                            isLoadingPicker = true
                                                                            val html = withContext(Dispatchers.IO) {
                                                                                vtopClient.fetchSlotSectionsRawHtml(cfg.semId, cfg.defaultClassId)
                                                                            }
                                                                            pickerSections = html?.let { CoursePageParser.parseSlotSections(it) } ?: emptyList()
                                                                            isLoadingPicker = false
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(26.dp)
                                                                ) {
                                                                    Icon(
                                                                        Icons.Default.Edit,
                                                                        contentDescription = "Edit faculty",
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(15.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Spacer(Modifier.width(8.dp))
                                                        Switch(
                                                            checked = cfg.enabled,
                                                            onCheckedChange = { checked ->
                                                                val updated = settings.courseConfigs.map {
                                                                    if (it.configKey == cfg.configKey) it.copy(enabled = checked) else it
                                                                }
                                                                persistSettings(settings.copy(courseConfigs = updated))
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // --- + ADD ANOTHER SEMESTER ---
                            OutlinedButton(
                                onClick = { showAddSemDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add another semester", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // --- SPECIFIC CLASS ID (DEV OPTIONS ONLY) ---
                            if (isDevOptionsEnabled) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                Text(
                                    text = "Wanna download from a specific Class ID?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = customClassIdInput,
                                        onValueChange = {
                                            customClassIdInput = it
                                            customClassError = null
                                        },
                                        label = { Text("Class ID (e.g. AP2026272000416)", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Button(
                                        enabled = !isAddingCustomClass && customClassIdInput.isNotBlank(),
                                        onClick = {
                                            val trimmed = customClassIdInput.trim()
                                            val semId = CoursePageParser.extractSemIdFromClassId(trimmed)
                                            if (semId == null) {
                                                customClassError = "Invalid Class ID format."
                                                return@Button
                                            }
                                            if (vtopClient == null) {
                                                Toast.makeText(context, "Session expired. Sync first.", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            scope.launch {
                                                isAddingCustomClass = true
                                                customClassError = null
                                                val slotHtml = withContext(Dispatchers.IO) {
                                                    vtopClient.fetchSlotSectionsRawHtml(semId, trimmed)
                                                }
                                                val parsedSlots = slotHtml?.let { CoursePageParser.parseSlotSections(it) } ?: emptyList()
                                                val matchedSec = parsedSlots.firstOrNull { it.classId.equals(trimmed, ignoreCase = true) }
                                                if (matchedSec == null) {
                                                    customClassError = "The respective faculty did not update the course page. Please check VTOP."
                                                } else {
                                                    val detailHtml = withContext(Dispatchers.IO) {
                                                        vtopClient.fetchCourseDetailRawHtml(semId, matchedSec.erpId, matchedSec.classId)
                                                    }
                                                    val detail = detailHtml?.let { CoursePageParser.parseCourseDetail(it) }
                                                    val newTarget = CustomClassIdTarget(
                                                        semId = semId,
                                                        classId = matchedSec.classId,
                                                        erpId = matchedSec.erpId,
                                                        facultyName = matchedSec.facultyName,
                                                        slot = matchedSec.slot,
                                                        courseCode = detail?.courseCode ?: matchedSec.courseCode,
                                                        courseTitle = detail?.courseTitle ?: "",
                                                        courseType = detail?.courseType ?: "",
                                                        enabled = true
                                                    )
                                                    val deduped = settings.customClassTargets.filter { it.targetKey != newTarget.targetKey } + newTarget
                                                    persistSettings(settings.copy(customClassTargets = deduped))
                                                    customClassIdInput = ""
                                                }
                                                isAddingCustomClass = false
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isAddingCustomClass) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        } else {
                                            Text("Add", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (customClassError != null) {
                                    Text(
                                        text = customClassError!!,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                settings.customClassTargets.forEach { target ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${target.courseCode} · ${target.slot}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${target.facultyName} (${target.classId})",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    persistSettings(
                                                        settings.copy(customClassTargets = settings.customClassTargets.filter { it.targetKey != target.targetKey })
                                                    )
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Class ID", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                            Switch(
                                                checked = target.enabled,
                                                onCheckedChange = { checked ->
                                                    val updated = settings.customClassTargets.map {
                                                        if (it.targetKey == target.targetKey) it.copy(enabled = checked) else it
                                                    }
                                                    persistSettings(settings.copy(customClassTargets = updated))
                                                }
                                            )
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

    // --- MATERIAL 3 TIME PICKER DIALOG ---
    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(initialHour = 18, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            title = { Text("Select Time", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val formatted = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    if (!settings.specificTimes.contains(formatted)) {
                        persistSettings(settings.copy(specificTimes = (settings.specificTimes + formatted).sorted()))
                    }
                    showTimePickerDialog = false
                }) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // --- WARNING DIALOG WHEN DISABLING SAVE-TO-DISK WHILE AUTO-DOWNLOAD IS ON ---
    if (showDisableDiskWarning) {
        AlertDialog(
            onDismissRequest = { showDisableDiskWarning = false },
            title = { Text("Disable saving to disk?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Auto-download requires saving course files to disk. Turning this off will also disable auto-download.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisableDiskWarning = false
                    saveToDisk = false
                    CourseFileManager.setSaveToDiskEnabled(context, false)
                    isAutoDownloadExpanded = false
                    persistSettings(settings.copy(enabled = false))
                }) {
                    Text("Turn Off Both", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDiskWarning = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // --- ADD ANOTHER SEMESTER DIALOG ---
    if (showAddSemDialog) {
        val availableToAdd = semesters.filter { it.id !in trackedSemIds }
        AlertDialog(
            onDismissRequest = { showAddSemDialog = false },
            title = { Text("Add another semester", fontWeight = FontWeight.Bold) },
            text = {
                if (availableToAdd.isEmpty()) {
                    Text("All available semesters are already added.", fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(availableToAdd, key = { it.id }) { sem ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddSemDialog = false
                                        persistSettings(settings.copy(extraSemIds = (settings.extraSemIds + sem.id).distinct()))
                                        syncCoursesForSemester(sem.id)
                                    }
                            ) {
                                Text(sem.name, modifier = Modifier.padding(14.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddSemDialog = false }) { Text("Close") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

private fun formatSettingsTypeLabel(rawType: String): String = when (rawType.trim().uppercase()) {
    "ETH", "TH" -> "Theory"
    "ELA", "LO" -> "Lab"
    else -> rawType.ifBlank { "Course" }
}

private fun format24To12(time24: String): String {
    val parts = time24.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return time24
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val amPm = if (h >= 12) "PM" else "AM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "%d:%02d %s".format(h12, m, amPm)
}

private fun extractMaterialLabel(key: String, isSyllabus: Boolean = false, fileName: String = ""): String {
    if (isSyllabus || key.startsWith("courseSyllabusDownload/", ignoreCase = true)) {
        return "Syllabus"
    }
    // If we have the saved filename, extract real lecture number if present (e.g. "01_..." or "Lec01_...")
    if (fileName.isNotBlank()) {
        val lecMatch = Regex("^(?:Lec[_\\s-]?)?(\\d{1,3})[_\\s-]").find(fileName.trim())
        if (lecMatch != null) {
            val num = lecMatch.groupValues[1].toIntOrNull()
            if (num != null) return "Lecture $num"
        }
    }
    // Fallback from VTOP URL: downloadPdf/{sem}/{classId}/{typeId}/{date}
    val parts = key.split("/")
    if (parts.size >= 4 && parts[0].equals("downloadPdf", ignoreCase = true)) {
        val typeId = parts[3].trim()
        val date = parts.getOrNull(4)?.trim().orEmpty()
        val slotTag = when (typeId) {
            "19" -> "Ref 1"
            "20" -> "Ref 2"
            "21" -> "Ref 3"
            "2", "3", "4" -> "General Material"
            else -> "Material"
        }
        return if (date.isNotBlank()) "$date · $slotTag" else slotTag
    }
    return "Course Material"
}

private fun extractMaterialSortKey(key: String, label: String): String {
    if (label.equals("Syllabus", ignoreCase = true)) return "0000-00-00"
    // Sort chronologically by dd-MM-yyyy date in the key if present
    val dateMatch = Regex("(\\d{2})-(\\d{2})-(\\d{4})").find(key)
    if (dateMatch != null) {
        val (dd, mm, yyyy) = dateMatch.destructured
        return "$yyyy-$mm-$dd-$key"
    }
    return "9999-99-99-$label"
}

private fun buildFallbackForgottenMeta(
    key: String,
    courseConfigs: List<AutoDownloadCourseConfig>,
    defaultRegInfoByClassId: Map<String, Pair<String, String>>
): ForgottenFileMeta {
    val parts = key.split("/")
    var semId = ""
    var courseCode = ""
    var courseTitle = ""
    var courseType = ""
    var slot = ""
    var faculty = ""

    if (key.startsWith("courseSyllabusDownload/", ignoreCase = true) && parts.size >= 2) {
        courseCode = parts[1].split("_").getOrNull(1) ?: ""
        courseType = parts.getOrNull(2) ?: ""
        val cfg = courseConfigs.firstOrNull { it.courseCode.equals(courseCode, ignoreCase = true) }
        if (cfg != null) {
            semId = cfg.semId
            courseTitle = cfg.courseTitle
            val (regSlot, regFac) = defaultRegInfoByClassId[cfg.defaultClassId.trim()] ?: ("" to "")
            slot = cfg.faculties.firstOrNull()?.slot ?: regSlot
            faculty = cfg.faculties.firstOrNull()?.facultyName ?: regFac
        }
    } else if (parts.size >= 3 && parts[0].equals("downloadPdf", ignoreCase = true)) {
        semId = parts[1].trim()
        val classId = parts[2].trim()
        val cfg = courseConfigs.firstOrNull { c ->
            c.defaultClassId.equals(classId, ignoreCase = true) ||
                    c.faculties.any { it.classId.equals(classId, ignoreCase = true) }
        }
        val facTarget = cfg?.faculties?.firstOrNull { it.classId.equals(classId, ignoreCase = true) }
        val (regSlot, regFac) = defaultRegInfoByClassId[classId] ?: ("" to "")
        courseCode = cfg?.courseCode ?: "Unknown"
        courseTitle = cfg?.courseTitle ?: ""
        courseType = cfg?.courseType ?: ""
        slot = facTarget?.slot ?: regSlot
        faculty = facTarget?.facultyName ?: regFac
    }

    return ForgottenFileMeta(
        key = key,
        semId = semId,
        courseCode = courseCode.ifBlank { "Other" },
        courseTitle = courseTitle,
        courseType = courseType,
        slot = slot,
        facultyName = faculty,
        lectureLabel = extractMaterialLabel(key),
        fileName = ""
    )
}