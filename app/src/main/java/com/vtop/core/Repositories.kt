package com.vtop.core

import android.content.Context
import com.vtop.models.*
import com.vtop.utils.Vault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppRepositories {
    fun loadAll(context: Context) {
        TimetableRepository.load(context)
        AttendanceRepository.load(context)
        ExamsRepository.load(context)
        MarksRepository.load(context)
        GradesRepository.load(context)
        OutingsRepository.load(context)
        CalendarRepository.load(context)
        ProfileRepository.load(context)
        SemesterRepository.load(context)
    }
}

object TimetableRepository {
    private val _timetable = MutableStateFlow<TimetableModel?>(null)
    val timetable: StateFlow<TimetableModel?> = _timetable.asStateFlow()

    fun load(context: Context) { _timetable.value = Vault.getTimetable(context) }
    fun update(context: Context, data: TimetableModel) { Vault.saveTimetable(context, data); _timetable.value = data }
}

object AttendanceRepository {
    private val _attendance = MutableStateFlow<List<AttendanceModel>>(emptyList())
    val attendance: StateFlow<List<AttendanceModel>> = _attendance.asStateFlow()

    fun load(context: Context) { _attendance.value = Vault.getAttendance(context) ?: emptyList() }
    fun update(context: Context, data: List<AttendanceModel>) { Vault.saveAttendance(context, data); _attendance.value = data }
}

object ExamsRepository {
    private val _exams = MutableStateFlow<List<ExamScheduleModel>>(emptyList())
    val exams: StateFlow<List<ExamScheduleModel>> = _exams.asStateFlow()

    fun load(context: Context) { _exams.value = Vault.getExamSchedule(context) ?: emptyList() }
    fun update(context: Context, data: List<ExamScheduleModel>) { Vault.saveExamSchedule(context, data); _exams.value = data }
}

object MarksRepository {
    private val _marks = MutableStateFlow<List<CourseMark>>(emptyList())
    val marks: StateFlow<List<CourseMark>> = _marks.asStateFlow()

    fun load(context: Context) { _marks.value = Vault.getMarks(context) ?: emptyList() }
    fun update(context: Context, data: List<CourseMark>) { Vault.saveMarks(context, data); _marks.value = data }
}

object GradesRepository {
    private val _grades = MutableStateFlow<List<CourseGrade>>(emptyList())
    val grades: StateFlow<List<CourseGrade>> = _grades.asStateFlow()

    private val _historySummary = MutableStateFlow<CGPASummary?>(null)
    val historySummary: StateFlow<CGPASummary?> = _historySummary.asStateFlow()

    private val _historyItems = MutableStateFlow<List<GradeHistoryItem>>(emptyList())
    val historyItems: StateFlow<List<GradeHistoryItem>> = _historyItems.asStateFlow()

    fun load(context: Context) {
        _grades.value = Vault.getGrades(context) ?: emptyList()
        _historySummary.value = Vault.getCGPASummary(context)
        _historyItems.value = Vault.getHistory(context) ?: emptyList()
    }

    fun updateGrades(context: Context, data: List<CourseGrade>) {
        Vault.saveGrades(context, data)
        _grades.value = data
    }

    fun updateHistory(context: Context, summary: CGPASummary?, items: List<GradeHistoryItem>) {
        Vault.saveCGPASummary(context, summary)
        Vault.saveHistory(context, items)
        _historySummary.value = summary
        _historyItems.value = items
    }
}

object OutingsRepository {
    private val _outings = MutableStateFlow<List<OutingModel>>(emptyList())
    val outings: StateFlow<List<OutingModel>> = _outings.asStateFlow()

    fun load(context: Context) { _outings.value = Vault.getOutings(context) ?: emptyList() }
    fun update(context: Context, data: List<OutingModel>) { Vault.saveOutings(context, data); _outings.value = data }
}

object CalendarRepository {
    private val _calendar = MutableStateFlow<List<AcademicCalendarEvent>>(emptyList())
    val calendar: StateFlow<List<AcademicCalendarEvent>> = _calendar.asStateFlow()

    private val _semesterId = MutableStateFlow("")

    fun load(context: Context) {
        val semId = Vault.getSelectedSemester(context)[0] ?: ""
        _semesterId.value = semId
        if (semId.isNotEmpty()) {
            _calendar.value = Vault.getAcademicCalendar(context, semId) ?: emptyList()
        }
    }

    fun update(context: Context, semId: String, data: List<AcademicCalendarEvent>) {
        Vault.saveAcademicCalendar(context, semId, data)
        _semesterId.value = semId
        _calendar.value = data
    }
}

object ProfileRepository {
    private val _profile = MutableStateFlow<Map<String, Map<String, String>>?>(null)
    val profile: StateFlow<Map<String, Map<String, String>>?> = _profile.asStateFlow()

    fun load(context: Context) { _profile.value = Vault.getProfile(context) }
    fun update(context: Context, data: Map<String, Map<String, String>>) { Vault.saveProfile(context, data); _profile.value = data }
}

object SemesterRepository {
    private val _isSemesterCompleted = MutableStateFlow(false)
    val isSemesterCompleted: StateFlow<Boolean> = _isSemesterCompleted.asStateFlow()

    fun load(context: Context) {
        _isSemesterCompleted.value = false // Defaulted as transition engine was removed
    }

    fun updateCompletedStatus(status: Boolean) { _isSemesterCompleted.value = status }
}