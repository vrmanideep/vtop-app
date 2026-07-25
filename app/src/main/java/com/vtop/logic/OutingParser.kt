package com.vtop.logic

import android.util.Log
import com.vtop.models.OutingModel
import org.jsoup.Jsoup

object OutingParser {

    fun parseGeneral(html: String): List<OutingModel> {
        val records = mutableListOf<OutingModel>()
        try {
            val doc = Jsoup.parse(html)
            val table = doc.selectFirst("table#BookingRequests") ?: return records

            val rows = table.select("tr")
            for (i in 1 until rows.size) {
                val cells = rows[i].select("td")

                if (cells.size >= 11) {
                    val place = cells[2].text().trim()
                    val purpose = cells[3].text().trim()
                    val fromDate = cells[4].text().trim().split(" ").firstOrNull() ?: ""
                    val fromTime = cells[5].text().trim()
                    val toDate = cells[6].text().trim().split(" ").firstOrNull() ?: ""
                    val toTime = cells[7].text().trim()
                    val status = cells[9].text().trim()

                    var canDownload = false
                    var leaveId = ""
                    val rowHtml = rows[i].outerHtml()

                    val idMatch = Regex("[A-Z]\\d{6,15}").find(rowHtml)
                    if (idMatch != null) {
                        leaveId = idMatch.value
                    }

                    val downloadLink = cells[10].selectFirst("a[data-url]")
                    if (downloadLink != null && downloadLink.attr("data-url").isNotEmpty()) {
                        canDownload = true
                    }

                    if (leaveId.isEmpty()) {
                        leaveId = "GEN_${System.currentTimeMillis()}_$i"
                    }

                    records.add(OutingModel(id = leaveId, type = "GENERAL", place = place, purpose = purpose, fromDate = fromDate, fromTime = fromTime, toDate = toDate, toTime = toTime, status = status, canDownload = canDownload))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return records
    }

    fun parseWeekend(html: String): List<OutingModel> {
        val records = mutableListOf<OutingModel>()
        try {
            val doc = Jsoup.parse(html)
            val table = doc.selectFirst("table#BookingRequests") ?: return records
            val rows = table.select("tbody > tr")

            Log.d("WKND_OUTING_ROWS", "total rows = ${rows.size}")

            rows.forEachIndexed { index, row ->
                val cells = row.select("td")
                Log.d("WKND_OUTING_CELLS", "row=$index cells=${cells.size}")

                if (cells.size < 11) return@forEachIndexed

                // Step 1 — Detect table format
                val isWeekendFormat = cells.size >= 14

                // Step 2 — Compute indices instead of hardcoding them
                val contactIndex: Int
                val parentContactIndex: Int
                val dateIndex: Int
                val bookingIdIndex: Int
                val statusIndex: Int
                val downloadIndex: Int

                if (isWeekendFormat) {
                    contactIndex = 7
                    parentContactIndex = 8
                    dateIndex = 9
                    bookingIdIndex = 10
                    statusIndex = 12
                    downloadIndex = 13
                } else {
                    contactIndex = -1
                    parentContactIndex = -1
                    dateIndex = 7
                    bookingIdIndex = -1
                    statusIndex = 9
                    downloadIndex = 10
                }

                fun text(idx: Int): String {
                    return if (idx in 0 until cells.size) cells[idx].text().trim() else ""
                }

                val place = text(4)
                val purpose = text(5)
                val timeStr = text(6)

                // Step 3 — Contact numbers
                val contactNumber = if (isWeekendFormat) text(contactIndex) else ""
                val parentContactNumber = if (isWeekendFormat) text(parentContactIndex) else ""

                // Step 4 — Date
                val dateStr = text(dateIndex).split(" ").firstOrNull() ?: ""

                // Step 5 — Status
                var status = text(statusIndex)

                // Step 6 & 7 — Booking ID & Download link
                var bookingId = ""
                val downloadLink = cells[downloadIndex].selectFirst("a[data-leave-url]")

                if (isWeekendFormat) {
                    val bookingIdText = text(bookingIdIndex)
                    if (bookingIdText.isNotBlank()) {
                        bookingId = bookingIdText
                    } else if (downloadLink != null) {
                        val dataUrl = downloadLink.attr("data-leave-url")
                        bookingId = dataUrl.split("/").lastOrNull() ?: ""
                    }
                } else {
                    if (downloadLink != null) {
                        val dataUrl = downloadLink.attr("data-leave-url")
                        bookingId = dataUrl.split("/").lastOrNull() ?: ""
                    }
                }

                // Step 8 — canDownload
                val canDownload = bookingId.isNotBlank() && status.equals("Outing Request Accepted", ignoreCase = true)

                if (status.contains("Accepted", ignoreCase = true)) {
                    status = "Approved"
                }

                val leaveId = if (bookingId.isNotBlank()) bookingId else "WKND_${System.currentTimeMillis()}_$index"

                val fromTime = timeStr.substringBefore("-").trim()
                val toTime = timeStr.substringAfter("-").trim()

                // Step 9 & 10 — Record construction (Preserving existing model definition)
                records.add(
                    OutingModel(
                        id = leaveId,
                        type = "WEEKEND",
                        place = place,
                        purpose = purpose,
                        fromDate = dateStr,
                        fromTime = fromTime,
                        toDate = dateStr,
                        toTime = toTime,
                        status = status,
                        canDownload = canDownload
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("WKND_OUTING_PARSE", "Failed to parse weekend outings", e)
        }

        return records
    }

    fun parsePrefilledFormData(html: String?): Map<String, String>? {
        if (html.isNullOrEmpty()) return null
        try {
            val doc = Jsoup.parse(html)
            val appNo = doc.select("input#applicationNo").attr("value")

            if (appNo.isNullOrEmpty()) return null

            return mapOf(
                "name" to doc.select("input#name").attr("value"),
                "regNo" to doc.select("input#regNo").attr("value"),
                "appNo" to appNo,
                "gender" to doc.select("input#gender").attr("value"),
                "block" to doc.select("input#hostelBlock").attr("value"),
                "room" to doc.select("input#roomNo").attr("value")
            )
        } catch (e: Exception) { return null }
    }

    fun parseWeekendFormData(html: String?): Map<String, String>? {
        if (html.isNullOrEmpty()) return null
        try {
            val doc = Jsoup.parse(html)
            // Trap VTOP's native error (e.g., trying to apply outside the Tue-Sat window)
            val jsonBom = doc.select("input#jsonBom").attr("value")
            if (jsonBom.isNotEmpty()) return mapOf("error" to jsonBom)

            val appNo = doc.select("input#applicationNo").attr("value")
            if (appNo.isNullOrEmpty()) return null

            return mapOf(
                "name" to doc.select("input#name").attr("value"),
                "regNo" to doc.select("input#regNo").attr("value"),
                "appNo" to appNo,
                "gender" to doc.select("input#gender").attr("value"),
                "block" to doc.select("input#hostelBlock").attr("value"),
                "room" to doc.select("input#roomNo").attr("value"),
                "parentContact" to doc.select("input#parentContactNumber").attr("value")
            )
        } catch (e: Exception) { return null }
    }
}