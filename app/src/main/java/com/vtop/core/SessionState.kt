package com.vtop.core

sealed interface SessionState {

    data object LoggedOut : SessionState

    data object LoggingIn : SessionState

    data object LoggedIn : SessionState

    data object Refreshing : SessionState

    data class Failed(
        val message: String? = null
    ) : SessionState
}