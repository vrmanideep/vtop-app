package com.vtop.models

import java.io.Serializable

data class OutingModel(
    val id: String,
    val type: String, // "GENERAL" or "WEEKEND"
    val place: String,
    val purpose: String,
    val fromDate: String,
    val fromTime: String,
    val toDate: String,
    val toTime: String,
    val status: String,
    val canDownload: Boolean
) : Serializable