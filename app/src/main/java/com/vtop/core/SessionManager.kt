package com.vtop.core

import com.vtop.network.VtopClient
import com.vtop.ui.core.AppBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {

    private val _state = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    var client: VtopClient?
        get() = AppBridge.activeClient
        set(value) {
            AppBridge.activeClient = value

            _state.value =
                if (value == null)
                    SessionState.LoggedOut
                else
                    SessionState.LoggedIn
        }

    fun updateState(state: SessionState) {
        _state.value = state
    }

    fun isLoggedIn(): Boolean {
        return client != null
    }

    fun invalidate() {
        client = null
    }

    fun logout() {
        client = null
    }

    suspend fun login() {
        throw UnsupportedOperationException(
            "SessionManager.login() has not been migrated yet."
        )
    }

     fun extractAuthorizedIdFromContent(html: String?): String? {
        if (html.isNullOrBlank()) return null

        val regNoPattern = Regex("""\b\d{2}[a-zA-Z]{3}\d{4}\b""")
        val match = regNoPattern.find(html)
        if (match != null) return match.value.uppercase()

        val jsPattern = Regex("""(?:let|var)\s+id\s*=\s*['"]([^'"]+)['"]""")
        val jsMatch = jsPattern.find(html)
        if (jsMatch != null) return jsMatch.groupValues[1].uppercase()

        return null
    }
}