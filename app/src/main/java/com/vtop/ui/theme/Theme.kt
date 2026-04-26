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

// The predefined list of accent colors users can pick from
val AccentColors = listOf(
    VtopPrimaryBlue,   // Original Blue
    Color(0xFF10B981), // Emerald Green
    Color(0xFFE11D48), // Crimson Red
    Color(0xFF8B5CF6), // Royal Purple
    Color(0xFFF59E0B)  // Sunset Amber
)

val CoursePalette = listOf(VtopPrimaryBlue, VtopGreen, VtopYellow, VtopRed, VtopPurple)

// --- STATE MANAGER ---
object ThemeManager {
    var themeMode = mutableStateOf(AppThemeMode.SYSTEM)
    var useDynamicColor = mutableStateOf(true) // Idea 2
    var customAccent = mutableStateOf(VtopPrimaryBlue) // Idea 3
}

interface AuthActionCallback {
    fun onLoginSubmit(regNo: String, pass: String)
    fun onSemesterSelect(semId: String, semName: String)
}

enum class AuthState { FORM, LOADING_SEMESTERS, SELECT_SEMESTER, DOWNLOADING_DATA, OTP }
enum class DockPosition { TOP, BOTTOM, LEFT, RIGHT }
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

// --- DARK THEME ---
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

// --- LIGHT THEME ---
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
    val glassBg: Color @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val glassBorder: Color @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f)
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

    val colorScheme = when {
        // Dynamic Color (Android 12+)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Fallback to custom accent colors
        darkTheme -> DarkColors.copy(primary = customAccent)
        else -> LightColors.copy(primary = customAccent)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}