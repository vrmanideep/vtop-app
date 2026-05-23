package com.vtop.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.vtop.models.*
import com.vtop.ui.core.CourseReminder
import com.vtop.ui.screens.main.*
import com.vtop.ui.theme.AppThemeMode

@Composable
fun HomePageContent(
    timetable: TimetableModel,
    attendanceData: List<AttendanceModel>,
    examsData: List<ExamScheduleModel>,
    holidaysMap: Map<String, String>
) {

    Timetable(
        timetable = timetable,
        attendanceData = attendanceData,
        examsData = examsData,
        holidays = holidaysMap
    )
}

@Composable
fun AttendancePageContent(
    attendanceData: List<AttendanceModel>
) {

    Attendance(attendanceData)
}

@Composable
fun ExamsPageContent(
    examsData: List<ExamScheduleModel>
) {

    Exams(examsData)
}

@Composable
fun MarksPageContent(
    marksData: List<CourseMark>,
    historySummary: CGPASummary?,
    historyData: List<GradeHistoryItem>,
    onHistoryLoad: () -> Unit
) {

    Marks(
        marksData = marksData,
        historySummary = historySummary,
        historyData = historyData,
        onHistoryLoad = onHistoryLoad
    )
}

@Composable
fun OutingsPageContent(
    outingsData: List<OutingModel>,
    outingHandler: OutingActionHandler
) {

    VtopOutingsTab(
        outingsData = outingsData,
        handler = outingHandler
    )
}

@Composable
fun ProfilePageContent(
    onBack: () -> Unit,
    timetable: TimetableModel,
    examsData: List<ExamScheduleModel>,
    onOpenPortal: () -> Unit,
    currentTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    customAccent: Color,
    onAccentChange: (Color) -> Unit,
    currentNavStyle: String,
    onNavStyleChange: (String) -> Unit,
    mergeLabs: Boolean,
    onMergeLabsChange: (Boolean) -> Unit,
    mergeMarks: Boolean,
    onMergeMarksChange: (Boolean) -> Unit,
    showOutings: Boolean,
    onShowOutingsChange: (Boolean) -> Unit,
    onLogout: () -> Unit,
    profileData: Map<String, Map<String, String>>,
    selectedSemester: String,
    availableSemesters: List<SemesterOption>,
    onSemesterChange: (String) -> Unit,
    currentRegNo: String,
    currentPass: String,
    onCredentialsSave: (String, String) -> Unit,
    reminders: List<CourseReminder>,
    onDeleteReminder: (String) -> Unit,
    onNavigateToAnalytics: () -> Unit,
    lastSyncTime: String,
    onSyncClick: (Boolean) -> Unit,
    onNavigateToFaculty: () -> Unit
) {

    Profile(
        onBack = onBack,
        timetable = timetable,
        examsData = examsData,
        onOpenPortal = onOpenPortal,
        currentTheme = currentTheme,
        onThemeChange = onThemeChange,
        useDynamicColor = useDynamicColor,
        onDynamicColorChange = onDynamicColorChange,
        customAccent = customAccent,
        onAccentChange = onAccentChange,
        currentNavStyle = currentNavStyle,
        onNavStyleChange = onNavStyleChange,
        mergeLabs = mergeLabs,
        onMergeLabsChange = onMergeLabsChange,
        mergeMarks = mergeMarks,
        onMergeMarksChange = onMergeMarksChange,
        showOutings = showOutings,
        onShowOutingsChange = onShowOutingsChange,
        onLogout = onLogout,
        profileData = profileData,
        selectedSemester = selectedSemester,
        availableSemesters = availableSemesters,
        onSemesterChange = onSemesterChange,
        currentRegNo = currentRegNo,
        currentPass = currentPass,
        onCredentialsSave = onCredentialsSave,
        reminders = reminders,
        onDeleteReminder = onDeleteReminder,
        onNavigateToAnalytics = onNavigateToAnalytics,
        lastSyncTime = lastSyncTime,
        onSyncClick = onSyncClick,
        onNavigateToFaculty = onNavigateToFaculty
    )
}