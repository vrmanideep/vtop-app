package com.vtop.ui.screens.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vtop.models.FacultyModel

fun loadFaculty(context: Context): List<FacultyModel> {
    return try {
        val json = context.assets.open("faculty.json").bufferedReader().use { it.readText() }
        Gson().fromJson(json, object : TypeToken<List<FacultyModel>>() {}.type) ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@Composable
fun FacultyScreen(facultyList: List<FacultyModel>) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedId by remember { mutableStateOf<Int?>(null) }

    val filteredList = remember(searchQuery, facultyList) {
        if (searchQuery.isBlank()) facultyList
        else facultyList.filter {
            it.name.contains(searchQuery, true) ||
                    it.department?.contains(searchQuery, true) == true ||
                    it.research?.contains(searchQuery, true) == true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search name, dept, or research...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        if (facultyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No faculty data found.\nEnsure 'faculty.json' is in the assets folder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matches found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { faculty ->
                    FacultyCard(
                        faculty = faculty,
                        isExpanded = expandedId == faculty.id,
                        onClick = { expandedId = if (expandedId == faculty.id) null else faculty.id }
                    )
                }
            }
        }
    }
}

@Composable
fun FacultyCard(faculty: FacultyModel, isExpanded: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- CABIN ROW (Updated Formatting & Dark Mode Fix) ---
                val rawOffice = faculty.office ?: "N/A"
                val formattedOffice = rawOffice.replace(";", "-")

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary // Primary color for better visibility
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formattedOffice,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface // Fixed Dark Mode visibility
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
                            contentDescription = "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- EMAIL ROW ---
                val safeEmail = faculty.email ?: "N/A"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).clickable(enabled = safeEmail != "N/A") {
                            try {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$safeEmail")))
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
                            color = MaterialTheme.colorScheme.primary
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

                val safeResearch = faculty.research ?: ""
                if (safeResearch.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Research Interests:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = safeResearch,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}