package com.vtop.portal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PortalController {

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _destination = MutableStateFlow(PortalDestination.UNKNOWN)
    val destination: StateFlow<PortalDestination> = _destination.asStateFlow()

    fun openHome() {
        _destination.value = PortalDestination.HOME
    }

    fun openAttendance() {
        _destination.value = PortalDestination.ATTENDANCE
    }

    fun openTimetable() {
        _destination.value = PortalDestination.TIMETABLE
    }

    fun openMarks() {
        _destination.value = PortalDestination.MARKS
    }

    fun openExams() {
        _destination.value = PortalDestination.EXAMS
    }

    fun openFaculty() {
        _destination.value = PortalDestination.FACULTY
    }

    fun openCourseRegistration() {
        _destination.value = PortalDestination.COURSE_REGISTRATION
    }

    fun updateCurrentUrl(url: String?) {
        _currentUrl.value = url
    }

    fun reset() {
        _currentUrl.value = null
        _destination.value = PortalDestination.UNKNOWN
    }
}