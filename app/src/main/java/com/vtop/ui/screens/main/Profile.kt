package com.vtop.ui.screens.main

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Link2Off
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.vtop.BuildConfig
import com.vtop.models.ExamScheduleModel
import com.vtop.models.SemesterOption
import com.vtop.models.TimetableModel
import com.vtop.network.VtopClient
import com.vtop.telemetry.Telemetry
import com.vtop.ui.core.CalendarInfo
import com.vtop.ui.core.CalendarSync
import com.vtop.ui.core.CourseReminder
import com.vtop.ui.theme.AppThemeMode
import com.vtop.utils.AnalyticsManager
import com.vtop.utils.UpdateInfo
import com.vtop.utils.UpdateManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import com.vtop.ui.legal.*
import com.vtop.ui.pages.ProfilePage
import com.vtop.ui.screens.profile.LegalDocumentScreen
import com.vtop.ui.screens.sub.*
import com.vtop.ui.core.AppBridge
import com.vtop.ui.screens.portal.VtopPortalScreen

private fun formatReminderDate(dateStr: String): String {
    return try {
        val inFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val outFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH)
        val d = inFormat.parse(dateStr)
        if (d != null) outFormat.format(d) else dateStr
    } catch (e: Exception) { dateStr }
}

private fun formatLastSync(timestamp: Long): String {
    if (timestamp <= 0L) return "Never synced"
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / (1000 * 60)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Synced just now"
        minutes < 60 -> "Synced $minutes mins ago"
        hours < 24 -> "Synced $hours hrs ago"
        else -> "Synced $days days ago"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Profile(
    onBack: () -> Unit,
    initialPage: ProfilePage = ProfilePage.MAIN,
    onProfilePageChanged: (Boolean) -> Unit = {},
    timetable: TimetableModel,
    examsData: List<ExamScheduleModel>,
    onOpenPortal: () -> Unit,
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    customAccent: Color,
    onAccentChange: (Color) -> Unit,
    currentNavStyle: String,
    onNavStyleChange: (String) -> Unit,
    mergeLabs: Boolean,
    onMergeLabsChange: (Boolean) -> Unit,
    mergeMarks: Boolean,
    onMergeMarksChange: (Boolean) -> Unit,
    showOutings: Boolean,
    onShowOutingsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    profileData: Map<String, Map<String, String>>,
    selectedSemester: String,
    availableSemesters: List<SemesterOption>,
    onSemesterChange: (SemesterOption) -> Unit,
    currentRegNo: String,
    currentPass: String,
    onCredentialsSave: (String, String) -> Unit,
    reminders: List<CourseReminder>,
    onDeleteReminder: (String) -> Unit,
    lastSyncTime: String,
    onSyncClick: (Boolean) -> Unit,
    onForceAttendanceSync: () -> Unit,
    isForceAttendanceSyncing: Boolean,
    vtopClient: VtopClient?
) {
    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Profile_Screen")
    }

    var currentPage by rememberSaveable {
        mutableStateOf(initialPage)
    }
    LaunchedEffect(currentPage) {
        onProfilePageChanged(currentPage != ProfilePage.MAIN)
    }




    BackHandler(enabled = currentPage != ProfilePage.MAIN) {
        currentPage = when (currentPage) {
            ProfilePage.ABOUT -> ProfilePage.MAIN
            ProfilePage.TELEMETRY -> ProfilePage.MAIN
            ProfilePage.ACADEMIC_CALENDAR -> ProfilePage.MAIN
            ProfilePage.FACULTY -> ProfilePage.MAIN
            ProfilePage.PORTAL -> ProfilePage.MAIN

            ProfilePage.CHANGELOG -> ProfilePage.ABOUT
            ProfilePage.LICENSES -> ProfilePage.ABOUT
            ProfilePage.LEGAL -> ProfilePage.ABOUT


            ProfilePage.MAIN -> ProfilePage.MAIN
        }
    }

    var selectedLegalDocument by rememberSaveable {
        mutableStateOf(LegalDocumentType.PRIVACY_POLICY)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE) }
    var showCalendarSheet by remember { mutableStateOf(false) }
    var availableCalendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.WRITE_CALENDAR] == true && permissions[Manifest.permission.READ_CALENDAR] == true) {
            availableCalendars = CalendarSync.getWritableCalendars(context)
            showCalendarSheet = true
        } else {
            Toast.makeText(context, "Calendar permissions are required to export schedule", Toast.LENGTH_SHORT).show()
        }
    }

    var googleEmail by remember { mutableStateOf(com.vtop.utils.Vault.getGoogleEmail(context)) }
    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val email = account?.email ?: ""

            if (email.endsWith("@vitapstudent.ac.in")) {
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential)
                    .addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            com.vtop.utils.Vault.saveGoogleEmail(context, email)
                            googleEmail = email
                            Toast.makeText(context, "Email linked successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            val client = com.vtop.utils.AuthHelper.getGoogleSignInClient(context, context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName)))
                            client.signOut()
                            Toast.makeText(context, "Firebase Auth Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                val client = com.vtop.utils.AuthHelper.getGoogleSignInClient(context, context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName)))
                client.signOut()
                Toast.makeText(context, "Must use @vitapstudent.ac.in email", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            val statusCode = if (e is com.google.android.gms.common.api.ApiException) e.statusCode.toString() else "Unknown"
            Toast.makeText(context, "Sign-in failed: Code $statusCode", Toast.LENGTH_LONG).show()
        }
    }

    val basicInfo = profileData["basic"] ?: emptyMap()
    val name = basicInfo["name"]?.takeIf { it != "-" && it.isNotBlank() } ?: "Student Name"
    val regNo = basicInfo["regno"]?.takeIf { it != "-" && it.isNotBlank() } ?: "Fetching details..."
    val branch = basicInfo["program"]?.takeIf { it != "-" && it.isNotBlank() } ?: "Programme"
    val initial = name.firstOrNull()?.uppercase() ?: ""

    var heroTapCount by remember { mutableIntStateOf(0) }
    var isMoreExpanded by remember { mutableStateOf(false) }
    var isPreferencesExpanded by remember { mutableStateOf(false) }

    var showSemesterDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showCredDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }

    var autoSyncInterval by remember { mutableIntStateOf(sharedPrefs.getInt("AUTO_SYNC_INTERVAL", 8)) }
    var syncDropdownExpanded by remember { mutableStateOf(false) }
    val syncOptions = mapOf(0 to "None", 1 to "1 hr", 2 to "2 hrs", 4 to "4 hrs", 8 to "8 hrs")

    when (currentPage) {
        ProfilePage.MAIN -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 96.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- TOP BAR ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
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

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { currentPage = ProfilePage.ACADEMIC_CALENDAR }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Academic Calendar", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Open Calendar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp))
                    }
                }

                // --- PROFILE HERO CARD ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            heroTapCount++
                            if (heroTapCount >= 5) {
                                val newStyle = if (currentNavStyle == "DOCK") "STATIC" else "DOCK"
                                sharedPrefs.edit().putString("NAV_STYLE", newStyle).apply()
                                onNavStyleChange(newStyle)
                                Toast.makeText(context, "Nav style set to $newStyle", Toast.LENGTH_SHORT).show()
                                heroTapCount = 0
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(initial, fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text("$regNo · $branch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("VIT-AP University", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    Text(
                                        text = "Open VTOP ↗",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    currentPage = ProfilePage.PORTAL
                                                },
                                                onLongClick = {
                                                    onSyncClick(true)
                                                    currentPage = ProfilePage.PORTAL
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }

                // --- ACCOUNT ACTIONS ---
                SectionHeader("ACCOUNT")
                CardGroup {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSyncClick(false) },
                                onLongClick = { onSyncClick(true) }
                            )
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Last Synced", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatLastSync(lastSyncTime.toLongOrNull() ?: 0L),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).padding(8.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )

                    SettingRow(
                        label = "Force sync attendance",
                        value = if (isForceAttendanceSyncing) {
                            "Fetching complete attendance history..."
                        } else {
                            "Fetch complete attendance history"
                        },
                        actionText = if (isForceAttendanceSyncing) "Syncing..." else "Sync",
                        onClick = {
                            if (!isForceAttendanceSyncing) {
                                onForceAttendanceSync()
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    SettingRow(
                        label = "Change Semester",
                        value = "Current: $selectedSemester",
                        actionText = "Switch",
                        onClick = { showSemesterDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    SettingRow(
                        label = "Manage Credentials",
                        value = "Update VTOP password",
                        actionText = "Edit",
                        onClick = { showCredDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    if (googleEmail.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Linked Gmail for OTP", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(googleEmail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = {
                                val client = com.vtop.utils.AuthHelper.getGoogleSignInClient(context, context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName)))
                                client.signOut().addOnCompleteListener {
                                    com.vtop.utils.Vault.saveGoogleEmail(context, "")
                                    googleEmail = ""
                                    Toast.makeText(context, "Google Account Unlinked", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(imageVector = Lucide.Link2Off, contentDescription = "Unlink Google", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                val client = com.vtop.utils.AuthHelper.getGoogleSignInClient(context, context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName)))
                                googleSignInLauncher.launch(client.signInIntent)
                            }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Linked Gmail for OTP", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text("Link @vitapstudent.ac.in email", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Link", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Icon(imageVector = Lucide.Link, contentDescription = "Link Google", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // --- THE "MORE" ACCORDION ---
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { isMoreExpanded = !isMoreExpanded }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("More", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text("Calendar export, Auto Sync, Faculty", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(imageVector = if (isMoreExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand/Collapse", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (isMoreExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            SettingRow(
                                label = "Export to Google Calendar",
                                value = "Add classes & exams to your calendar",
                                actionText = "Sync",
                                onClick = {
                                    calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { syncDropdownExpanded = true }.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Background Auto Sync", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Frequency of background updates", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }

                                Box(contentAlignment = Alignment.TopEnd) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = syncOptions[autoSyncInterval] ?: "8 hrs", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Select", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(start = 4.dp))
                                    }

                                    DropdownMenu(
                                        expanded = syncDropdownExpanded,
                                        onDismissRequest = { syncDropdownExpanded = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    ) {
                                        syncOptions.forEach { (hours, label) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(text = label, color = if (autoSyncInterval == hours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (autoSyncInterval == hours) FontWeight.Bold else FontWeight.Normal)
                                                },
                                                onClick = {
                                                    autoSyncInterval = hours
                                                    sharedPrefs.edit().putInt("AUTO_SYNC_INTERVAL", hours).apply()
                                                    syncDropdownExpanded = false

                                                    val workManager = androidx.work.WorkManager.getInstance(context)
                                                    if (hours == 0) {
                                                        workManager.cancelUniqueWork("VTOP_BACKGROUND_SYNC")
                                                        Toast.makeText(context, "Auto sync disabled", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<com.vtop.ui.core.VtopSyncWorker>(hours.toLong(), java.util.concurrent.TimeUnit.HOURS).build()
                                                        workManager.enqueueUniquePeriodicWork("VTOP_BACKGROUND_SYNC", androidx.work.ExistingPeriodicWorkPolicy.REPLACE, syncRequest)
                                                        Toast.makeText(context, "Auto sync set to $label", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            SettingRow(
                                label = "Faculty Directory",
                                value = "Find cabins, emails & domains",
                                actionText = "Open",
                                onClick = { currentPage = ProfilePage.FACULTY }
                            )
                        }
                    }
                }

                // --- PREFERENCES & APPEARANCE ACCORDION ---
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { isPreferencesExpanded = !isPreferencesExpanded }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Preferences & Appearance", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text("Theme, Accent, Outings, Timetable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(imageVector = if (isPreferencesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = "Expand/Collapse", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (isPreferencesExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Show Outings Tab", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Display hostel outings in navigation", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = showOutings,
                                    onCheckedChange = { newValue ->
                                        sharedPrefs.edit().putBoolean("SHOW_OUTINGS", newValue).apply()
                                        onShowOutingsChange(newValue)
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Merge Consecutive sessions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Combine consecutive slots of same course in Timetable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = mergeLabs,
                                    onCheckedChange = { newValue ->
                                        sharedPrefs.edit().putBoolean("MERGE_LABS", newValue).apply()
                                        onMergeLabsChange(newValue)
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Merge Marks Components", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Group Theory & Lab/Project marks together", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = mergeMarks,
                                    onCheckedChange = { newValue ->
                                        sharedPrefs.edit().putBoolean("MERGE_MARKS", newValue).apply()
                                        onMergeMarksChange(newValue)
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            SettingRow(
                                label = "App Theme",
                                value = "Current: ${currentTheme.name.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }}",
                                actionText = "Change",
                                onClick = { showThemeDialog = true }
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Accent Color", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta, Color.Red)), CircleShape)
                                            .border(
                                                width = if (useDynamicColor) 3.dp else 0.dp,
                                                color = if (useDynamicColor) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                sharedPrefs.edit().putBoolean("USE_DYNAMIC_COLOR", true).apply()
                                                onDynamicColorChange(true)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    com.vtop.ui.theme.AccentColors.forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(color, CircleShape)
                                                .border(
                                                    width = if (!useDynamicColor && customAccent == color) 3.dp else 0.dp,
                                                    color = if (!useDynamicColor && customAccent == color) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    sharedPrefs.edit().putBoolean("USE_DYNAMIC_COLOR", false).apply()
                                                    sharedPrefs.edit().putInt("CUSTOM_ACCENT", color.toArgb()).apply()

                                                    onDynamicColorChange(false)
                                                    onAccentChange(color)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- REMINDERS ---
                SectionHeader("UPCOMING REMINDERS")
                if (reminders.isEmpty()) {
                    Text(
                        "No upcoming reminders.\nCreate one by tapping on a subject in home page.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    reminders.forEach { reminder ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${reminder.courseCode} · ${reminder.type}", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Due: ${formatReminderDate(reminder.date)}", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    if (reminder.syllabus.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(reminder.syllabus, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeleteReminder(reminder.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                SectionHeader("SYSTEM")
                CardGroup {
                    // --- ABOUT ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentPage = ProfilePage.ABOUT
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "About the app",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Version ${BuildConfig.VERSION_NAME}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    // --- TELEMETRY TOGGLE ---
                    var isTelemetryEnabled by rememberSaveable {
                        mutableStateOf(Telemetry.isEnabled())
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Developer Debug Logs",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Record app behavior",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        Switch(
                            checked = isTelemetryEnabled,
                            onCheckedChange = {
                                isTelemetryEnabled = it
                                Telemetry.setEnabled(it)
                            }
                        )
                    }

                    if (isTelemetryEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        SettingRow(
                            label = "Telemetry Viewer",
                            value = "Browse captured sessions",
                            actionText = "View",
                            onClick = {
                                currentPage = ProfilePage.TELEMETRY
                            }
                        )
                    }
                }

                // --- LOGOUT BUTTON ---
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .clickable { showLogoutDialog = true }
                        .background(Color.Transparent)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // --- DIALOGS & BOTTOM SHEETS ---
            if (showCalendarSheet) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                var selectedCalendarId by remember { mutableLongStateOf(availableCalendars.firstOrNull()?.id ?: -1L) }
                var calendarDropdownExpanded by remember { mutableStateOf(false) }
                var reminderMins by remember { mutableIntStateOf(10) }
                var reminderDropdownExpanded by remember { mutableStateOf(false) }
                val reminderOptions = mapOf(0 to "No Reminder", 10 to "10 mins before", 30 to "30 mins before", 60 to "1 hour before")
                var titleTemplate by remember { mutableStateOf("{courseCode} ({slot})") }
                var descTemplate by remember { mutableStateOf("{courseTitle}\nFaculty: {faculty}\nType: {courseType}\nClass ID: {classId}") }
                var locTemplate by remember { mutableStateOf("{venue}") }
                val sdf = remember { SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH) }
                var endDate by remember { mutableStateOf(CalendarSync.getDefaultEndDate(context)) }
                var showDatePicker by remember { mutableStateOf(false) }

                ModalBottomSheet(onDismissRequest = { showCalendarSheet = false }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Export to Google Calendar", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                        Text("SELECT CALENDAR", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Box(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.2f), RoundedCornerShape(8.dp)).background(Color.Transparent).clickable { calendarDropdownExpanded = true }.padding(16.dp)) {
                            val selectedName = availableCalendars.find { it.id == selectedCalendarId }?.name ?: "None"
                            Text(selectedName, color = MaterialTheme.colorScheme.onSurface)
                            DropdownMenu(expanded = calendarDropdownExpanded, onDismissRequest = { calendarDropdownExpanded = false }) {
                                availableCalendars.forEach { cal ->
                                    DropdownMenuItem(text = { Text(cal.name) }, onClick = { selectedCalendarId = cal.id; calendarDropdownExpanded = false })
                                }
                            }
                        }
                        Text("REMINDER", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Box(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.2f), RoundedCornerShape(8.dp)).background(Color.Transparent).clickable { reminderDropdownExpanded = true }.padding(16.dp)) {
                            Text(reminderOptions[reminderMins] ?: "None", color = MaterialTheme.colorScheme.onSurface)
                            DropdownMenu(expanded = reminderDropdownExpanded, onDismissRequest = { reminderDropdownExpanded = false }) {
                                reminderOptions.forEach { (mins, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = { reminderMins = mins; reminderDropdownExpanded = false })
                                }
                            }
                        }
                        Text("END SYNC ON (LAST INSTRUCTIONAL DAY)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Row(modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.2f), RoundedCornerShape(8.dp)).background(Color.Transparent).clickable { showDatePicker = true }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(endDate, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Outlined.Edit, "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                        Text("EVENT TEMPLATES", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        OutlinedTextField(value = titleTemplate, onValueChange = { titleTemplate = it }, label = { Text("Event Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = descTemplate, onValueChange = { descTemplate = it }, label = { Text("Event Description") }, modifier = Modifier.fillMaxWidth().height(100.dp))
                        OutlinedTextField(value = locTemplate, onValueChange = { locTemplate = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { CalendarSync.clearSyncedEvents(context, selectedCalendarId); showCalendarSheet = false }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
                                Text("Clear Old", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                            }
                            Button(onClick = { if (selectedCalendarId != -1L) { CalendarSync.syncToCalendar(context, timetable, examsData, mergeLabs, selectedCalendarId, reminderMins, endDate, titleTemplate, descTemplate, locTemplate) }; showCalendarSheet = false }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(12.dp)) {
                                Text("Sync Now", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = sdf.parse(endDate)?.time?.plus(TimeZone.getDefault().rawOffset))
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { millis -> val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }; endDate = sdf.format(cal.time) }; showDatePicker = false }) { Text("OK", color = MaterialTheme.colorScheme.primary) } },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    ) { DatePicker(state = datePickerState) }
                }
            }

            if (showThemeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
                    title = { Text("Select App Theme", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeChip("🌑", "Dark", currentTheme == AppThemeMode.DARK, Modifier.weight(1f)) {
                                sharedPrefs.edit().putString("APP_THEME", AppThemeMode.DARK.name).apply()
                                onThemeChange(AppThemeMode.DARK)
                                showThemeDialog = false
                            }
                            ThemeChip("☀️", "Light", currentTheme == AppThemeMode.LIGHT, Modifier.weight(1f)) {
                                sharedPrefs.edit().putString("APP_THEME", AppThemeMode.LIGHT.name).apply()
                                onThemeChange(AppThemeMode.LIGHT)
                                showThemeDialog = false
                            }
                            ThemeChip("⚙️", "System", currentTheme == AppThemeMode.SYSTEM, Modifier.weight(1f)) {
                                sharedPrefs.edit().putString("APP_THEME", AppThemeMode.SYSTEM.name).apply()
                                onThemeChange(AppThemeMode.SYSTEM)
                                showThemeDialog = false
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            if (showSemesterDialog) {
                AlertDialog(
                    onDismissRequest = { showSemesterDialog = false },
                    title = { Text("Select Semester", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            availableSemesters.forEach { sem ->
                                val isSelected = sem.name == selectedSemester
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().clickable { onSemesterChange(sem); showSemesterDialog = false }
                                ) {
                                    Text(sem.name, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = { TextButton(onClick = { showSemesterDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            if (showCredDialog) {
                var tempReg by remember { mutableStateOf(currentRegNo) }
                var tempPass by remember { mutableStateOf(currentPass) }
                var passwordVisible by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showCredDialog = false },
                    title = { Text("Update Credentials", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = tempReg, onValueChange = { tempReg = it }, label = { Text("Username") },
                                leadingIcon = { Icon(Lucide.UserRound, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = tempPass, onValueChange = { tempPass = it }, label = { Text("VTOP Password") },
                                leadingIcon = { Icon(Icons.Outlined.Lock, null) }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(imageVector = image, contentDescription = "Toggle Password", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = { onCredentialsSave(tempReg, tempPass); showCredDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("Save & Sync", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = { TextButton(onClick = { showCredDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Confirm Logout", fontWeight = FontWeight.Bold) },
                    text = { Text("Are you sure you want to log out? All data will be cleared.") },
                    confirmButton = {
                        TextButton(onClick = { showLogoutDialog = false; onLogout() }) { Text("Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            updateInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    title = {
                        Column {
                            Text("Update Available", fontWeight = FontWeight.Black, fontSize = 20.sp)
                            if (info.releaseTitle.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(text = info.releaseTitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(text = "Version ${info.latestVersion} is ready to download. Do you want to install it now?", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)

                            if (info.features.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("✨ Features", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.5.sp)
                                    info.features.forEach { feature ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                                            Text(feature, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            }

                            if (info.fixes.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("🛠 Fixes", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), letterSpacing = 0.5.sp)
                                    info.fixes.forEach { fix ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text("•", color = Color(0xFF10B981), modifier = Modifier.padding(end = 8.dp))
                                            Text(fix, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            }

                            if (info.important.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("📢 Important", fontSize = 12.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error, letterSpacing = 0.5.sp)
                                    info.important.forEach { note ->
                                        Row(verticalAlignment = Alignment.Top) {
                                            Text("•", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                                            Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isDownloadingUpdate = true
                                UpdateManager.downloadAndInstallUpdate(context = context, downloadUrl = info.downloadUrl, version = info.latestVersion)
                                updateInfo = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(if (isDownloadingUpdate) "Downloading..." else "Update Now", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }

        ProfilePage.PORTAL -> {
            if (vtopClient != null) {
                VtopPortalScreen(
                    vtopClient = vtopClient,
                    onBack = {
                        currentPage = ProfilePage.MAIN
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    onSyncClick(true)
                }

                Text(
                    "Preparing VTOP session..."
                )
            }
        }

        ProfilePage.ABOUT -> {
            AboutScreen(
                onBack = {
                    currentPage = ProfilePage.MAIN
                },
                onOpenChangelog = {
                    currentPage = ProfilePage.CHANGELOG
                },
                onOpenLicenses = {
                    currentPage = ProfilePage.LICENSES
                },
                onOpenLegal = { type ->
                    selectedLegalDocument = type
                    currentPage = ProfilePage.LEGAL
                }
            )
        }
        ProfilePage.CHANGELOG -> {
            ChangelogScreen(
                onBack = {
                    currentPage = ProfilePage.ABOUT
                }
            )
        }

        ProfilePage.LICENSES -> {
            LicensesScreen(
                onBack = {
                    currentPage = ProfilePage.ABOUT
                }
            )
        }
        ProfilePage.LEGAL -> {
            LegalDocumentScreen(
                type = selectedLegalDocument,
                onBack = {
                    currentPage = ProfilePage.ABOUT
                }
            )
        }

        ProfilePage.TELEMETRY -> {
            TelemetryScreen(
                onBack = { currentPage = ProfilePage.MAIN }
            )
        }

        ProfilePage.FACULTY -> {
            FacultyScreen(
                onBack = {
                    currentPage = ProfilePage.MAIN
                }
            )
        }

        ProfilePage.ACADEMIC_CALENDAR -> {
            AcademicCalendarScreen(
                onBack = { currentPage = ProfilePage.MAIN }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title, fontSize = 10.sp, fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun CardGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(), content = content
    )
}

@Composable
private fun SettingRow(label: String, value: String, actionText: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (actionText != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionText, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp).padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun ThemeChip(icon: String, label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}