package com.vtop.ui.core

import androidx.compose.ui.graphics.Color
import com.vtop.ui.theme.CoursePalette
import kotlin.math.abs

object CourseColorManager {
    /**
     * Generates a consistent color based on the course code and type.
     * e.g., "CSE1005_THEORY" will ALWAYS return the exact same color.
     */
    fun getColorForCourse(courseCode: String, courseType: String): Color {
        val uniqueString = "${courseCode.trim().uppercase()}_${courseType.trim().uppercase()}"
        val hash = abs(uniqueString.hashCode())
        val colorIndex = hash % CoursePalette.size
        return CoursePalette[colorIndex]
    }

    /**
     * Use this function inside your WidgetWorker or NextClassWidget.
     * RemoteViews require standard Android Color Ints, not Compose Colors.
     */
    fun getWidgetColorIntForCourse(courseCode: String, courseType: String): Int {
        val color = getColorForCourse(courseCode, courseType)
        return android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }
}