package com.vtop.ui.screens.sub

import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.vtop.logic.CoursePageParser
import com.vtop.models.*
import com.vtop.network.VtopClient
import com.vtop.utils.CourseFileManager
import com.vtop.utils.NotificationHelper
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class CoursePageStep { COURSES, SECTIONS, DETAIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursePageScreen(
    vtopClient: VtopClient?,
    timetable: TimetableModel?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(CoursePageStep.COURSES) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Local semester state (never touches global Vault semester)
    var semesters by remember { mutableStateOf<List<SemesterOption>>(emptyList()) }
    var selectedSem by remember {
        val saved = Vault.getSelectedSemester(context)
        mutableStateOf(SemesterOption(saved[0] ?: "", saved[1] ?: "Select Semester"))
    }
    var semDropdownExpanded by remember { mutableStateOf(false) }

    var groupedCourses by remember { mutableStateOf<List<GroupedCourseOption>>(emptyList()) }
    var sections by remember { mutableStateOf<List<CourseSection>>(emptyList()) }
    var activeTargetClassId by remember { mutableStateOf("") }
    var courseDetail by remember { mutableStateOf<CourseDetailData?>(null) }

    // Direct lookup inputs
    var directClassId by remember { mutableStateOf("") }
    var directClassId2 by remember { mutableStateOf("") }
    var directFacultyId by remember { mutableStateOf("") }

    // Track downloaded state & active downloads
    var downloadingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshDownloadTick by remember { mutableIntStateOf(0) }
    var sectionSearchQuery by remember { mutableStateOf("") }
    var downloadedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isDevOptionsEnabled = remember { CourseFileManager.isDevOptionsEnabled(context) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var saveToDiskEnabled by remember { mutableStateOf(CourseFileManager.isSaveToDiskEnabled(context)) }

    LaunchedEffect(courseDetail, refreshDownloadTick) {
        val detail = courseDetail ?: return@LaunchedEffect
        val allKeys = buildList {
            detail.syllabusPath?.let { add(it) }
            detail.allMaterialsPath?.let { add(it) }
            add("course_plan_${detail.classId}")
            detail.generalMaterials.forEach { add(it.relativePath.ifBlank { it.label }) }
            detail.lectures.forEach { lec ->
                lec.materials.filter { !it.isWebLink }.forEach { add(it.relativePath.ifBlank { it.label }) }
            }
        }
        downloadedKeys = withContext(Dispatchers.IO) {
            CourseFileManager.getDownloadedKeys(context, allKeys)
        }
    }

    val userClassIds = remember(timetable) {
        timetable?.scheduleMap?.values?.flatten()?.mapNotNull { it.classId?.trim() }?.toSet() ?: emptySet()
    }

    fun loadCoursesForSem(semId: String) {
        if (vtopClient == null || semId.isBlank()) return
        scope.launch {
            isLoading = true
            errorMsg = null
            val html = withContext(Dispatchers.IO) { vtopClient.fetchCoursesForCoursePageRawHtml(semId) }
            if (html != null) {
                groupedCourses = CoursePageParser.parseCourseOptions(html)
            } else {
                errorMsg = "Session expired or failed to load courses."
            }
            isLoading = false
        }
    }

    fun loadSections(semId: String, classId: String) {
        if (vtopClient == null) {
            Toast.makeText(context, "Session expired. Sync first.", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            errorMsg = null
            activeTargetClassId = classId.trim()
            val html = withContext(Dispatchers.IO) { vtopClient.fetchSlotSectionsRawHtml(semId, classId.trim()) }
            val parsed = html?.let { CoursePageParser.parseSlotSections(it) } ?: emptyList()
            if (parsed.isNotEmpty()) {
                sections = parsed
                step = CoursePageStep.SECTIONS
            } else {
                Toast.makeText(context, "No faculty/sections found for $classId", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    fun loadDetail(semId: String, erpId: String, classId: String) {
        if (vtopClient == null) {
            Toast.makeText(context, "Session expired. Sync first.", Toast.LENGTH_SHORT).show()
            return
        }
        focusManager.clearFocus()
        scope.launch {
            isLoading = true
            errorMsg = null
            val html = withContext(Dispatchers.IO) { vtopClient.fetchCourseDetailRawHtml(semId, erpId.trim(), classId.trim()) }
            val parsed = html?.let { CoursePageParser.parseCourseDetail(it) }
            if (parsed != null) {
                courseDetail = parsed
                step = CoursePageStep.DETAIL
            } else {
                Toast.makeText(context, "Failed to load course details.", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    fun handleMaterialClick(
        detail: CourseDetailData,
        item: CourseMaterialItem,
        slNo: String = "",
        date: String = "",
        topic: String = "",
        isGeneral: Boolean = false,
        isLecture: Boolean = false,
        isSyllabus: Boolean = false
    ) {
        if (item.isWebLink) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, item.relativePath.toUri()))
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
            }
            return
        }
        val key = item.relativePath.ifBlank { item.label }
        val existingRecord = CourseFileManager.getDownloadRecord(context, key)
        if (existingRecord != null) {
            val existingUri = CourseFileManager.findExistingUri(context, existingRecord)
            if (existingUri != null) {
                CourseFileManager.openFile(context, existingUri, existingRecord.fileName)
                return
            }
        }
        if (vtopClient == null) {
            Toast.makeText(context, "Session expired. Sync first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (downloadingKeys.contains(key)) return

        val fallbackExt = when {
            item.isZip -> "zip"
            item.isCoursePlan -> "xlsx"
            else -> "pdf"
        }

        scope.launch {
            val notifId = key.hashCode()
            downloadingKeys = downloadingKeys + key
            NotificationHelper.showDownloadProgressNotification(
                context = context,
                notificationId = notifId,
                fileName = "${detail.courseCode} · ${item.label}",
                progress = -1
            )

            val tempFile = File(context.cacheDir, "tmp_${System.currentTimeMillis()}")
            val result = withContext(Dispatchers.IO) {
                vtopClient.downloadCourseMaterial(item.relativePath, item.isCoursePlan, detail.semSubId, detail.classId, tempFile)
            }

            NotificationHelper.dismissNotification(context, notifId)

            if (result.success) {
                val ext = CourseFileManager.extractExtension(result.serverFileName, item.fileNameHint, fallbackExt)
                val code = CourseFileManager.sanitizePart(detail.courseCode, 20)
                val snoPadded = slNo.toIntOrNull()?.let { "%02d".format(it) } ?: slNo.padStart(2, '0')
                val customDate = CourseFileManager.sanitizePart(date, 20)
                val safeTopic = CourseFileManager.sanitizePart(topic.ifBlank { item.label }, 40)

                val baseName = when {
                    item.isZip -> {
                        val safeSlot = CourseFileManager.sanitizePart(detail.slot, 20)
                        val facOnly = detail.facultyName.split(" - ").getOrNull(1) ?: detail.facultyName
                        val safeFac = CourseFileManager.sanitizePart(facOnly, 30)
                        "${code}_all_${safeSlot}_${safeFac}"
                    }
                    isLecture -> {
                        listOf("${code}_L${snoPadded}", customDate, safeTopic)
                            .filter { it.isNotBlank() }
                            .joinToString("_")
                    }
                    isGeneral -> {
                        val pathDate = item.relativePath.substringAfterLast('/', "").takeIf { it.contains("-") } ?: ""
                        val gmDate = CourseFileManager.sanitizePart(date.ifBlank { pathDate }, 20)
                        listOf("${code}_GM${snoPadded}", gmDate, safeTopic)
                            .filter { it.isNotBlank() }
                            .joinToString("_")
                    }
                    item.isCoursePlan -> {
                        val safeSlot = CourseFileManager.sanitizePart(detail.slot, 20)
                        val facOnly = detail.facultyName.split(" - ").getOrNull(1) ?: detail.facultyName
                        val safeFac = CourseFileManager.sanitizePart(facOnly, 30)
                        "${code}_${safeSlot}_${safeFac}_CoursePlan"
                    }
                    else -> "${code}_${CourseFileManager.sanitizePart(item.label, 30)}"
                }
                val finalName = "$baseName.$ext"

                val subPath = CourseFileManager.buildCourseSubPath(
                    courseCode = detail.courseCode,
                    courseTitle = detail.courseTitle,
                    courseType = detail.courseType,
                    isSyllabus = isSyllabus
                )
                val uri = withContext(Dispatchers.IO) {
                    CourseFileManager.persistDownloadedFile(context, tempFile, finalName, subPath, result.mimeType)
                }
                if (uri != null) {
                    CourseFileManager.recordDownloadedFile(
                        context,
                        com.vtop.utils.DownloadedFileRecord(
                            key = key,
                            courseCode = detail.courseCode,
                            courseTitle = detail.courseTitle,
                            courseType = detail.courseType,
                            semSubId = detail.semSubId,
                            classId = detail.classId,
                            relativePath = item.relativePath,
                            fileName = finalName,
                            subPath = subPath,
                            isSyllabus = isSyllabus,
                            isCoursePlan = item.isCoursePlan
                        )
                    )
                    refreshDownloadTick++

                    if (CourseFileManager.isAppInForeground()) {
                        CourseFileManager.openFile(context, uri, finalName)
                    } else {
                        val resolvedMime = result.mimeType?.substringBefore(";")?.takeIf { it.isNotBlank() }
                            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                            ?: "application/octet-stream"

                        NotificationHelper.showDownloadNotificationFromUri(
                            context = context,
                            uri = uri,
                            fileName = finalName,
                            mimeType = resolvedMime,
                            title = "${detail.courseCode} · ${item.label}",
                            description = finalName
                        )
                    }
                }
            } else {
                tempFile.delete()
                Toast.makeText(context, result.error ?: "Download failed", Toast.LENGTH_SHORT).show()
            }
            downloadingKeys = downloadingKeys - key
        }
    }

    LaunchedEffect(Unit) {
        if (vtopClient == null) {
            errorMsg = "Session expired. Pull down to sync first."
            return@LaunchedEffect
        }
        isLoading = true
        val semHtml = withContext(Dispatchers.IO) { vtopClient.fetchCoursePageSemestersRawHtml() }
        if (semHtml != null) {
            semesters = CoursePageParser.parseSemesters(semHtml)
            val match = semesters.find { it.id == selectedSem.id } ?: semesters.firstOrNull()
            if (match != null) {
                selectedSem = match
                loadCoursesForSem(match.id)
            } else if (selectedSem.id.isNotBlank()) {
                loadCoursesForSem(selectedSem.id)
            } else {
                errorMsg = "Could not load semesters. Session may have expired."
                isLoading = false
            }
        } else {
            errorMsg = "Session expired. Pull down to sync first."
            isLoading = false
        }
    }

    BackHandler {
        when (step) {
            CoursePageStep.DETAIL -> step = if (sections.isNotEmpty()) CoursePageStep.SECTIONS else CoursePageStep.COURSES
            CoursePageStep.SECTIONS -> step = CoursePageStep.COURSES
            CoursePageStep.COURSES -> onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                focusManager.clearFocus()
                when (step) {
                    CoursePageStep.DETAIL -> step = if (sections.isNotEmpty()) CoursePageStep.SECTIONS else CoursePageStep.COURSES
                    CoursePageStep.SECTIONS -> step = CoursePageStep.COURSES
                    CoursePageStep.COURSES -> onBack()
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = when (step) {
                    CoursePageStep.COURSES -> "Course Page"
                    CoursePageStep.SECTIONS -> sections.firstOrNull()?.courseCode ?: "Select Faculty"
                    CoursePageStep.DETAIL -> courseDetail?.courseCode ?: "Course Details"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                focusManager.clearFocus()
                showSettingsSheet = true
            }) {
                Icon(Icons.Default.Settings, contentDescription = "Course Page Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        if (errorMsg != null && step == CoursePageStep.COURSES && groupedCourses.isEmpty() && semesters.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
            return@Column
        }

        when (step) {
            CoursePageStep.COURSES -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Local Semester Dropdown
                    item(key = "sem_dropdown") {
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable {
                                        focusManager.clearFocus()
                                        semDropdownExpanded = true
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedSem.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = semDropdownExpanded,
                                onDismissRequest = { semDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                semesters.forEach { sem ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                sem.name,
                                                fontWeight = if (sem.id == selectedSem.id) FontWeight.Bold else FontWeight.Normal,
                                                color = if (sem.id == selectedSem.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            semDropdownExpanded = false
                                            selectedSem = sem
                                            loadCoursesForSem(sem.id)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Empty courses fallback with Retry
                    if (groupedCourses.isEmpty()) {
                        item(key = "empty_courses") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = errorMsg ?: "No courses loaded for this semester.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { loadCoursesForSem(selectedSem.id) }) {
                                        Text("Retry", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Grouped Course Cards
                    items(groupedCourses, key = { it.courseCode }) { group ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column {
                                    Text(group.courseCode, fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    Text(group.courseTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.types.forEach { opt ->
                                        AssistChip(
                                            onClick = { loadSections(selectedSem.id, opt.classId) },
                                            label = { Text(formatCourseTypeLabel(opt.courseType), fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isDevOptionsEnabled) {
                        // --- OR: Direct Class ID Lookup ---
                        item(key = "direct_class_lookup") {
                            OrDivider()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Lookup Faculty by Class ID",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedTextField(
                                        value = directClassId,
                                        onValueChange = { directClassId = it },
                                        label = { Text("Class ID (e.g. AP2026272000416)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Button(
                                        onClick = {
                                            val semId = CoursePageParser.extractSemIdFromClassId(
                                                directClassId
                                            )
                                            if (semId == null) {
                                                Toast.makeText(
                                                    context,
                                                    "Invalid Class ID",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                loadSections(semId, directClassId)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Fetch Faculty List", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }

                        // --- OR: Direct Class ID + Faculty ID Lookup ---
                        item(key = "direct_detail_lookup") {
                            OrDivider()
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "Direct Course Page Lookup",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    OutlinedTextField(
                                        value = directClassId2,
                                        onValueChange = { directClassId2 = it },
                                        label = { Text("Class ID") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    OutlinedTextField(
                                        value = directFacultyId,
                                        onValueChange = { directFacultyId = it },
                                        label = { Text("Faculty ID / ERP ID (e.g. 70640)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    Button(
                                        onClick = {
                                            val semId = CoursePageParser.extractSemIdFromClassId(
                                                directClassId2
                                            )
                                            if (semId == null || directFacultyId.isBlank()) {
                                                Toast.makeText(
                                                    context,
                                                    "Enter valid Class ID and Faculty ID",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                sections = emptyList()
                                                loadDetail(semId, directFacultyId, directClassId2)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) { Text("Open Course Page", fontWeight = FontWeight.Bold) }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }
            }

            CoursePageStep.SECTIONS -> {
                val filteredSections = remember(sections, sectionSearchQuery) {
                    val q = sectionSearchQuery.trim()
                    if (q.isEmpty()) sections
                    else sections.filter {
                        it.facultyName.contains(q, ignoreCase = true) ||
                                it.slot.contains(q, ignoreCase = true) ||
                                it.erpId.contains(q, ignoreCase = true)
                    }
                }
                val groupedBySlot = remember(filteredSections) { filteredSections.groupBy { it.slot } }
                val userSlot = remember(sections, userClassIds, activeTargetClassId) {
                    sections.firstOrNull { it.classId == activeTargetClassId || userClassIds.contains(it.classId) }?.slot
                }
                val expandedSlots = remember(userSlot) {
                    mutableStateMapOf<String, Boolean>().apply { if (userSlot != null) put(userSlot, true) }
                }
                val isSearching = sectionSearchQuery.isNotBlank()

                val sectionsListState = rememberLazyListState()
                LaunchedEffect(userSlot, groupedBySlot) {
                    if (userSlot != null && sectionSearchQuery.isBlank()) {
                        val targetIndex = groupedBySlot.keys.indexOf(userSlot)
                        if (targetIndex >= 0) {
                            sectionsListState.animateScrollToItem(targetIndex)
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = sectionSearchQuery,
                        onValueChange = { sectionSearchQuery = it },
                        placeholder = { Text("Enter Faculty Name, Slot...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (isSearching) {
                                IconButton(onClick = { sectionSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    )

                    LazyColumn(
                        state = sectionsListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groupedBySlot.forEach { (slot, slotSections) ->
                            val isExpanded = isSearching || expandedSlots[slot] == true
                            item(key = "slot_$slot") {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (slot == userSlot) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().animateContentSize()
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    focusManager.clearFocus()
                                                    expandedSlots[slot] = !isExpanded
                                                }
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(slot, fontSize = 15.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                                Text("(${slotSections.size})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (slot == userSlot) {
                                                    Text(
                                                        "YOUR SLOT",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Icon(
                                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (isExpanded) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                            slotSections.forEachIndexed { idx, sec ->
                                                val isUserFaculty = sec.classId == activeTargetClassId || userClassIds.contains(sec.classId)
                                                val semId = CoursePageParser.extractSemIdFromClassId(sec.classId) ?: selectedSem.id
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            if (isUserFaculty) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                            else cardContainerColor()
                                                        )
                                                        .clickable { loadDetail(semId, sec.erpId, sec.classId) }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            sec.facultyName,
                                                            fontSize = 14.sp,
                                                            fontWeight = if (isUserFaculty) FontWeight.Black else FontWeight.SemiBold,
                                                            color = if (isUserFaculty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text("${sec.erpId} · ${sec.classId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                if (idx < slotSections.lastIndex) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
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

            CoursePageStep.DETAIL -> {
                val detail = courseDetail ?: return@Column
                val lecturesWithMats = remember(detail) {
                    detail.lectures.filter { it.materials.isNotEmpty() || it.topic.isNotBlank() }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Metadata Card
                    item(key = "meta_card") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(detail.courseTitle, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                Text("${detail.courseType} · ${detail.slot}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(detail.facultyName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    detail.syllabusPath?.let { path ->
                                        MaterialActionChip(
                                            item = CourseMaterialItem("Syllabus", path, "Syllabus.pdf"),
                                            downloadingKeys = downloadingKeys,
                                            downloadedKeys = downloadedKeys,
                                            onClick = { handleMaterialClick(detail, it, isSyllabus = true) }
                                        )
                                    }
                                    MaterialActionChip(
                                        item = CourseMaterialItem("Course Plan", "course_plan_${detail.classId}", "CoursePlan.xlsx", isCoursePlan = true),
                                        downloadingKeys = downloadingKeys,
                                        downloadedKeys = downloadedKeys,
                                        onClick = { handleMaterialClick(detail, it) }
                                    )
                                    detail.allMaterialsPath?.let { path ->
                                        MaterialActionChip(
                                            item = CourseMaterialItem("Download All", path, "${detail.courseCode}_AllMaterials.zip", isZip = true),
                                            downloadingKeys = downloadingKeys,
                                            downloadedKeys = downloadedKeys,
                                            onClick = { handleMaterialClick(detail, it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // General Materials
                    if (detail.generalMaterials.isNotEmpty()) {
                        item(key = "gen_header") {
                            Text("GENERAL MATERIALS", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                        itemsIndexed(detail.generalMaterials, key = { index, mat -> "gen_${index}_${mat.label}" }) { index, mat ->
                            MaterialRowCard(
                                item = mat,
                                downloadingKeys = downloadingKeys,
                                downloadedKeys = downloadedKeys,
                                onClick = {
                                    handleMaterialClick(
                                        detail = detail,
                                        item = mat,
                                        slNo = (index + 1).toString(),
                                        topic = mat.label,
                                        isGeneral = true
                                    )
                                }
                            )
                        }
                    }

                    // Lectures with Materials
                    // Lectures with Materials / Empty State
                    if (lecturesWithMats.isEmpty()) {
                        item(key = "lec_empty") {
                            val vtopLinkText = buildAnnotatedString {
                                append("The faculty did not update the course page. If you think this is a bug, you can verify with the ")
                                pushStringAnnotation(tag = "URL", annotation = "https://vtop.vitap.ac.in")
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = TextDecoration.Underline
                                    )
                                ) {
                                    append("VTOP")
                                }
                                pop()
                                append(".")
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                @Suppress("DEPRECATION")
                                ClickableText(
                                    text = vtopLinkText,
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.error,
                                        lineHeight = 19.sp
                                    ),
                                    modifier = Modifier.padding(16.dp),
                                    onClick = { offset ->
                                        vtopLinkText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                                            .firstOrNull()?.let { annotation ->
                                                try {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, annotation.item.toUri()))
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "Unable to open VTOP", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                    }
                                )
                            }
                        }
                    } else {
                        item(key = "lec_header") {
                            Text("LECTURES", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                        }
                        itemsIndexed(lecturesWithMats, key = { index, lec -> "lec_${index}_${lec.slNo}" }) { _, lec ->
                            val hasMaterials = lec.materials.isNotEmpty()
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasMaterials) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    } else {
                                        cardContainerColor()
                                    }
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (hasMaterials) 3.dp else 2.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (hasMaterials) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("#${lec.slNo} · ${lec.date} (${lec.day})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (lec.topic.isNotBlank()) {
                                        Text(lec.topic, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                    } else {
                                        Text(
                                            text = "! No Description Provided by the faculty",
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (lec.materials.isNotEmpty()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
                                        lec.materials.forEachIndexed { idx, mat ->
                                            val matKey = mat.relativePath.ifBlank { mat.label }
                                            val isDownloading = downloadingKeys.contains(matKey)
                                            val isDownloaded = !mat.isWebLink && downloadedKeys.contains(matKey)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        handleMaterialClick(
                                                            detail = detail,
                                                            item = mat,
                                                            slNo = lec.slNo,
                                                            date = lec.date,
                                                            topic = lec.topic,
                                                            isLecture = true
                                                        )
                                                    }
                                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = mat.label,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    if (mat.isWebLink) {
                                                        Text(
                                                            text = mat.relativePath,
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                when {
                                                    mat.isWebLink -> Icon(
                                                        Lucide.ExternalLink,
                                                        contentDescription = "Open web link",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    isDownloading -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    isDownloaded -> Icon(
                                                        Icons.Default.CheckCircle,
                                                        contentDescription = "Downloaded",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    else -> Icon(
                                                        Lucide.ArrowDownToLine,
                                                        contentDescription = "Download",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            if (idx < lec.materials.lastIndex) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item(key = "bottom_spacer") { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Course Page Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = cardContainerColor()),
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
                                    text = if (saveToDiskEnabled) "Saved to Downloads/vtop_course_files" else "Saved to temporary cache (auto-clears)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = saveToDiskEnabled,
                                onCheckedChange = { enabled ->
                                    saveToDiskEnabled = enabled
                                    CourseFileManager.setSaveToDiskEnabled(context, enabled)
                                }
                            )
                        }

                        if (saveToDiskEnabled) {
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
                                        text = "Downloads/vtop_course_files",
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
                    }
                }
            }
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Text("  OR  ", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    }
}

@Composable
private fun MaterialActionChip(
    item: CourseMaterialItem,
    downloadingKeys: Set<String>,
    downloadedKeys: Set<String>,
    onClick: (CourseMaterialItem) -> Unit
) {
    val key = item.relativePath.ifBlank { item.label }
    val isDownloading = downloadingKeys.contains(key)
    val isDownloaded = downloadedKeys.contains(key)

    Surface(
        onClick = { onClick(item) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when {
                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
                isDownloaded -> Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                else -> Icon(Lucide.ArrowDownToLine, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }
            Text(
                text = item.label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MaterialRowCard(
    item: CourseMaterialItem,
    downloadingKeys: Set<String>,
    downloadedKeys: Set<String>,
    onClick: () -> Unit
) {
    val key = item.relativePath.ifBlank { item.label }
    val isDownloading = downloadingKeys.contains(key)
    val isDownloaded = downloadedKeys.contains(key)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (item.fileNameHint.isNotBlank() && item.fileNameHint != item.label) {
                    Text(item.fileNameHint, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            when {
                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                isDownloaded -> Icon(Icons.Default.CheckCircle, contentDescription = "Open", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                else -> Icon(Lucide.ArrowDownToLine, contentDescription = "Download", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun formatCourseTypeLabel(rawType: String): String = when (rawType.trim().uppercase()) {
    "ETH", "TH" -> "Theory"
    "ELA", "LO" -> "Lab"
    else -> rawType.ifBlank { "Open" }
}

@Composable
private fun cardContainerColor(): androidx.compose.ui.graphics.Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        androidx.compose.ui.graphics.Color(0xFF18181B)
    } else {
        MaterialTheme.colorScheme.surface
    }