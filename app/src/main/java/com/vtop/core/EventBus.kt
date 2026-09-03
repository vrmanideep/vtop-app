package com.vtop.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class AppEvent {
    data class SyncStatusChanged(val status: String) : AppEvent()
    data class ToastMessage(val message: String, val isLong: Boolean = false) : AppEvent()
    data class SyncError(val exception: Exception) : AppEvent()
    data class AuthOtpRequested(val resolver: Any) : AppEvent()
    object SyncCompleted : AppEvent()
    object CalendarUpdated : AppEvent()
}

object EventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 10)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    fun tryEmit(event: AppEvent) {
        _events.tryEmit(event)
    }
}