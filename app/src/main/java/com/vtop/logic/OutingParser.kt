package com.vtop.logic

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

                    val idMatch = Regex("[A-Z]\\d{8,15}").find(rowHtml)
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

            val rows = table.select("tr")
            for (i in 1 until rows.size) {
                val cells = rows[i].select("td")

                if (cells.size >= 11) {
                    val place = cells[4].text().trim()
                    val purpose = cells[5].text().trim()
                    val timeStr = cells[6].text().trim()
                    val dateStr = cells[7].text().trim().split(" ").firstOrNull() ?: ""
                    val status = cells[9].text().trim()

                    var canDownload = false
                    var leaveId = ""
                    val rowHtml = rows[i].outerHtml()

                    // Try regex first (e.g., W23674651358)
                    val idMatch = Regex("[A-Z]\\d{8,15}").find(rowHtml)
                    if (idMatch != null) {
                        leaveId = idMatch.value
                    }

                    // THE FIX: Weekend uses 'data-leave-url', not 'data-url'
                    val downloadLink = cells[10].selectFirst("a[data-leave-url]")
                    if (downloadLink != null && downloadLink.attr("data-leave-url").isNotEmpty()) {
                        canDownload = true

                        // If Regex failed, fallback to extracting ID from the download URL
                        if (leaveId.isEmpty()) {
                            leaveId = downloadLink.attr("data-leave-url").split("/").last()
                        }
                    }

                    if (leaveId.isEmpty()) leaveId = "WKND_${System.currentTimeMillis()}_$i"

                    val fromTime = timeStr.substringBefore("-").trim()
                    val toTime = timeStr.substringAfter("-").trim()

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
            }
        } catch (e: Exception) {
            e.printStackTrace()
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