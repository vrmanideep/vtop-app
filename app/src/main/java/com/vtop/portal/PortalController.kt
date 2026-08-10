package com.vtop.portal

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class PortalCommand {
    object Reload : PortalCommand()
    object GoBack : PortalCommand()
    data class LoadUrl(val url: String) : PortalCommand()
    data class ExecuteJs(val script: String) : PortalCommand()
}

object PortalController {

    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private val _destination = MutableStateFlow(PortalDestination.UNKNOWN)
    val destination: StateFlow<PortalDestination> = _destination.asStateFlow()

    private val _commands = MutableSharedFlow<PortalCommand>(extraBufferCapacity = 10)
    val commands: SharedFlow<PortalCommand> = _commands.asSharedFlow()

    fun reload() { _commands.tryEmit(PortalCommand.Reload) }
    fun goBack() { _commands.tryEmit(PortalCommand.GoBack) }
    fun loadUrl(url: String) { _commands.tryEmit(PortalCommand.LoadUrl(url)) }
    fun executeJs(script: String) { _commands.tryEmit(PortalCommand.ExecuteJs(script)) }

    // Fast DOM navigation - Clicks the menu item natively without triggering a full page reload
    private fun navigateViaDom(menuName: String) {
        val script = """
            (function() {
                var links = Array.from(document.querySelectorAll('a, span'));
                var target = links.find(e => e.textContent.trim() === '$menuName');
                if (target) {
                    target.click();
                } else {
                    console.log('VTOP Menu item $menuName not found');
                }
            })();
        """.trimIndent()
        executeJs(script)
    }

    fun openHome() {
        _destination.value = PortalDestination.HOME
        navigateViaDom("Dashboard")
    }

    fun openAttendance() {
        _destination.value = PortalDestination.ATTENDANCE
        navigateViaDom("Class Attendance")
    }

    fun openTimetable() {
        _destination.value = PortalDestination.TIMETABLE
        navigateViaDom("Time Table")
    }

    fun openMarks() {
        _destination.value = PortalDestination.MARKS
        navigateViaDom("Marks View")
    }

    fun openExams() {
        _destination.value = PortalDestination.EXAMS
        navigateViaDom("Exam Schedule")
    }

    fun openFaculty() {
        _destination.value = PortalDestination.FACULTY
        navigateViaDom("Know Your Faculty")
    }

    fun openCourseRegistration() {
        _destination.value = PortalDestination.COURSE_REGISTRATION
        navigateViaDom("Course Registration")
    }

    fun updateCurrentUrl(url: String?) {
        _currentUrl.value = url
    }

    fun reset() {
        _currentUrl.value = null
        _destination.value = PortalDestination.UNKNOWN
    }
}