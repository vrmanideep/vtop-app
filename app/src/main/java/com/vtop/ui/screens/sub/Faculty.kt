package com.vtop.ui.screens.sub

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.vtop.models.FacultyEntity
import com.vtop.network.FacultyDetails
import com.vtop.network.FacultyScraper
import com.vtop.network.VtopClient
import com.vtop.utils.AnalyticsManager
import com.vtop.core.FacultyStorage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Mapped exactly to the HTML acronym requirements
fun getShortDept(dept: String?): String {
    if (dept.isNullOrBlank()) return ""
    val d = dept.lowercase().replace("&", "and").replace(Regex("\\s+"), " ")
    return when {
        d.contains("computer science") || d.contains("scope") -> "SCOPE"
        d.contains("electronics") || d.contains("sense") -> "SENSE"
        d.contains("mechanical") || d.contains("smec") -> "SMEC"
        d.contains("advanced science") || d.contains("sas") -> "SAS"
        d.contains("social science") || d.contains("humanities") || d.contains("vish") -> "VISH"
        d.contains("law") || d.contains("vsl") -> "VSL"
        d.contains("business") || d.contains("vsb") -> "VSB"
        d.contains("bio science") || d.contains("technology") || d.contains("sbst") -> "SBST"
        else -> dept.trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacultyScreen(
    vtopClient: VtopClient,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var facultyList by remember { mutableStateOf(emptyList<FacultyEntity>()) }
    var refreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Faculty_Screen")
        facultyList = FacultyStorage.loadFaculty(context)
    }

    BackHandler { onBack() }

    var searchQuery by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var expandedFacultyImage by remember { mutableStateOf<FacultyEntity?>(null) }
    var selectedSchool by remember { mutableStateOf("All") }

    val schools = remember(facultyList) {
        listOf("All") + facultyList.mapNotNull {
            val dept = getShortDept(it.department)
            if (dept.isNotBlank()) dept else null
        }.distinct().sorted()
    }

    val filteredList = remember(searchQuery, selectedSchool, facultyList) {
        facultyList.filter { faculty ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                faculty.name.contains(searchQuery, true) ||
                        faculty.department?.contains(searchQuery, true) == true ||
                        faculty.id.toString().contains(searchQuery)
            }
            val matchesSchool = if (selectedSchool == "All") true else getShortDept(faculty.department) == selectedSchool
            matchesSearch && matchesSchool
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Faculty") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                refreshing = true
                                errorMessage = null
                                try {
                                    val freshData = FacultyScraper.download(vtopClient)
                                    FacultyStorage.saveFaculty(context, freshData)
                                    facultyList = FacultyStorage.loadFaculty(context)
                                } catch (e: Exception) {
                                    errorMessage = "Network error: Unable to fetch data."
                                } finally {
                                    refreshing = false
                                }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search faculty") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp), singleLine = true
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                items(schools) { school ->
                    val isSelected = selectedSchool == school
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                            .clickable { selectedSchool = school }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(school, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }

            if (facultyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.CloudOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(16.dp))
                        Text(text = errorMessage ?: "No faculty data found.\nTap refresh to download offline cache.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        if (errorMessage != null) {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { refreshing = true; /* Trigger same launch logic */ }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                                Text("Retry")
                            }
                        }
                    }
                }
            } else if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(filteredList, key = { it.id }) { faculty ->
                        FacultyCard(
                            faculty = faculty, vtopClient = vtopClient, isExpanded = expandedId == faculty.id,
                            onClick = { expandedId = if (expandedId == faculty.id) null else faculty.id },
                            onImageClick = { expandedFacultyImage = it }
                        )
                    }
                }
            }
        }
    }

    if (expandedFacultyImage != null) {
        val faculty = expandedFacultyImage!!
        var offsetY by remember { mutableFloatStateOf(0f) }
        var isDismissing by remember { mutableStateOf(false) }
        val animatedOffsetY by animateFloatAsState(targetValue = if (isDismissing) 1500f else offsetY, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "swipe")

        LaunchedEffect(animatedOffsetY) { if (isDismissing && animatedOffsetY > 800f) expandedFacultyImage = null }

        Dialog(onDismissRequest = { isDismissing = true }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = (0.7f - (abs(offsetY) / 1000f)).coerceIn(0f, 0.7f))).clickable { isDismissing = true }, contentAlignment = Alignment.Center) {
                Card(
                    shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(0.75f).aspectRatio(1f).offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(onDragEnd = { if (offsetY > 200f || offsetY < -200f) isDismissing = true else offsetY = 0f }, onDragCancel = { offsetY = 0f }) { change, dragAmount -> change.consume(); offsetY += dragAmount }
                        }.clickable(enabled = false) {}
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (faculty.image.isNullOrBlank()) {
                            Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.fillMaxSize().padding(32.dp))
                        } else {
                            AsyncImage(model = faculty.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).padding(16.dp)) {
                            Text(faculty.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val shortDept = getShortDept(faculty.department)
                            if (shortDept.isNotBlank()) Text(shortDept, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FacultyCard(faculty: FacultyEntity, vtopClient: VtopClient, isExpanded: Boolean, onClick: () -> Unit, onImageClick: (FacultyEntity) -> Unit) {
    val context = LocalContext.current
    var details by remember { mutableStateOf<FacultyDetails?>(null) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded && details == null) {
            // Read from Disk if it was saved during Timetable Sync!
            if (faculty.office != null || faculty.email != null) {
                details = FacultyDetails(faculty.email, faculty.office, faculty.research, faculty.openHours ?: emptyList())
            } else {
                isLoadingDetails = true
                details = FacultyScraper.fetchDetails(vtopClient, faculty.id)
                isLoadingDetails = false
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onImageClick(faculty) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    if (!faculty.image.isNullOrBlank()) AsyncImage(model = faculty.image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(faculty.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    val designation = faculty.designation ?: ""
                    if (designation.isNotBlank()) Text(designation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    val shortSchool = getShortDept(faculty.department)
                    if (shortSchool.isNotBlank()) {
                        Box(modifier = Modifier.padding(top = 6.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text(shortSchool, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(16.dp))
                if (isLoadingDetails) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                } else {
                    val rawOffice = details?.office ?: "N/A"
                    val formattedOffice = rawOffice.replace(";", "-")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(formattedOffice, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        if (rawOffice != "N/A" && rawOffice.isNotBlank()) {
                            IconButton(onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Cabin", formattedOffice)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied: $formattedOffice", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val safeEmail = details?.email ?: "N/A"
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clickable(enabled = safeEmail != "N/A") {
                            try { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:$safeEmail".toUri())) } catch (_: Exception) {}
                        }.padding(vertical = 4.dp)) {
                            Icon(Icons.Outlined.Email, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(safeEmail, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (safeEmail != "N/A" && safeEmail.isNotBlank()) {
                            IconButton(onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Email", safeEmail)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied: $safeEmail", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (!details?.research.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Science, null, modifier = Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(details!!.research!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                        }
                    }

                    if (details?.openHours?.isNotEmpty() == true) {
                        Spacer(Modifier.height(16.dp))
                        Text("Open Hours:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Column(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))) {
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f)).padding(12.dp)) {
                                Text("Weekday", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Hours", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            details!!.openHours.forEachIndexed { index, oh ->
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(oh.day, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(oh.time, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (index < details!!.openHours.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}