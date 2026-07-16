package com.vtop.models

data class FacultyEntity(
    val id: Int,
    val name: String,
    val designation: String?,
    val email: String?,
    val office: String?,
    val department: String?,
    val subDepartment: String?,
    val research: String?,
    val image: String?
)