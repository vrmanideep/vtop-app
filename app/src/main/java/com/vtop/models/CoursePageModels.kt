package com.vtop.models

data class CoursePageOption(
    val classId: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String
)

data class GroupedCourseOption(
    val courseCode: String,
    val courseTitle: String,
    val types: List<CoursePageOption>
)

data class CourseSection(
    val classGroup: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val classId: String,
    val slot: String,
    val erpId: String,
    val facultyName: String,
    val school: String
)

data class CourseMaterialItem(
    val label: String,
    val relativePath: String,
    val fileNameHint: String = "",
    val isCoursePlan: Boolean = false,
    val isWebLink: Boolean = false,
    val isZip: Boolean = false
)

data class CourseLectureItem(
    val slNo: String,
    val date: String,
    val day: String,
    val topic: String,
    val materials: List<CourseMaterialItem>
)

data class CourseDetailData(
    val classGroup: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val classId: String,
    val semSubId: String,
    val slot: String,
    val facultyName: String,
    val allMaterialsPath: String?,
    val generalMaterialsPath: String?,
    val syllabusPath: String?,
    val generalMaterials: List<CourseMaterialItem>,
    val lectures: List<CourseLectureItem>
)