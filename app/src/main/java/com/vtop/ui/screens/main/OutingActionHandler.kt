package com.vtop.ui

import java.io.File

fun interface FetchCallback {
    fun onResult(data: Map<String, String>?)
}

interface OutingActionHandler {
    fun onWeekendSubmit(place: String, purpose: String, date: String, time: String, contact: String)
    fun onGeneralSubmit(place: String, purpose: String, fromDate: String, toDate: String, fromTime: String, toTime: String)
    fun onDelete(id: String, isWeekend: Boolean)
    fun onViewPass(id: String, isWeekend: Boolean, onReady: (File?) -> Unit)
    fun onFetchGeneralFormData(callback: FetchCallback)
    fun onFetchWeekendFormData(callback: FetchCallback) // NEW
}