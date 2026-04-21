package com.vtop.logic

import org.jsoup.Jsoup
import com.vtop.models.ExamScheduleModel
import android.util.Log

object ExamScheduleParser {
    fun parse(html: String): List<ExamScheduleModel> {
        val exams = mutableListOf<ExamScheduleModel>()
        try {
            val document = Jsoup.parse(html)
            val rows = document.select("table.customTable tr.tableContent")
            var currentExamType = "Unknown"

            for (row in rows) {
                val cells = row.select("td")

                if (cells.size == 1 && cells[0].hasClass("panelHead-secondary")) {
                    currentExamType = cells[0].text().trim()
                    continue
                }

                if (cells.size >= 13) {
                    // Mapping all 13 arguments required by the new Model
                    exams.add(
                        ExamScheduleModel(
                            courseCode = cells[1].text().trim(),
                            courseTitle = cells[2].text().trim(),
                            courseType = cells[3].text().trim(),
                            classId = cells[4].text().trim(),
                            slot = cells[5].text().trim(),
                            examDate = cells[6].text().trim(),
                            examSession = cells[7].text().trim(),
                            reportingTime = cells[8].text().trim(),
                            examTime = cells[9].text().trim(),
                            venue = cells[10].text().trim(),
                            seatLocation = cells[11].text().trim(),
                            seatNumber = cells[12].text().trim(),
                            examType = currentExamType
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("EXAM_PARSER", "Error parsing exam HTML", e)
        }
        return exams
    }
}