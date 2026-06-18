package com.vtop.logic

import com.vtop.models.AcademicCalendarEvent
import org.jsoup.Jsoup

object CalendarParser {

    fun parseCalendarHtml(html: String): List<AcademicCalendarEvent> {
        val parsedEvents = mutableListOf<AcademicCalendarEvent>()
        if (html.isBlank()) return parsedEvents

        try {
            val doc = Jsoup.parse(html)

            // 1. Grab the Month and Year from the header (e.g., "MAY 2026")
            val h4 = doc.selectFirst("h4") ?: return parsedEvents
            // Format it as "-MAY-2026" to append to the day number later
            val monthYear = h4.text().trim().replace(" ", "-")

            // 2. VTOP maps columns 0-6 directly to Sunday-Saturday
            val daysOfWeek = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

            val table = doc.selectFirst("table.calendar-table") ?: return parsedEvents
            val rows = table.select("tr")

            for (row in rows) {
                // Skip the header row containing the <th> tags
                if (row.select("th").isNotEmpty()) continue

                val cells = row.select("td")
                for ((colIdx, cell) in cells.withIndex()) {
                    if (colIdx >= daysOfWeek.size) break

                    val spans = cell.select("span")
                    if (spans.isEmpty()) continue

                    // The first <span> in a valid cell always contains the date number (e.g., "24")
                    val dayNumText = spans[0].text().trim()

                    // Ignore empty cells or cells without a valid number
                    if (dayNumText.isEmpty() || !dayNumText.all { it.isDigit() }) continue

                    val dayNum = dayNumText.padStart(2, '0')
                    val fullDate = "$dayNum-$monthYear"

                    // All remaining spans in that cell contain the actual event details
                    val eventTexts = mutableListOf<String>()
                    for (i in 1 until spans.size) {
                        val text = spans[i].text().trim()
                        if (text.isNotEmpty()) {
                            eventTexts.add(text)
                        }
                    }

                    // If there are events on this day, join them and add to our list
                    if (eventTexts.isNotEmpty()) {
                        val particulars = eventTexts.joinToString(" ")
                        parsedEvents.add(
                            AcademicCalendarEvent(
                                date = fullDate,
                                day = daysOfWeek[colIdx],
                                particulars = particulars
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return parsedEvents
    }
}