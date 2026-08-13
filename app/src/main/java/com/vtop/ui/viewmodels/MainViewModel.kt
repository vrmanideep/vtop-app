package com.vtop.ui.viewmodels

import android.app.Application
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import com.vtop.core.*
import com.vtop.utils.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(
    val currentTab: String = "HOME",
    val isProfileSubPage: Boolean = false,
    val showPortal: Boolean = false,
    val navStyle: String = "STATIC",
    val showOutings: Boolean = true,
    val mergeLabs: Boolean = true,
    val mergeMarks: Boolean = true,
    val isForceAttendanceSyncing: Boolean = false,
    val isForceTimetableSyncing: Boolean = false,
    val dockOffset: Offset = Offset.Zero
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        MainUiState(
            navStyle = Vault.getNavStyle(application).takeIf { it.isNotBlank() } ?: "STATIC",
            showOutings = prefs.getBoolean("SHOW_OUTINGS", true),
            mergeLabs = prefs.getBoolean("MERGE_LABS", true),
            mergeMarks = prefs.getBoolean("MERGE_MARKS", true)
        )
    )
    val uiState = _uiState.asStateFlow()

    // Centralized Repository Flows
    val timetable = TimetableRepository.timetable
    val attendance = AttendanceRepository.attendance
    val exams = ExamsRepository.exams
    val marks = MarksRepository.marks
    val historySummary = GradesRepository.historySummary
    val historyItems = GradesRepository.historyItems
    val outings = OutingsRepository.outings
    val profile = ProfileRepository.profile

    // Global App State Exposures
    val syncStatus = AppState.syncStatus
    val appError = AppState.appError
    val currentOtpResolver = AppState.currentOtpResolver

    fun updateTab(tab: String) = _uiState.update { it.copy(currentTab = tab) }
    fun updateProfileSubPage(isSub: Boolean) = _uiState.update { it.copy(isProfileSubPage = isSub) }
    fun updatePortalVisibility(show: Boolean) = _uiState.update { it.copy(showPortal = show) }
    fun updateForceAttSync(isSyncing: Boolean) = _uiState.update { it.copy(isForceAttendanceSyncing = isSyncing) }
    fun updateForceTTSync(isSyncing: Boolean) = _uiState.update { it.copy(isForceTimetableSyncing = isSyncing) }
    fun updateDockOffset(offset: Offset) = _uiState.update { it.copy(dockOffset = offset) }

    fun setNavStyle(style: String) {
        Vault.saveNavStyle(getApplication(), style)
        _uiState.update { it.copy(navStyle = style) }
    }

    fun setShowOutings(show: Boolean) {
        prefs.edit().putBoolean("SHOW_OUTINGS", show).apply()
        _uiState.update { it.copy(showOutings = show) }
    }

    fun setMergeLabs(merge: Boolean) {
        prefs.edit().putBoolean("MERGE_LABS", merge).apply()
        _uiState.update { it.copy(mergeLabs = merge) }
    }

    fun setMergeMarks(merge: Boolean) {
        prefs.edit().putBoolean("MERGE_MARKS", merge).apply()
        _uiState.update { it.copy(mergeMarks = merge) }
    }
}