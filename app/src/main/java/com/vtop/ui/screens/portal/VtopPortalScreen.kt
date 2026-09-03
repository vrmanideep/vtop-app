package com.vtop.ui.screens.portal

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.network.VtopClient
import com.vtop.portal.PortalController
import com.vtop.portal.PortalHost
import com.vtop.portal.PortalSessionProvider
import com.vtop.core.AppState
import com.vtop.core.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun premiumSurfaceColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF141414) else Color(0xFFFFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VtopPortalScreen(
    vtopClient: VtopClient,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageTitle by remember { mutableStateOf("VTOP") }
    var isLoading by remember { mutableStateOf(true) }
    var authStatusMessage by remember { mutableStateOf("Initializing secure session...") }
    var sessionError by remember { mutableStateOf<String?>(null) }

    var activeClient by remember { mutableStateOf<VtopClient?>(null) }
    var sessionKey by remember { mutableIntStateOf(0) }
    var isAuthenticated by remember { mutableStateOf(false) }

    val portalPreferences = remember { context.getSharedPreferences("vtop_portal_preferences", Context.MODE_PRIVATE) }
    val isParallel = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE).getBoolean("PARALLEL_PORTAL_SESSION", false) }

    var desktopMode by remember { mutableStateOf(portalPreferences.getBoolean("desktop_mode", true)) }
    var vtopThemeDark by remember { mutableStateOf(portalPreferences.getBoolean("vtop_theme_dark", false)) }
    var expandedMenu by remember { mutableStateOf(false) }

    suspend fun executeLoginFlow() {
        isLoading = true
        isAuthenticated = false
        sessionError = null
        authStatusMessage = "Connecting to VTOP..."

        val result = PortalSessionProvider.getOrCreateSession(
            context = context,
            isParallel = isParallel,
            fallbackClient = vtopClient,
            onStatusUpdate = { message ->
                withContext(Dispatchers.Main) { authStatusMessage = message }
            },
            onOtpRequested = { resolver ->
                withContext(Dispatchers.Main) {
                    authStatusMessage = "Awaiting OTP Verification..."
                    AppState.currentOtpResolver.value = resolver
                }
            }
        )

        result.onSuccess { client ->
            withContext(Dispatchers.Main) {
                activeClient = client
                sessionKey++
                isAuthenticated = true
            }
        }.onFailure { error ->
            withContext(Dispatchers.Main) { sessionError = error.message ?: "Unknown error occurred" }
        }

        withContext(Dispatchers.Main) { isLoading = false }
    }

    LaunchedEffect(Unit) {
        if (isParallel) {
            val portalClient = SessionManager.getPortalClient()
            if (portalClient != null) {
                activeClient = portalClient
                isAuthenticated = true
                isLoading = false
            } else {
                executeLoginFlow()
            }
        } else {
            // Using the shared sync client requires us to ensure it's logged in
            executeLoginFlow()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = "Secure Session", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(text = pageTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Text(
                            text = if (isParallel) "Parallel Session Active" else "Shared Session Active",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { expandedMenu = true }, enabled = isAuthenticated) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (vtopThemeDark) "VTOP theme: dark" else "VTOP theme: light") },
                                onClick = {
                                    expandedMenu = false
                                    vtopThemeDark = !vtopThemeDark
                                    portalPreferences.edit().putBoolean("vtop_theme_dark", vtopThemeDark).apply()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (desktopMode) "Desktop Mode" else "Mobile Mode") },
                                onClick = {
                                    expandedMenu = false
                                    desktopMode = !desktopMode
                                    portalPreferences.edit().putBoolean("desktop_mode", desktopMode).apply()
                                    Toast.makeText(context, if (desktopMode) "Mobile mode enabled" else "Desktop mode enabled", Toast.LENGTH_SHORT).show()
                                    if (isParallel) SessionManager.setPortalClient(null)
                                    scope.launch { executeLoginFlow() }
                                }
                            )
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { expandedMenu = false; PortalController.reload() })
                            DropdownMenuItem(text = { Text("Force Refresh Session") }, onClick = { expandedMenu = false; scope.launch { executeLoginFlow() } })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = premiumSurfaceColor(),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Mask the WebView completely until authentication succeeds
            if (!isAuthenticated || activeClient == null) {
                VtopWebViewLoading(
                    error = sessionError,
                    statusMessage = authStatusMessage,
                    onRetry = if (sessionError != null) { { scope.launch { executeLoginFlow() } } } else null,
                    onBack = onBack
                )
            } else {
                PortalHost(
                    activeClient = activeClient!!,
                    sessionKey = sessionKey,
                    desktopMode = desktopMode,
                    vtopThemeDark = vtopThemeDark,
                    onPageLoading = { isLoading = it },
                    onTitleUpdate = { pageTitle = it },
                    onSessionExpired = { scope.launch { executeLoginFlow() } }
                )
            }

            AnimatedVisibility(visible = isLoading && isAuthenticated, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            }
        }
    }
}

@Composable
fun VtopWebViewLoading(error: String?, statusMessage: String, onRetry: (() -> Unit)?, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Authentication Failed", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                        Text("Go Back", color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (onRetry != null) {
                        Button(onClick = onRetry, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Text("Retry", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            } else {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Preparing VTOP...", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(statusMessage, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
    }
}