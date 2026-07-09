package com.vtop.telemetry.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vtop.telemetry.model.TelemetryEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TelemetryViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository =
        TelemetryRepository(application)

    private val _sessions =
        MutableStateFlow<List<TelemetrySessionInfo>>(emptyList())

    val sessions: StateFlow<List<TelemetrySessionInfo>> =
        _sessions.asStateFlow()

    private val _selectedSession =
        MutableStateFlow<TelemetrySessionInfo?>(null)

    val selectedSession: StateFlow<TelemetrySessionInfo?> =
        _selectedSession.asStateFlow()

    private val _events =
        MutableStateFlow<List<TelemetryEvent>>(emptyList())

    val events: StateFlow<List<TelemetryEvent>> =
        _events.asStateFlow()

    private val _filteredEvents =
        MutableStateFlow<List<TelemetryEvent>>(emptyList())

    val filteredEvents: StateFlow<List<TelemetryEvent>> =
        _filteredEvents.asStateFlow()

    private val _statistics =
        MutableStateFlow<TelemetryStatistics?>(null)

    val statistics: StateFlow<TelemetryStatistics?> =
        _statistics.asStateFlow()

    private val _filter =
        MutableStateFlow(TelemetryFilter.NONE)

    val filter: StateFlow<TelemetryFilter> =
        _filter.asStateFlow()

    private val _query =
        MutableStateFlow("")

    val query: StateFlow<String> =
        _query.asStateFlow()

    init {

        refreshSessions()
    }

    fun refreshSessions() {

        viewModelScope.launch {

            val sessions =
                withContext(Dispatchers.IO) {

                    repository.getSessions()
                }

            _sessions.value = sessions

            if (_selectedSession.value == null &&
                sessions.isNotEmpty()
            ) {

                openSession(
                    sessions.first()
                )
            }
        }
    }

    fun openSession(
        session: TelemetrySessionInfo
    ) {

        viewModelScope.launch {

            val events =
                withContext(Dispatchers.IO) {

                    repository.loadSession(session)
                }

            val stats =
                TelemetryStatistics.from(events)

            _selectedSession.value =
                session

            _events.value =
                events

            _statistics.value =
                stats

            applySearch()
        }
    }

    fun setSearchQuery(
        query: String
    ) {

        _query.value = query

        applySearch()
    }

    fun setFilter(
        filter: TelemetryFilter
    ) {

        _filter.value = filter

        applySearch()
    }

    private fun applySearch() {

        _filteredEvents.value =

            TelemetrySearch.filterAndSearch(

                events = _events.value,

                filter = _filter.value,

                query = _query.value
            )
    }

    fun deleteSelectedSession() {

        val session =
            _selectedSession.value
                ?: return

        viewModelScope.launch {

            withContext(Dispatchers.IO) {

                repository.deleteSession(session)
            }

            _selectedSession.value = null

            _events.value = emptyList()

            _filteredEvents.value = emptyList()

            _statistics.value = null

            refreshSessions()
        }
    }

    fun clearAllSessions() {

        viewModelScope.launch {

            withContext(Dispatchers.IO) {

                repository.clear()
            }

            _selectedSession.value = null

            _events.value = emptyList()

            _filteredEvents.value = emptyList()

            _statistics.value = null

            refreshSessions()
        }
    }

    fun deleteSession(session: com.vtop.telemetry.viewer.TelemetrySessionInfo) {}
}