package com.vtop.core

import androidx.compose.runtime.mutableStateOf
import com.vtop.ui.theme.AuthState

object AuthStateManager {
    var currentState = mutableStateOf(AuthState.FORM)
    var loginError = mutableStateOf<String?>(null)
    var fetchedSemesters = mutableStateOf<List<Map<String, String>>>(emptyList())
    var cachedRegNo = ""
    var cachedPassword = ""
}