@file:Suppress("SpellCheckingInspection")

package com.vtop.ui.screens.auth

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.os.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Web
import androidx.compose.ui.text.style.TextAlign
import com.vtop.core.AuthStateManager
import com.vtop.core.AppState
import com.vtop.ui.components.OtpForm
import com.vtop.ui.theme.*
import kotlinx.coroutines.delay

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.vtop.logic.AuthHelper
import com.vtop.utils.AnalyticsManager

@Composable
fun LoginScreen(savedReg: String?, savedPass: String?, callback: AuthActionCallback) {
    LaunchedEffect(Unit) {
        AnalyticsManager.logScreenView("Login_Screen_View")
    }
    val context = LocalContext.current
    val currentState = AuthStateManager.currentState.value
    val errorMsg = AuthStateManager.loginError.value

    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            delay(5000)
            AuthStateManager.loginError.value = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentState,
            transitionSpec = {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
            },
            label = "LoginTransition"
        ) { state ->
            when (state) {
                AuthState.FORM, AuthState.LOADING_SEMESTERS -> {
                    LoginFormView(savedReg, savedPass, state == AuthState.LOADING_SEMESTERS, callback)
                }
                AuthState.SELECT_SEMESTER, AuthState.DOWNLOADING_DATA -> {
                    val sems = if (AuthStateManager.fetchedSemesters.value.isEmpty()) {
                        loadSemestersFromCache(context)
                    } else AuthStateManager.fetchedSemesters.value

                    SemesterPickerView(sems, state == AuthState.DOWNLOADING_DATA, callback)
                }
                else -> { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() } }
            }
        }

        AnimatedVisibility(
            visible = errorMsg != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).systemBarsPadding()
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(16.dp).fillMaxWidth(0.9f).clickable { AuthStateManager.loginError.value = null }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = errorMsg ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        val otpResolver = AppState.currentOtpResolver.value
        if (otpResolver != null) {

            var showGooglePrompt by remember {
                mutableStateOf(com.vtop.utils.Vault.getGoogleEmail(context).isEmpty() && !com.vtop.utils.Vault.hasPromptedGoogleSignIn(context))
            }

            if (showGooglePrompt) {
                GoogleSignInDialog(
                    onDismiss = {
                        com.vtop.utils.Vault.setHasPromptedGoogleSignIn(context, true)
                        showGooglePrompt = false
                    },
                    onSuccess = {
                        showGooglePrompt = false
                    }
                )
            } else {
                OtpForm(
                    onVerify = { otp ->
                        otpResolver.submit(otp)
                        AppState.currentOtpResolver.value = null
                    },
                    onCancel = {
                        otpResolver.cancel()
                        AppState.currentOtpResolver.value = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginFormView(savedReg: String?, savedPass: String?, isLoading: Boolean, callback: AuthActionCallback) {
    var regNo by remember { mutableStateOf(savedReg ?: "") }
    var password by remember { mutableStateOf(savedPass ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val freshCreds = com.vtop.utils.Vault.getCredentials(context)
        val freshReg = freshCreds[0]
        val freshPass = freshCreds[1]

        if (!freshReg.isNullOrBlank()) regNo = freshReg
        if (!freshPass.isNullOrBlank()) password = freshPass
    }

    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("V", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
        Text(text = "VTOP", color = MaterialTheme.colorScheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = "VIT-AP student portal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        Spacer(Modifier.height(40.dp))

        val inputColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Username", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
            OutlinedTextField(
                value = regNo,
                onValueChange = { regNo = it.uppercase() },
                singleLine = true,
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
                colors = inputColors,
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Password", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                trailingIcon = {
                    Text(
                        text = if (passwordVisible) "Hide" else "Show",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(enabled = !isLoading) { passwordVisible = !passwordVisible }
                            .padding(8.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = inputColors,
                shape = RoundedCornerShape(10.dp)
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                AuthStateManager.cachedRegNo = regNo
                AuthStateManager.cachedPassword = password
                callback.onLoginSubmit(regNo, password)
            },
            enabled = !isLoading && regNo.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = "Log in", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
@Composable
private fun SemesterPickerView(semesters: List<Map<String, String>>, isDownloading: Boolean, callback: AuthActionCallback) {
    val context = LocalContext.current

    val currentSemIndex = remember(semesters) {
        semesters.indexOfFirst { it["isCurrent"] == "true" }.coerceAtLeast(0)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(text = "Select Semester", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(
            text = if (isDownloading) "Downloading your academic data..." else "Tap a semester to download your data",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        if (isDownloading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(semesters) { index, sem ->
                    val semName = sem["name"] ?: ""
                    val semId = sem["id"] ?: ""

                    val isCurrent = index == currentSemIndex

                    val distance = kotlin.math.abs(index - currentSemIndex)
                    val opacity = (1f - (distance * 0.25f)).coerceIn(0.35f, 1f)

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = opacity)),
                        border = BorderStroke(1.dp, AppColors.glassBorder.copy(alpha = opacity)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            callback.onSemesterSelect(semId, semName)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = semName,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = opacity),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .background(AppColors.success.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .border(1.dp, AppColors.success.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Current", color = AppColors.success, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleSignInDialog(onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val webClientId = context.getString(context.resources.getIdentifier("default_web_client_id", "string", context.packageName))
    val signInClient = remember { AuthHelper.getGoogleSignInClient(context, webClientId) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                            com.vtop.utils.Vault.setHasPromptedGoogleSignIn(context, true)
                            Toast.makeText(context, "Email linked successfully!", Toast.LENGTH_SHORT).show()
                            onSuccess(email)
                        } else {
                            signInClient.signOut()
                            Toast.makeText(context, "Firebase Auth Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                signInClient.signOut()
                Toast.makeText(context, "Must use @vitapstudent.ac.in email", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-Sync OTPs", fontWeight = FontWeight.Bold) },
        text = { Text("Link your @vitapstudent.ac.in email to let the app automatically read VTOP OTPs in the background. You won't have to manually enter them anymore.") },
        confirmButton = {
            Button(
                onClick = { launcher.launch(signInClient.signInIntent) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Link Google Account", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = {
                com.vtop.utils.Vault.setHasPromptedGoogleSignIn(context, true)
                onDismiss()
            }) { Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
private fun loadSemestersFromCache(context: Context): List<Map<String, String>> {

    val semesters = com.vtop.utils.Vault
        .getCalendarSemesterOptions(context)

    if (semesters.isEmpty()) {
        return listOf(
            mapOf(
                "id" to "DEFAULT",
                "name" to "Default Semester",
                "isCurrent" to "true"
            )
        )
    }

    val selected = com.vtop.utils.Vault
        .getSelectedSemester(context)
        .firstOrNull()

    return semesters.map {
        mapOf(
            "id" to it.id,
            "name" to it.name,
            "isCurrent" to (it.id == selected).toString()
        )
    }
}

@Composable
fun OnboardingFlow(onComplete: () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)

    // Controls which screen is currently visible in the sequence
    var currentStep by remember { mutableIntStateOf(0) }

    // Launcher for Android 13+ Notifications
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        currentStep++ // Move to the next step regardless of grant/deny
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith slideOutHorizontally { width -> -width } + fadeOut()
            }, label = "Onboarding"
        ) { step ->
            when (step) {
                0 -> {
                    val currentStyle = sharedPrefs.getString("NAV_STYLE", "DOCK") ?: "DOCK"

                    NavStylePickerStep(
                        initialSelection = currentStyle,
                        onContinue = { selectedStyle ->
                            sharedPrefs.edit().putString("NAV_STYLE", selectedStyle).apply()
                            currentStep++
                        }
                    )
                }
                1 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermissionStep(
                            title = "Stay in the Loop",
                            description = "Allow notifications so we can alert you about upcoming classes, exams, and critical attendance drops.",
                            icon = Icons.Default.Notifications,
                            onGrant = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            onSkip = { currentStep++ }
                        )
                    } else {
                        LaunchedEffect(Unit) { currentStep++ } // Skip if not needed on older Androids
                    }
                }
                // Add more steps (like Battery Optimization prompts) here as case 2, 3, etc.
                2 -> {
                    LaunchedEffect(Unit) { onComplete() }
                }
            }
        }
    }
}

@Composable
fun NavStylePickerStep(initialSelection: String, onContinue: (String) -> Unit) {
    var selectedStyle by remember { mutableStateOf(initialSelection) }

    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Make it yours.", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Choose your Nav Style", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(32.dp))

        // Modern Pill Option
        NavStyleCard(
            title = "Floating Pill",
            description = "Modern, compact, and completely immersive.",
            icon = Icons.Default.ViewCarousel,
            isSelected = selectedStyle == "DOCK",
            onClick = { selectedStyle = "DOCK" }
        )

        Spacer(Modifier.height(16.dp))

        // Classic Bar Option
        NavStyleCard(
            title = "Classic Bar",
            description = "Traditional full-width bottom navigation.",
            icon = Icons.Default.Web,
            isSelected = selectedStyle == "STATIC",
            onClick = { selectedStyle = "STATIC" }
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = { onContinue(selectedStyle) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = "Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun NavStyleCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
fun PermissionStep(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onGrant: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(text = "Allow Permission", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onSkip) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}