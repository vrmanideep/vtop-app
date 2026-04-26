package com.vtop.ui.screens.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtop.ui.core.LoginBridge
import com.vtop.ui.core.AppBridge
import com.vtop.ui.core.OtpForm
import com.vtop.ui.theme.*
import com.vtop.logic.*
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(savedReg: String?, savedPass: String?, callback: AuthActionCallback) {
    val currentState = LoginBridge.currentState.value
    val errorMsg = LoginBridge.loginError.value

    LaunchedEffect(errorMsg) {
        if (errorMsg != null) {
            delay(5000)
            LoginBridge.loginError.value = null
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
                    // Mocking some semesters for the UI if the bridge is empty
                    val sems = if (LoginBridge.fetchedSemesters.value.isEmpty()) {
                        listOf(
                            mapOf("id" to "AP2025264", "name" to "WIN 2024-25"),
                            mapOf("id" to "AP2024264", "name" to "FALL 2024-25"),
                            mapOf("id" to "AP2024254", "name" to "SUM 2023-24")
                        )
                    } else LoginBridge.fetchedSemesters.value

                    SemesterPickerView(sems, state == AuthState.DOWNLOADING_DATA, callback)
                }
                else -> { Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() } }
            }
        }

        // Error Toast
        AnimatedVisibility(
            visible = errorMsg != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).systemBarsPadding()
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(16.dp).fillMaxWidth(0.9f).clickable { LoginBridge.loginError.value = null }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = errorMsg ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // --- GLOBAL OTP OVERLAY ---
        val otpResolver = AppBridge.currentOtpResolver.value
        if (otpResolver != null) {
            OtpForm(
                onVerify = { otp ->
                    otpResolver.submit(otp)
                    AppBridge.currentOtpResolver.value = null
                },
                onCancel = {
                    otpResolver.cancel()
                    AppBridge.currentOtpResolver.value = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginFormView(savedReg: String?, savedPass: String?, isLoading: Boolean, callback: AuthActionCallback) {
    var regNo by remember { mutableStateOf(savedReg ?: "") }
    var password by remember { mutableStateOf(savedPass ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- LOGO HEADER ---
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(2.dp, AppColors.glassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("V", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(16.dp))
        Text(text = "VTOP", color = MaterialTheme.colorScheme.onBackground, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
        Text(text = "VIT-AP STUDENT PORTAL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

        Spacer(Modifier.height(40.dp))

        // --- INPUTS ---
        val colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = AppColors.glassBorder,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
        )

        OutlinedTextField(
            value = regNo,
            onValueChange = { regNo = it.uppercase() },
            label = { Text("Registration Number", fontSize = 12.sp) },
            singleLine = true,
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password", fontSize = 12.sp) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                Text(
                    text = if (passwordVisible) "HIDE" else "SHOW",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = !isLoading) { passwordVisible = !passwordVisible }.padding(8.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(24.dp))

        // --- SUBMIT BUTTON & PROGRESS BAR ---
        Button(
            onClick = {
                LoginBridge.cachedRegNo = regNo
                LoginBridge.cachedPassword = password
                callback.onLoginSubmit(regNo, password)
            },
            enabled = !isLoading && regNo.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isLoading) "LOGGING IN..." else "LOGIN",
                color = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }

        if (isLoading) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(0.5f).height(4.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // --- GLOBAL OTP OVERLAY ---
        val otpResolver = AppBridge.currentOtpResolver.value
        if (otpResolver != null) {
            OtpForm(
                onVerify = { otp ->
                    otpResolver.submit(otp)
                    AppBridge.currentOtpResolver.value = null
                },
                onCancel = {
                    otpResolver.cancel()
                    AppBridge.currentOtpResolver.value = null
                }
            )
        }
    }
}

@Composable
private fun SemesterPickerView(semesters: List<Map<String, String>>, isDownloading: Boolean, callback: AuthActionCallback) {
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
                    // Reads the logic from LoginActivity, falls back to index 0 for mocked UI
                    val isCurrent = sem["isCurrent"]?.toBoolean() ?: (index == 0)
                    val opacity = if (isCurrent) 1f else if (index == 1 || index == 0) 0.7f else 0.4f

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = opacity)),
                        border = BorderStroke(1.dp, AppColors.glassBorder.copy(alpha = opacity)),
                        modifier = Modifier.fillMaxWidth().clickable {
                            callback.onSemesterSelect(sem["id"] ?: "", sem["name"] ?: "")
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sem["name"] ?: "Unknown",
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