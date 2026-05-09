package com.vtop.models

import androidx.annotation.Keep

@Keep
data class FacultyModel(
    val id: Int = 0,
    val name: String = "",
    val designation: String? = null,
    val email: String? = null,
    val office: String? = null,
    val department: String? = null,
    val sub_department: String? = null,
    val research: String? = null
)