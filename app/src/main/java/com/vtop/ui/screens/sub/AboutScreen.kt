package com.vtop.ui.screens.sub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.composables.icons.lucide.Github
import com.composables.icons.lucide.Lucide
import com.mikepenz.markdown.m3.Markdown
import com.vtop.BuildConfig
import com.vtop.ui.legal.LegalDocumentType
import com.vtop.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<com.vtop.utils.UpdateInfo?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // HERO SECTION
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡", fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("VTOP Mate", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Version ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(50))
                                .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stable release", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // WHAT'S NEW SECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("WHAT'S NEW")
                CardGroup {
                    GroupedActionRow(
                        icon = Icons.Outlined.Article, title = "Release notes", subtitle = "v${BuildConfig.VERSION_NAME} · Latest changes",
                        onClick = { navController.navigate("changelog") }
                    )
                }
            }

            // APP INFO SECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("APP INFO")
                CardGroup {
                    GroupedActionRow(
                        icon = Icons.Default.Refresh,
                        title = "Check for updates",
                        subtitle = if (isCheckingUpdate) "Checking..." else if (isDownloadingUpdate) "Downloading..." else "Tap to check manually",
                        onClick = {
                            if (!isCheckingUpdate && !isDownloadingUpdate) {
                                isCheckingUpdate = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val info = UpdateManager.checkForUpdates()
                                        withContext(Dispatchers.Main) {
                                            if (info.isUpdateAvailable) {
                                                updateInfo = info
                                            } else {
                                                android.widget.Toast.makeText(context, "You are on the latest version!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "Failed to check for updates.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    GroupedInfoRow("Version", BuildConfig.VERSION_NAME)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    GroupedInfoRow("Package", context.packageName)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    GroupedInfoRow("Build type", if (BuildConfig.DEBUG) "Debug" else "Release")
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    GroupedInfoRow("Target SDK", "Android ${context.applicationInfo.targetSdkVersion}")
                }
            }

            // LEGAL SECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("LEGAL")
                CardGroup {
                    GroupedActionRow(
                        icon = Icons.Outlined.PrivacyTip, title = "Privacy policy", subtitle = "How your data is handled",
                        onClick = { navController.navigate("legal/${LegalDocumentType.PRIVACY_POLICY.name}") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    GroupedActionRow(
                        icon = Icons.Outlined.Gavel, title = "Terms of use", subtitle = "Usage terms and conditions",
                        onClick = { navController.navigate("legal/${LegalDocumentType.TERMS_OF_USE.name}") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    GroupedActionRow(
                        icon = Icons.Outlined.Info, title = "Disclaimer", subtitle = "Important information",
                        onClick = { navController.navigate("legal/${LegalDocumentType.DISCLAIMER.name}") }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    GroupedActionRow(
                        icon = Icons.Outlined.Code, title = "Open source licenses", subtitle = "Libraries and acknowledgements",
                        onClick = { navController.navigate("licenses") }
                    )
                }
            }

            // DEVELOPER SECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("DEVELOPER")
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("MP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("V R Manideep P", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Student developer · VIT-AP", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Built with ❤️ for VIT students", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                CardGroup {
                    GroupedActionRow(
                        icon = Lucide.Github, title = "GitHub", subtitle = "Source code and issues", rightText = "vrmanideep",
                        onClick = { uriHandler.openUri("https://github.com/vrmanideep/vtop-app") }
                    )
                }
            }

            Text(
                text = "VTOP Mate is not affiliated with VIT-AP University",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 32.dp), textAlign = TextAlign.Center
            )
        }
    }

    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = {
                Column {
                    Text("Update Available", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    if (!updateInfo?.releaseTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(text = updateInfo!!.releaseTitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Version ${updateInfo?.latestVersion} is ready to download. Do you want to install it now?", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    if (!updateInfo?.releaseNotes.isNullOrBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Text(text = "Release Notes:", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Markdown(content = updateInfo!!.releaseNotes.replace("\\n", "\n"), modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isDownloadingUpdate = true
                        UpdateManager.downloadAndInstallUpdate(context = context, downloadUrl = updateInfo!!.downloadUrl, version = updateInfo!!.latestVersion)
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

@Composable
private fun SectionHeader(title: String) {
    Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun CardGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), content = content)
}

@Composable
private fun GroupedActionRow(icon: ImageVector, title: String, subtitle: String, rightText: String? = null, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.05f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rightText != null) {
            Text(text = rightText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Forward", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun GroupedInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}