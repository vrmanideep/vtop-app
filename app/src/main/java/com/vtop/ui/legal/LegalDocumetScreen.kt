package com.vtop.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.ui.legal.LegalDocumentType

data class LegalSection(
    val heading: String? = null,
    val subheading: String? = null,
    val text: String? = null,
    val bullets: List<String> = emptyList()
)

object LegalContent {
    val privacyPolicy = listOf(
        LegalSection(heading = "Last Updated", bullets = listOf("11-06-2026")),
        LegalSection(heading = "Overview", text = "VTOP Mate is an independent student-developed application that helps students access and organize information available through their authorized VTOP account.\n\nThis application is not affiliated with, endorsed by, or maintained by VIT-AP University."),
        LegalSection(heading = "Information We Collect", subheading = "User Credentials", bullets = listOf("Registration Number", "VTOP Credentials", "Selected Semester Preferences")),
        LegalSection(subheading = "Academic Information", text = "The application may retrieve and store:", bullets = listOf("Timetable", "Attendance", "Marks", "Grade History", "Examination Schedules", "Outing Information", "Student Profile Information")),
        LegalSection(subheading = "Optional Google Services", text = "When enabled:", bullets = listOf("Google Account Email", "Calendar Synchronization Preferences")),
        LegalSection(subheading = "Notifications", text = "The application may store:", bullets = listOf("Firebase Cloud Messaging Token", "Notification Preferences")),
        LegalSection(heading = "Data Usage", text = "Information is used only to:", bullets = listOf("Display academic information", "Generate reminders", "Sync calendar events", "Deliver notifications", "Improve application functionality")),
        LegalSection(heading = "Data Storage", bullets = listOf("Most information is stored locally on the user's device.", "The developer does not sell or share personal information with advertisers.")),
        LegalSection(heading = "Third-Party Services", text = "This application may use:", bullets = listOf("Firebase", "Google Sign-In", "Google Calendar APIs", "Each service operates under its own privacy policy.")),
        LegalSection(heading = "Data Removal", text = "Users may remove stored information by:", bullets = listOf("Logging Out", "Clearing Application Data", "Uninstalling the Application")),
        LegalSection(heading = "Contact", text = "Questions regarding privacy may be submitted through the project's GitHub repository.")
    )

    val termsOfUse = listOf(
        LegalSection(heading = "Last Updated", bullets = listOf("11-06-2026")),
        LegalSection(heading = "Acceptance", bullets = listOf("By using VTOP Mate, you agree to these Terms of Use.")),
        LegalSection(heading = "Independent Application", bullets = listOf("VTOP Mate is an independent student-developed application.", "VTOP Mate is not affiliated with VIT-AP University.")),
        LegalSection(heading = "User Responsibility", text = "Users are responsible for:", bullets = listOf("Maintaining credential security.", "Verifying academic information through official university systems.", "Ensuring compliance with university policies.")),
        LegalSection(heading = "Availability", bullets = listOf("Application functionality depends on the availability and structure of VTOP services.", "Features may change or stop working if university systems are modified.")),
        LegalSection(heading = "No Warranty", bullets = listOf("The application is provided \"as is\" without warranties of any kind.")),
        LegalSection(heading = "Limitation of Liability", text = "The developer shall not be liable for:", bullets = listOf("Missed Classes", "Attendance Discrepancies", "Incorrect Schedules", "Missed Examinations", "Outing-Related Issues", "Data Loss")),
        LegalSection(text = "Users should always verify critical information through official university channels."),
        LegalSection(heading = "Changes", bullets = listOf("These terms may be updated periodically without prior notice."))
    )

    val disclaimer = listOf(
        LegalSection(heading = "Independent Project", bullets = listOf("VTOP Mate is an independent student-developed project.", "This application is not affiliated with, endorsed by, sponsored by, or maintained by VIT-AP University.")),
        LegalSection(heading = "Information Source", bullets = listOf("All information displayed within the application originates from data accessible through the user's authorized VTOP account.")),
        LegalSection(heading = "Accuracy", bullets = listOf("While reasonable efforts are made to provide accurate information, users should always verify important academic information through official university resources.")),
        LegalSection(heading = "Liability", bullets = listOf("The developer assumes no responsibility for decisions made based on information presented within the application.", "Use of the application is entirely at the user's own risk.")),
        LegalSection(heading = "Application Limitations", text = "This application cannot modify official VTOP data including:", bullets = listOf("Timetable", "Attendance", "Marks", "Exam Schedule", "Grades", "Outings", "Any other university-managed information"))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDocumentScreen(
    type: LegalDocumentType,
    onBack: () -> Unit
) {
    val content = when (type) {
        LegalDocumentType.PRIVACY_POLICY -> LegalContent.privacyPolicy
        LegalDocumentType.TERMS_OF_USE -> LegalContent.termsOfUse
        LegalDocumentType.DISCLAIMER -> LegalContent.disclaimer
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(type.title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(content) { section ->
                Column {
                    if (section.heading != null) {
                        Text(
                            text = section.heading,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    if (section.subheading != null) {
                        Text(
                            text = section.subheading,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    if (section.text != null) {
                        Text(
                            text = section.text,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    section.bullets.forEach { bullet ->
                        Row(
                            modifier = Modifier.padding(bottom = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp, end = 12.dp)
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                            )
                            Text(
                                text = bullet,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}