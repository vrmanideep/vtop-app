package com.vtop.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val VtopWhite = Color(0xFFFFFFFF)
val VtopBlack = Color(0xFF1F1D1D)
val VtopGrey = Color(0xFF6B7280)
val VtopPrimaryBlue = Color(0xFF327FD1)
val VtopGreen = Color(0xFF16A34A)
val VtopYellow = Color(0xFFF59E0B)
val VtopRed = Color(0xFFC91818)
val VtopPurple = Color(0xFF8A13DD)

val AccentColors = listOf(
    VtopPrimaryBlue,
    Color(0xFF10B981),
    Color(0xFFE11D48),
    Color(0xFF8B5CF6),
    Color(0xFFF59E0B)
)

val CoursePalette = listOf(VtopPrimaryBlue, VtopGreen, VtopYellow, VtopRed, VtopPurple)

// --- STATE MANAGER ---
object ThemeManager {
    var themeMode = mutableStateOf(AppThemeMode.SYSTEM)
    var useDynamicColor = mutableStateOf(true)
    var customAccent = mutableStateOf(VtopPrimaryBlue)
}

interface AuthActionCallback {
    fun onLoginSubmit(regNo: String, pass: String)
    fun onSemesterSelect(semId: String, semName: String)
}

enum class AuthState { FORM, LOADING_SEMESTERS, SELECT_SEMESTER, DOWNLOADING_DATA, OTP }
enum class DockPosition { TOP, BOTTOM, LEFT, RIGHT }
// REMOVED AMOLED enum
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

// --- DARK THEME (Base) ---
private val DarkColors = darkColorScheme(
    background = VtopBlack,
    surface = VtopBlack,
    surfaceVariant = Color(0xFF374151),
    primary = VtopPrimaryBlue,
    onBackground = VtopWhite,
    onSurface = VtopWhite,
    onSurfaceVariant = VtopGrey,
    error = VtopRed
)

// --- LIGHT THEME (Base) ---
private val LightColors = lightColorScheme(
    background = Color(0xFFF3F4F6),
    surface = VtopWhite,
    surfaceVariant = Color(0xFFE5E7EB),
    primary = VtopPrimaryBlue,
    onBackground = VtopBlack,
    onSurface = VtopBlack,
    onSurfaceVariant = VtopGrey,
    error = VtopRed
)

object AppColors {
    private val isDark: Boolean
        @Composable get() = when (ThemeManager.themeMode.value) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

    val glassBg: Color @Composable get() = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val glassBorder: Color @Composable get() = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
    val success: Color = VtopGreen
    val danger: Color = VtopRed
    val warning: Color = VtopYellow
    val info: Color = VtopPrimaryBlue
}

@Composable
fun AppTheme(
    themeMode: AppThemeMode = ThemeManager.themeMode.value,
    useDynamicColor: Boolean = ThemeManager.useDynamicColor.value,
    customAccent: Color = ThemeManager.customAccent.value,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors.copy(primary = customAccent)
        else -> LightColors.copy(primary = customAccent)
    }

    // Use 'remember' so we don't recreate the entire color scheme 60 times a second!
    val finalColorScheme = androidx.compose.runtime.remember(darkTheme, baseColorScheme) {
        if (darkTheme) {
            baseColorScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceVariant = Color(0xFF0A0A0A)
            )
        } else {
            baseColorScheme
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        content = content
    )
    MaterialTheme(
        colorScheme = finalColorScheme,
        content = content
    )
}