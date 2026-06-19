package com.vtop.ui.screens.sub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ExternalLink

data class ReleaseLog(
    val version: String,
    val tag: String,
    val date: String,
    val features: List<String> = emptyList(),
    val fixes: List<String> = emptyList()
) {
    val url: String get() = "https://github.com/vrmanideep/vtop-app/releases/tag/$tag"
}

val releaseHistory = listOf(
    ReleaseLog(
        version = "Version 1.1.9",
        tag = "v1.1.9",
        date = "19 Jun 2026",
        features = listOf(
            "Implemented new structured JSON OTA update engine.",
            "Native Compose update dialogs.",
            "Removed external Markdown rendering dependency for OTA dialogs."
        ),
        fixes = listOf(
            "Fixed Academic Calendar bug where Lab FATs were merging into Instructional Days.",
            "Fixed back-stack navigation issues across the app."
        )
    ),

    ReleaseLog(
        version = "Version 1.1.8",
        tag = "v1.1.8",
        date = "18 Jun 2026",
        features = listOf(
            "Academic Calendar Overhaul: Complete rewrite of the calendar system with native Modal Bottom Sheet UI for navigating historical data.",
            "Gmail OTP Auto-Resolve: Automated OTP verification using linked student Gmail.",
            "Smart Session Recovery: Added auto-healing logic; the app will now attempt to silently re-authenticate if a session expires during a sync.",
            "Pull-to-Refresh: Added pull-to-refresh functionality and manual sync buttons for Calendar and Timetable modules.",
            "Progress Tracking: Added visual progress bars for batch sync operations.",
            "Widget Optimizations: Improved reliability for home screen widgets."
        ),
        fixes = listOf(
            "Fixed persistent Shared Preferences errors.",
            "Fixed calculation logic errors in Bunk Simulator.",
            "Resolved network timeout issues (SocketTimeoutException) during Timetable to PNG exports.",
            "Fixed UI positioning for Dropdown menus in Profile settings."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.7",
        tag = "v1.1.7",
        date = "26 May 2026",
        fixes = listOf(
            "Deleted auto download of Outpass upon approval."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.6",
        tag = "v1.1.6",
        date = "25 May 2026",
        features = listOf(
            "Directly download timetable via Download button in Home tab."
        ),
        fixes = listOf(
            "Timetable opening on random dates instead of automatically focusing on today.",
            "Weekend outings not appearing after sync due to VTOP weekend outing structure changes.",
            "Merged timetable slots (e.g., TCC+TCC1, TAA+TAA1) getting truncated in UI.",
            "Incorrect timetable merge handling for consecutive theory/lab sessions.",
            "Attendance screen using outdated scaffold spacing, causing squeezed layouts.",
            "Holiday data not loading correctly from academic calendar assets.",
            "Semester dialog failing to display/select semesters properly.",
            "Timetable cards visually misaligned and left-biased.",
            "Weekend Outing parser failing.",
            "Multiple overlay/glass spacing inconsistencies across screens.",
            "Duplicate scroll behaviors interfering with auto-scroll-to-today.",
            "Various Compose state propagation and recomposition issues."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.5",
        tag = "v1.1.5",
        date = "12 May 2026",
        features = listOf(
            "Customizable background Auto-Sync intervals (None, 1 hr, 2 hrs, 4 hrs, 8 hrs) accessible via the Profile tab.",
            "Swipe-to-cancel for pending outpasses featuring mechanical vibration feedback and a safety confirmation dialog.",
            "Automatic outpass PDF background downloads and push notifications upon Warden/Mentor approval.",
            "Faculty cabin and email details natively integrated inside Timetable Exam Cards.",
            "Dynamic approval journey labels for tracking outpass statuses.",
            "Modern Material 3 Date and Time pickers for leave requests.",
            "In-app browser (Chrome Custom Tabs) for the CGPA Calculator.",
            "Course distribution tags displayed in Grade History cards."
        ),
        fixes = listOf(
            "Grade History parser mistakenly displaying student profile info as a course.",
            "Timetable wiping out regular classes too early; now correctly shows \"Preparation Day\".",
            "Outpass downloading failed.",
            "Outpass auto-download triggering repeatedly instead of exactly once upon approval.",
            "UI text overflow in grade history circles."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.4",
        tag = "v1.1.4",
        date = "09 May 2026",
        features = listOf(
            "Faculty page"
        ),
        fixes = listOf(
            "Login using custom username apart from register number."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.3",
        tag = "v1.1.3",
        date = "08 May 2026",
        features = listOf(
            "OTP bypass via Gmail API and OAuth sign-in.",
            "Notification service for exams, login errors."
        ),
        fixes = listOf(
            "Login issues.",
            "OTP handling.",
            "Widget sync issues."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.2",
        tag = "v1.1.2",
        date = "04 May 2026",
        features = listOf(
            "Migrated from GitHub API to Firebase for OTA updates."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.1",
        tag = "v1.1.1",
        date = "30 Apr 2026",
        fixes = listOf(
            "Dark Mode's background color is now completely AMOLED black."
        )
    ),
    ReleaseLog(
        version = "Version 1.1.0",
        tag = "v1.1.0",
        date = "30 Apr 2026",
        features = listOf(
            "Academic Calendar View"
        ),
        fixes = listOf(
            "Date Parsing error in Exams that caused the app to crash.",
            "Widget syncing issue.",
            "VTOP Webview failing on OTP requirement."
        )
    ),
    ReleaseLog(
        version = "Version 1.0.0",
        tag = "v1.0.0",
        date = "27 Apr 2026",
        features = listOf(
            "Initial Release"
        )
    )
)

@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(top = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .clickable { onBack() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Go Back", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("Changelog", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(releaseHistory) { release ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {

                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(release.version, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(release.date, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            // GitHub Link Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { uriHandler.openUri(release.url) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("GitHub", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Lucide.ExternalLink, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Features List
                        if (release.features.isNotEmpty()) {
                            release.features.forEach { feature ->
                                Row(modifier = Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(feature, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }

                        // Fixes List
                        if (release.fixes.isNotEmpty()) {
                            if (release.features.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Text("FIXES & IMPROVEMENTS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            release.fixes.forEach { fix ->
                                Row(modifier = Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Top) {
                                    Box(modifier = Modifier.padding(top = 6.dp).size(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(fix, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}