package com.vtop.logic

import org.jsoup.Jsoup
import com.vtop.models.*
import android.util.Log

object MarksParser {

    // Helper to scrape the Semester Dropdown lists
    fun parseSemesters(html: String): List<SemesterOption> {
        val list = mutableListOf<SemesterOption>()
        try {
            val doc = Jsoup.parse(html)
            val options = doc.select("select#semesterSubId option")
            for (opt in options) {
                val value = opt.attr("value")
                if (value.isNotBlank()) {
                    list.add(SemesterOption(id = value, name = opt.text().trim()))
                }
            }
        } catch (e: Exception) { Log.e("MARKS_PARSER", "Error parsing semesters", e) }
        return list
    }

    fun parseMarks(html: String): List<CourseMark> {
        val list = mutableListOf<CourseMark>()
        try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.customTable > tbody > tr")

            var i = 1 // Skip header row
            while (i < rows.size) {
                val mainRow = rows[i]
                if (mainRow.hasClass("tableContent") && mainRow.select("td").size >= 9) {
                    val cells = mainRow.select("td")
                    val courseCode = cells[2].text().trim()
                    val courseTitle = cells[3].text().trim()
                    val courseType = cells[4].text().trim()

                    val details = mutableListOf<MarkDetail>()
                    var totalWeightageMark = 0.0
                    var totalWeightagePercent = 0.0

                    // The immediate next row contains the nested details table
                    if (i + 1 < rows.size) {
                        val detailRow = rows[i + 1]
                        val detailCells = detailRow.select("table.customTable-level1 tr.tableContent-level1")
                        for (dRow in detailCells) {
                            val dCols = dRow.select("td")
                            if (dCols.size >= 7) {
                                val weightagePercent = dCols[3].text().toDoubleOrNull() ?: 0.0
                                val weightageMark = dCols[6].text().toDoubleOrNull() ?: 0.0

                                details.add(
                                    MarkDetail(
                                        title = dCols[1].text().trim(),
                                        maxMark = dCols[2].text().trim(),
                                        weightagePercent = weightagePercent,
                                        scoredMark = dCols[5].text().trim(),
                                        weightageMark = weightageMark
                                    )
                                )
                                totalWeightagePercent += weightagePercent
                                totalWeightageMark += weightageMark
                            }
                        }
                        i++ // Skip the nested row for the next main loop iteration
                    }

                    list.add(CourseMark(courseCode, courseTitle, courseType, details, totalWeightageMark, totalWeightagePercent))
                }
                i++
            }
        } catch (e: Exception) { Log.e("MARKS_PARSER", "Error parsing marks", e) }
        return list
    }

    fun parseGrades(html: String): List<CourseGrade> {
        val list = mutableListOf<CourseGrade>()
        try {
            val doc = Jsoup.parse(html)
            val rows = doc.select("table.table-hover tr")
            for (row in rows) {
                val cells = row.select("td")
                if (cells.size >= 8) {
                    list.add(
                        CourseGrade(
                            courseCode = cells[1].text().trim(),
                            courseTitle = cells[2].text().trim(),
                            courseType = cells[3].text().trim(),
                            grade = cells[6].text().trim()
                        )
                    )
                }
            }
        } catch (e: Exception) { Log.e("MARKS_PARSER", "Error parsing grades", e) }
        return list
    }

    fun parseHistory(html: String): Pair<CGPASummary?, List<GradeHistoryItem>> {
        var summary: CGPASummary? = null
        val historyList = mutableListOf<GradeHistoryItem>()

        try {
            val doc = Jsoup.parse(html)

            // Extract CGPA Summary
            val cgpaRow = doc.select("div.box-body.table-responsive tbody tr").first()
            if (cgpaRow != null) {
                val cells = cgpaRow.select("td")
                if (cells.size >= 3) {
                    summary = CGPASummary(
                        creditsRegistered = cells[0].text().trim(),
                        creditsEarned = cells[1].text().trim(),
                        cgpa = cells[2].text().trim()
                    )
                }
            }

            // Extract History
            val rows = doc.select("table.customTable tr.tableContent")
            for (row in rows) {
                // Ignore nested detail rows by checking column count
                val cells = row.select("td")
                if (cells.size == 10) {
                    historyList.add(
                        GradeHistoryItem(
                            courseCode = cells[1].text().trim(),
                            courseTitle = cells[2].text().trim(),
                            courseType = cells[3].text().trim(),
                            credits = cells[4].text().trim(),
                            grade = cells[5].text().trim(),
                            examMonth = cells[6].text().trim()
                        )
                    )
                }
            }
        } catch (e: Exception) { Log.e("MARKS_PARSER", "Error parsing history", e) }
        return Pair(summary, historyList)
    }
}