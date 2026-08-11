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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VtopPortalScreen(
    vtopClient: VtopClient,
    onBack: () -> Unit
){
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageTitle by remember { mutableStateOf("VTOP") }
    var isLoading by remember { mutableStateOf(true) }
    var sessionError by remember { mutableStateOf<String?>(null) }
    var activeClient by remember { mutableStateOf<VtopClient?>(null) }

    val portalPreferences = remember { context.getSharedPreferences("vtop_portal_preferences", Context.MODE_PRIVATE) }
    val isParallel = remember { context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE).getBoolean("PARALLEL_PORTAL_SESSION", false) }

    var desktopMode by remember { mutableStateOf(portalPreferences.getBoolean("desktop_mode", true)) }
    var expandedMenu by remember { mutableStateOf(false) }

    suspend fun executeLoginFlow() {
        isLoading = true
        sessionError = null

        val result = PortalSessionProvider.getOrCreateSession(
            context = context,
            isParallel = isParallel,
            fallbackClient = vtopClient,
            onStatusUpdate = { message ->
                withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
            },
            onOtpRequested = { resolver ->
                withContext(Dispatchers.Main) { AppState.currentOtpResolver.value = resolver }
            }
        )

        result.onSuccess { client ->
            withContext(Dispatchers.Main) { activeClient = client }
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
            } else {
                executeLoginFlow()
            }
        } else {
            activeClient = vtopClient
        }
    }

    if (sessionError != null) {
        VtopWebViewLoading(error = sessionError, onRetry = { scope.launch { executeLoginFlow() } })
        return
    }

    if (activeClient == null) {
        VtopWebViewLoading(error = null, onRetry = null)
        return
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { Text(text = pageTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { expandedMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                        DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (desktopMode) "Desktop Mode" else "Mobile Mode") },
                                onClick = {
                                    expandedMenu = false
                                    desktopMode = !desktopMode
                                    portalPreferences.edit().putBoolean("desktop_mode", desktopMode).apply()
                                    Toast.makeText(context, if (desktopMode) "Mobile mode enabled" else "Desktop mode enabled", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(text = { Text("Refresh") }, onClick = { expandedMenu = false; PortalController.reload() })
                            DropdownMenuItem(text = { Text("Force Refresh Session") }, onClick = { expandedMenu = false; scope.launch { executeLoginFlow() } })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0f0f0f), titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            PortalHost(
                activeClient = activeClient!!,
                desktopMode = desktopMode,
                onPageLoading = { isLoading = it },
                onTitleUpdate = { pageTitle = it },
                onSessionExpired = { scope.launch { executeLoginFlow() } }
            )

            AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary, trackColor = Color.Transparent)
            }
        }
    }
}

@Composable
fun VtopWebViewLoading(error: String?, onRetry: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error != null) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFf87171), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Could not open VTOP", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color.Gray, fontSize = 12.sp)
                if (onRetry != null) {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onRetry, shape = RoundedCornerShape(10.dp)) { Text("Retry") }
                }
            } else {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(16.dp))
                Text("Opening VTOP...", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("Injecting your session securely", color = Color(0xFF555555), fontSize = 11.sp)
            }
        }
    }
}