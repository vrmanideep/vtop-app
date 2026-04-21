package com.vtop.models

// Shared model for the Minimal Dropdown
data class SemesterOption(
    val id: String,
    val name: String
)

// --- MARKS ---
data class MarkDetail(
    val title: String,
    val maxMark: String,
    val weightagePercent: Double,
    val scoredMark: String,
    val weightageMark: Double
)

data class CourseMark(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val details: List<MarkDetail>,
    val totalWeightageMark: Double,
    val totalWeightagePercent: Double
)

// --- GRADES ---
data class CourseGrade(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val grade: String
)

// --- GRADE HISTORY ---
data class GradeHistoryItem(
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val credits: String,
    val grade: String,
    val examMonth: String
)

data class CGPASummary(
    val creditsRegistered: String,
    val creditsEarned: String,
    val cgpa: String
)