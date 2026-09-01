package com.vtop.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val VtopWhite = Color(0xFFFFFFFF)
val VtopBlack = Color(0xFF000000) // True AMOLED Black
val VtopPrimaryBlue = Color(0xFF327FD1)
val VtopGreen = Color(0xFF10B981)
val VtopYellow = Color(0xFFF59E0B)
val VtopRed = Color(0xFFE11D48)
val VtopPurple = Color(0xFF8B5CF6)

val AccentColors = listOf(
    VtopPrimaryBlue,
    VtopGreen,
    VtopRed,
    VtopPurple,
    VtopYellow
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
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

object AppColors {
    private val isDark: Boolean
        @Composable get() = when (ThemeManager.themeMode.value) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        }

    val glassBg: Color @Composable get() = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
    val glassBorder: Color @Composable get() = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    val success: Color = VtopGreen
    val danger: Color = VtopRed
    val warning: Color = VtopYellow
    val info: Color = VtopPrimaryBlue
}

// Generates a complete cohesive palette from a single custom accent color
private fun createCustomColorScheme(accent: Color, isDark: Boolean): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.3f),
            onPrimaryContainer = accent.copy(alpha = 0.9f),
            secondary = accent,
            secondaryContainer = accent.copy(alpha = 0.2f),
            onSecondaryContainer = accent.copy(alpha = 0.9f),
            background = VtopBlack,
            surface = VtopBlack,
            surfaceVariant = Color(0xFF141414), // Clean, premium dark grey for cards
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFAAAAAA), // High contrast light grey for readable text
            error = VtopRed,
            outline = Color(0xFF2A2A2A)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = accent.copy(alpha = 0.2f),
            onPrimaryContainer = accent,
            secondary = accent,
            secondaryContainer = accent.copy(alpha = 0.1f),
            onSecondaryContainer = accent,
            background = Color(0xFFF9FAFB),
            surface = Color.White,
            surfaceVariant = Color(0xFFF3F4F6),
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color.DarkGray,
            error = VtopRed,
            outline = Color(0xFFE5E7EB)
        )
    }
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

    val context = LocalContext.current

    val resolvedScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        // We let Android handle the accent colors (primary, secondary, etc) based on the wallpaper.
        // But we MUST manually override the structural colors so they don't look muddy or unreadable.
        if (darkTheme) {
            dynamic.copy(
                background = VtopBlack,
                surface = VtopBlack,
                surfaceVariant = Color(0xFF141414), // Forces cards to be clean dark grey
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFFAAAAAA), // Forces text to be visible
                error = VtopRed,
                outline = Color(0xFF2A2A2A)
            )
        } else {
            dynamic.copy(
                background = Color(0xFFF9FAFB),
                surface = Color.White,
                surfaceVariant = Color(0xFFF3F4F6),
                onBackground = Color.Black,
                onSurface = Color.Black,
                onSurfaceVariant = Color.DarkGray,
                error = VtopRed,
                outline = Color(0xFFE5E7EB)
            )
        }
    } else {
        createCustomColorScheme(customAccent, darkTheme)
    }

    MaterialTheme(
        colorScheme = resolvedScheme,
        content = content
    )
}