package com.vtop.models

data class ExamScheduleModel(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val classId: String,
    val slot: String,
    val examDate: String,
    val examSession: String,
    val reportingTime: String,
    val examTime: String,
    val venue: String,
    val seatLocation: String,
    val seatNumber: String,
    val examType: String
)