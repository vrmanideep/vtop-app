@file:Suppress("SpellCheckingInspection", "UNUSED_VARIABLE")

package com.vtop.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.vtop.network.VtopClient
import com.vtop.core.AppState
import com.vtop.core.AuthStateManager
import com.vtop.core.SessionManager
import com.vtop.sync.SyncManager
import com.vtop.ui.screens.auth.LoginScreen
import com.vtop.ui.screens.auth.OnboardingFlow
import com.vtop.ui.theme.AppTheme
import com.vtop.ui.theme.AppThemeMode
import com.vtop.ui.theme.AuthActionCallback
import com.vtop.ui.theme.AuthState
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPrefs = getSharedPreferences("VTOP_PREFS", MODE_PRIVATE)
        val isExplicitlyLoggedOut = sharedPrefs.getBoolean("IS_EXPLICITLY_LOGGED_OUT", false)

        val credentials = Vault.getCredentials(this)
        val savedReg = credentials[0]
        val savedPass = credentials[1]

        if (!isExplicitlyLoggedOut &&
            !savedReg.isNullOrEmpty() &&
            !savedPass.isNullOrEmpty()
        ) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val savedThemeString = sharedPrefs.getString("APP_THEME", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val savedTheme = AppThemeMode.valueOf(savedThemeString)

        AuthStateManager.currentState.value = AuthState.FORM

        setContent {
            var showOnboarding by remember { mutableStateOf(false) }

            AppTheme(themeMode = savedTheme) {
                if (showOnboarding) {
                    OnboardingFlow(onComplete = {
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    })
                } else {
                    LoginScreen(
                        savedReg = savedReg,
                        savedPass = savedPass,
                        callback = object : AuthActionCallback {

                            override fun onLoginSubmit(regNo: String, pass: String) {
                                Vault.saveCredentials(this@LoginActivity, regNo, pass)
                                sharedPrefs.edit { putBoolean("IS_EXPLICITLY_LOGGED_OUT", false) }

                                AuthStateManager.currentState.value = AuthState.LOADING_SEMESTERS

                                lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        val client = VtopClient(this@LoginActivity, regNo, pass)
                                        client.reinitializeSession(this@LoginActivity)

                                        var loginSuccess = false
                                        var attempts = 0
                                        val maxAttempts = 3

                                        while (!loginSuccess && attempts < maxAttempts) {
                                            attempts++
                                            if (attempts > 1) {
                                                withContext(Dispatchers.Main) { AuthStateManager.loginError.value = "Retrying login ($attempts/$maxAttempts)..." }
                                                delay(1.seconds)
                                            }

                                            loginSuccess = client.autoLogin(this@LoginActivity, object : VtopClient.LoginListener {
                                                override fun onStatusUpdate(message: String) { Log.d("VTOP_LOGIN", message) }
                                                override fun onOtpRequired(resolver: VtopClient.OtpResolver) {
                                                    lifecycleScope.launch(Dispatchers.Main) { AppState.currentOtpResolver.value = resolver }
                                                }
                                            })
                                        }

                                        if (loginSuccess) {
                                            // Inject the authorized client into the App Session
                                            SessionManager.setSyncClient(client)

                                            if (client.authorizedId != null && !client.authorizedId.isEmpty() && !client.authorizedId.equals(regNo)) {
                                                Vault.saveRegNo(this@LoginActivity, client.authorizedId)
                                            }

                                            val semestersList = client.fetchSemesters()
                                            val firstSemName = semestersList.firstOrNull()?.get("name") ?: ""
                                            val currentIndex = getActiveSemesterIndex(this@LoginActivity, firstSemName)

                                            val processedSemesters = semestersList.mapIndexed { index, map ->
                                                map.toMutableMap().apply { this["isCurrent"] = (index == currentIndex).toString() }
                                            }

                                            withContext(Dispatchers.Main) {
                                                AuthStateManager.loginError.value = null
                                                AuthStateManager.fetchedSemesters.value = processedSemesters
                                                AuthStateManager.currentState.value = AuthState.SELECT_SEMESTER
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                AuthStateManager.loginError.value = "Login failed. VTOP might be unresponsive."
                                                AuthStateManager.currentState.value = AuthState.FORM
                                            }
                                        }
                                    } catch (e: com.vtop.network.VtopException) {
                                        withContext(Dispatchers.Main) {
                                            AuthStateManager.loginError.value = e.message
                                            AuthStateManager.currentState.value = AuthState.FORM
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            AuthStateManager.loginError.value = "Network error: ${e.message}"
                                            AuthStateManager.currentState.value = AuthState.FORM
                                        }
                                    }
                                }
                            }

                            override fun onSemesterSelect(semId: String, semName: String) {
                                Vault.saveSelectedSemester(this@LoginActivity, semId, semName)

                                // Trigger sync instantly without blocking the UI
                                lifecycleScope.launch(Dispatchers.IO) {
                                    SyncManager.performSync(
                                        context = this@LoginActivity,
                                        priorityTab = "HOME",
                                        forceNewSession = false,
                                        targetSemId = semId,
                                        skipLogin = true // Use the cookies we just established!
                                    )
                                }

                                showOnboarding = true
                            }
                        }
                    )
                }
            }
        }
    }

    private fun getActiveSemesterIndex(context: Context, topSemesterName: String): Int {
        if (topSemesterName.isEmpty()) return 0
        try {
            val jsonString = context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            if (!jsonObject.has("blocked_dates")) return 0

            val blocked = jsonObject.getJSONObject("blocked_dates")
            val keys = blocked.keys()

            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
            val today = Calendar.getInstance()
            val currentYear = today.get(Calendar.YEAR)
            val todayDate = sdf.parse(sdf.format(today.time)) ?: return 0

            while (keys.hasNext()) {
                val dateKey = keys.next()
                val eventName = blocked.getString(dateKey)
                val normalizedEvent = eventName.replace("Commencement of ", "", ignoreCase = true).trim()

                if (topSemesterName.contains(normalizedEvent, ignoreCase = true) || eventName.contains(topSemesterName, ignoreCase = true)) {
                    val targetDate = sdf.parse("$dateKey-$currentYear")
                    if (targetDate != null && targetDate.after(todayDate)) {
                        return 1
                    }
                }
            }
        } catch (_: Exception) { }
        return 0
    }
}