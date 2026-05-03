package com.vtop.ui.core

import androidx.compose.runtime.mutableStateOf
import com.vtop.models.AttendanceModel
import com.vtop.models.CGPASummary
import com.vtop.models.CourseGrade
import com.vtop.models.CourseMark
import com.vtop.models.ExamScheduleModel
import com.vtop.models.GradeHistoryItem
import com.vtop.models.OutingModel
import com.vtop.models.SemesterOption
import com.vtop.models.TimetableModel
import com.vtop.network.VtopClient
import com.vtop.ui.theme.AuthState

object AppBridge {
    val profileState = mutableStateOf<Map<String, Map<String, String>>?>(null)
    var timetableState = mutableStateOf<TimetableModel?>(null)
    var attendanceState = mutableStateOf<List<AttendanceModel>>(emptyList())
    var examsState = mutableStateOf<List<ExamScheduleModel>>(emptyList())
    var outingsState = mutableStateOf<List<OutingModel>>(emptyList())

    var semestersState = mutableStateOf<List<SemesterOption>>(emptyList())
    var marksState = mutableStateOf<List<CourseMark>>(emptyList())
    var gradesState = mutableStateOf<List<CourseGrade>>(emptyList())
    var historySummaryState = mutableStateOf<CGPASummary?>(null)
    var historyItemsState = mutableStateOf<List<GradeHistoryItem>>(emptyList())

    // --- NEW: Semester Transition State ---
    var isSemesterCompleted = mutableStateOf(false)

    var appError = mutableStateOf<String?>(null)
    var onMarksDataRequest: ((String, Boolean) -> Unit)? = null
    var syncStatus = mutableStateOf("IDLE")
    var currentOtpResolver = mutableStateOf<VtopClient.OtpResolver?>(null)

    @JvmStatic fun showError(message: String) { appError.value = message }
    @JvmStatic fun updateMarksOnly(newMarks: List<CourseMark>) { marksState.value = newMarks }
    @JvmStatic fun updateGradesOnly(newGrades: List<CourseGrade>) { gradesState.value = newGrades }
}

object LoginBridge {
    var currentState = mutableStateOf(AuthState.FORM)
    var outingsState = mutableStateOf<List<OutingModel>>(emptyList())
    val profileState = mutableStateOf<Map<String, Map<String, String>>?>(null)
    var fetchedSemesters = mutableStateOf<List<Map<String, String>>>(emptyList())
    var loginError = mutableStateOf<String?>(null)
    var cachedRegNo = ""
    var cachedPassword = ""
}