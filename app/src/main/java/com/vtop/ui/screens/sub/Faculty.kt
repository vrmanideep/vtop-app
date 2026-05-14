package com.vtop.ui.screens.sub

import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vtop.models.FacultyModel
import com.vtop.utils.AnalyticsManager
import kotlin.math.roundToInt

// Helper to extract "SAS" from "School of Advanced Sciences (SAS)"
fun getShortDept(dept: String?): String {
    if (dept.isNullOrBlank()) return ""
    val regex = Regex("\\((.*?)\\)")
    val match = regex.find(dept)
    return match?.groupValues?.get(1)?.trim() ?: dept.trim()
}

fun loadFaculty(context: Context): List<FacultyModel> {
    return try {
        val json = com.vtop.utils.OtaManager.getFacultyJson(context)
        Gson().fromJson(json, object : TypeToken<List<FacultyModel>>() {}.type) ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@Composable
fun FacultyScreen(facultyList: List<FacultyModel>) {
    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Faculty_Screen")
    }
    var searchQuery by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var expandedFacultyImage by remember { mutableStateOf<FacultyModel?>(null) }
    var selectedSchool by remember { mutableStateOf("All") }

    // Dynamically extract short department names
    val schools = remember(facultyList) {
        listOf("All") + facultyList.mapNotNull { getShortDept(it.department) }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    val filteredList = remember(searchQuery, selectedSchool, facultyList) {
        facultyList.filter { faculty ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                faculty.name.contains(searchQuery, true) ||
                        faculty.department?.contains(searchQuery, true) == true ||
                        faculty.research?.contains(searchQuery, true) == true
            }
            val matchesSchool = if (selectedSchool == "All") true else getShortDept(faculty.department) == selectedSchool

            matchesSearch && matchesSchool
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            placeholder = { Text("Search name, dept, or research...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // FILTER CHIPS
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
                    Text(
                        text = school,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (facultyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "No faculty data found.\nEnsure data is synced.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredList, key = { it.id }) { faculty ->
                    FacultyCard(
                        faculty = faculty,
                        isExpanded = expandedId == faculty.id,
                        onClick = { expandedId = if (expandedId == faculty.id) null else faculty.id },
                        onImageClick = { expandedFacultyImage = it }
                    )
                }
            }
        }
    }

    if (expandedFacultyImage != null) {
        val faculty = expandedFacultyImage!!
        var offsetY by remember { mutableFloatStateOf(0f) }
        var isDismissing by remember { mutableStateOf(false) }

        val animatedOffsetY by animateFloatAsState(
            targetValue = if (isDismissing) 1500f else offsetY,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "swipeDismiss"
        )

        // Clear the state completely once the exit animation falls off-screen
        LaunchedEffect(animatedOffsetY) {
            if (isDismissing && animatedOffsetY > 800f) {
                expandedFacultyImage = null
            }
        }

        Dialog(
            onDismissRequest = { isDismissing = true },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = (0.7f - (Math.abs(offsetY) / 1000f)).coerceIn(0f, 0.7f)))
                    .clickable { isDismissing = true },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .aspectRatio(1f) // Squarish, like WhatsApp
                        .offset { IntOffset(0, animatedOffsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (offsetY > 200f || offsetY < -200f) {
                                        isDismissing = true
                                    } else {
                                        offsetY = 0f
                                    }
                                },
                                onDragCancel = { offsetY = 0f }
                            ) { change, dragAmount ->
                                change.consume()
                                offsetY += dragAmount
                            }
                        }
                        .clickable(enabled = false) {} // Catch clicks to prevent dismissing when tapping the image
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {

                        // Fallback icon or Actual Image
                        if (faculty.image.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxSize().padding(32.dp)
                            )
                        } else {
                            AsyncImage(
                                model = faculty.image,
                                contentDescription = "Profile Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // WhatsApp-style Bottom Bar Overlay (Aligned to Bottom)
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = faculty.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val shortDept = getShortDept(faculty.department)
                            if (shortDept.isNotBlank()) {
                                Text(
                                    text = shortDept,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
fun FacultyCard(faculty: FacultyModel, isExpanded: Boolean, onClick: () -> Unit, onImageClick: (FacultyModel) -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                // CLICKABLE AVATAR
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClick(faculty) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    if (!faculty.image.isNullOrBlank()) {
                        AsyncImage(
                            model = faculty.image,
                            contentDescription = "Photo of ${faculty.name}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = faculty.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val designation = faculty.designation ?: ""
                    if (designation.isNotBlank()) {
                        Text(
                            text = designation,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // SMOOTH ROUNDED SCHOOL BADGE
                    val shortSchool = getShortDept(faculty.department)
                    if (shortSchool.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = shortSchool,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- EXPANDED DETAILS ---
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                val rawOffice = faculty.office ?: "N/A"
                val formattedOffice = rawOffice.replace(";", "-")

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formattedOffice,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(formattedOffice))
                            Toast.makeText(context, "Copied: $formattedOffice", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Cabin",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val safeEmail = faculty.email ?: "N/A"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).clickable(enabled = safeEmail != "N/A") {
                            try {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$safeEmail")))
                            } catch (e: Exception) {
                                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Email, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = safeEmail,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(safeEmail))
                            Toast.makeText(context, "Copied: $safeEmail", Toast.LENGTH_SHORT).show()
                        },
                        enabled = safeEmail != "N/A",
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}