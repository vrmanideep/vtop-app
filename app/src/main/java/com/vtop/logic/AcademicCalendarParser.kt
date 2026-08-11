package com.vtop.logic

import com.vtop.models.SemesterOption
import java.util.Locale
import java.util.regex.Pattern

object AcademicCalendarParser {

    fun parseSemesters(html: String?): List<SemesterOption> {
        val list = mutableListOf<SemesterOption>()
        if (html.isNullOrBlank()) return list

        val m = Pattern.compile("<option\\s+value=\"([^\"]+)\"[^>]*>([^<]+)</option>").matcher(html)
        while (m.find()) {
            val id = m.group(1) ?: continue
            val name = m.group(2) ?: continue

            if (!name.lowercase(Locale.getDefault()).contains("choose") && id.trim().isNotEmpty() && id.trim() != "COMB") {
                val cleanName = name.trim().replace(Regex("(?i)\\s*-\\s*AMR$"), "")
                list.add(SemesterOption(id.trim(), cleanName))
            }
        }
        return list
    }

    fun parseMonths(html: String?): List<String> {
        val months = mutableListOf<String>()
        if (html.isNullOrBlank()) return months

        val matcher = Pattern.compile("processViewCalendar\\(&#39;([A-Z0-9\\-]+)&#39;\\)").matcher(html)
        while (matcher.find()) {
            val month = matcher.group(1)
            if (month != null) {
                months.add(month)
            }
        }
        return months
    }
}