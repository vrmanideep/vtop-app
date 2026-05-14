package com.vtop.utils

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

object AnalyticsManager {
    private var analytics: FirebaseAnalytics = Firebase.analytics

    // Log which screens the user visits (e.g., "Timetable", "Faculty", "Bunk_Simulator")
    fun logScreenView(screenName: String) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
        }
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    // Log specific actions (e.g., "Sync_Clicked", "Outpass_Downloaded")
    fun logEvent(eventName: String, params: Map<String, String> = emptyMap()) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            bundle.putString(key, value)
        }
        // Firebase event names cannot have spaces, so we replace them with underscores
        val safeEventName = eventName.replace(" ", "_")
        analytics.logEvent(safeEventName, bundle)
    }

    // Tag the user's branch so you can filter analytics by department
    fun setUserBranch(branch: String) {
        analytics.setUserProperty("student_branch", branch)
    }
}