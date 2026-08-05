package com.vtop.core

import com.vtop.network.VtopClient
import android.content.Context
import android.util.Log
import com.vtop.utils.Vault
import com.vtop.ui.core.AppBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {

    private val _state = MutableStateFlow<SessionState>(SessionState.LoggedOut)
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private const val TAG = "SESSION"

    private var syncClient: VtopClient? = null
    private var portalClient: VtopClient? = null
    private var registrationClient: VtopClient? = null

    fun getSyncClient(): VtopClient? = syncClient

    fun setSyncClient(client: VtopClient?) {
        syncClient = client
        AppBridge.activeClient = client

        if (client == null) {
            Log.i(TAG, "Invalidated Sync Session")
            _state.value = SessionState.LoggedOut
        } else {
            Log.i(TAG, "Created Sync Session")
            _state.value = SessionState.LoggedIn
        }
    }

    fun invalidateSync() {
        setSyncClient(null)
    }

    fun getPortalClient(): VtopClient? = portalClient

    fun setPortalClient(client: VtopClient?) {
        portalClient = client
        if (client == null) {
            Log.i(TAG, "Invalidated Portal Session")
        } else {
            Log.i(TAG, "Created Portal Session")
        }
    }

    fun invalidatePortal() {
        setPortalClient(null)
    }

    fun getRegistrationClient(): VtopClient? = registrationClient

    fun setRegistrationClient(client: VtopClient?) {
        registrationClient = client
        if (client == null) {
            Log.i(TAG, "Invalidated Registration Session")
        } else {
            Log.i(TAG, "Created Registration Session")
        }
    }

    fun invalidateRegistration() {
        setRegistrationClient(null)
    }

    fun updateState(state: SessionState) {
        _state.value = state
    }

    fun isLoggedIn(): Boolean {
        return syncClient != null
    }

    fun invalidate() {
        Log.w(TAG, "Session invalidated.")
        invalidateSync()
        invalidatePortal()
        invalidateRegistration()
    }

    fun logout() {
        Log.i(TAG, "Logging out.")
        invalidateSync()
        invalidatePortal()
        invalidateRegistration()
    }

    suspend fun login(
        context: Context,
        forceNewSession: Boolean = false,
        loginBlock: suspend (VtopClient) -> Boolean
    ): Session {

        val (client, credentials) = createClient(context)

        val username = credentials.first ?: ""
        val password = credentials.second ?: ""

        Log.i(TAG, "Beginning login flow.")

        if (forceNewSession) {
            Log.i(TAG, "Force refresh requested. Reinitializing session.")
            client.reinitializeSession(context)
        }

        updateState(SessionState.LoggingIn)

        val success = loginBlock(client)

        if (!success) {
            updateState(SessionState.Failed("Authentication failed"))
            throw IllegalStateException("Login failed.")
        }

        updateState(SessionState.LoggedIn)

        Log.i(TAG, "Login successful.")

        return Session(
            client = client,
            username = username,
            authorizedId = "",
            semesterId = ""
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

    fun createClient(
        context: Context
    ): Pair<VtopClient, Pair<String?, String?>> {

        Log.d(TAG, "Loading credentials from Vault...")

        val creds = Vault.getCredentials(context)

        val username = creds[0]
        val password = creds[1]

        Log.d(TAG, "Creating VtopClient for user: $username")

        val client = VtopClient(
            context,
            username,
            password
        )

        setSyncClient(client)

        Log.d(TAG, "Client created successfully.")

        return client to (username to password)
    }
}