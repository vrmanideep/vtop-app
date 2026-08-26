package com.vtop.network

import android.content.Context
import android.util.Log
import com.vtop.models.FacultyEntity
import com.vtop.models.FacultyOpenHour
import com.vtop.models.TimetableModel
import com.vtop.core.FacultyStorage
import com.vtop.telemetry.Telemetry
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// Now holds the rich metadata directly from the API JSON
private data class ApiFacultyData(val image: String?, val email: String?, val office: String?, val research: String?)
data class FacultyDetails(val email: String?, val office: String?, val research: String?, val openHours: List<FacultyOpenHour>)

object FacultyMemoryCache {
    val cache = ConcurrentHashMap<Int, FacultyDetails>()
}

object FacultyScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun cleanWords(s: String): List<String> {
        return s.lowercase()
            .replace(Regex("[^a-z\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in listOf("dr", "prof", "mr", "mrs", "ms") }
    }

    private fun levenshtein(a: String, b: String): Int {
        var cost = IntArray(a.length + 1) { it }
        var newCost = IntArray(a.length + 1) { 0 }
        for (i in 1..b.length) {
            newCost[0] = i
            for (j in 1..a.length) {
                val match = if (a[j - 1] == b[i - 1]) 0 else 1
                val replaceCost = cost[j - 1] + match
                val insertCost = cost[j] + 1
                val deleteCost = newCost[j - 1] + 1
                newCost[j] = minOf(insertCost, minOf(deleteCost, replaceCost))
            }
            val swap = cost; cost = newCost; newCost = swap
        }
        return cost[a.length]
    }

    // Unified Strict Matching Engine
    fun matchFaculty(query: String, facultyList: List<FacultyEntity>): FacultyEntity? {
        val srcWords = cleanWords(query)
        val srcSortedStr = srcWords.sorted().joinToString("")
        var bestMatch: FacultyEntity? = null
        var bestDistance = Int.MAX_VALUE

        for (faculty in facultyList) {
            val tgtWords = cleanWords(faculty.name)
            if (tgtWords.isEmpty()) continue

            val tgtSortedStr = tgtWords.sorted().joinToString("")

            if (srcSortedStr == tgtSortedStr) {
                return faculty
            }

            val commonWords = srcWords.intersect(tgtWords.toSet())
            val hasSignificantCommonWord = commonWords.any { it.length >= 4 }

            if (hasSignificantCommonWord && (commonWords.size == srcWords.size || commonWords.size == tgtWords.size)) {
                bestMatch = faculty
                bestDistance = 0
                continue
            }

            val dist = levenshtein(srcSortedStr, tgtSortedStr)
            val maxAllowed = if (srcSortedStr.length > 10) 2 else 1

            if (dist <= maxAllowed && dist < bestDistance && bestDistance != 0) {
                bestDistance = dist
                bestMatch = faculty
            }
        }
        return bestMatch
    }

    // Deprecated dummy function to prevent your background workers from crashing
    suspend fun syncRegisteredFacultyDetails(context: Context, vtopClient: VtopClient, timetable: TimetableModel) {
        // Automatically bypassed: The timetable now reads directly from the API JSON saved to disk!
    }

    // 1. Base Scrape: Pulls names from HTML and merges with full API JSON
    suspend fun download(vtopClient: VtopClient): List<FacultyEntity> = withContext(Dispatchers.IO) {
        Telemetry.log(TelemetryStatus.INFO, "Faculty", "Hybrid scrape started.", TelemetryModule.NETWORK)

        coroutineScope {
            val htmlDeferred = async { vtopClient.fetchFacultiesRawHtml() }
            val apiDeferred = async { fetchApiDataMap() }

            val html = htmlDeferred.await() ?: throw IOException("Failed to fetch HTML payload")
            val apiDataMap = try { apiDeferred.await() } catch (e: Exception) { emptyMap<String, ApiFacultyData>() }

            val allFaculty = mutableListOf<FacultyEntity>()
            val rows = Jsoup.parse(html).select("div#4a table tr")

            for (i in 1 until rows.size) {
                val cols = rows[i].select("td")
                if (cols.size >= 4) {
                    val name = cols[0].text().trim()

                    // Normalize name to map with the API
                    val normName = name.replace(Regex("(?i)\\b(dr|mr|ms|mrs|prof)\\b\\.?"), "")
                        .replace(Regex("[^a-zA-Z0-9 ]"), "").replace(Regex("\\s+"), " ").trim().lowercase()

                    val id = cols[3].select("button").attr("id").toIntOrNull() ?: continue
                    val apiData = apiDataMap[normName] ?: apiDataMap.entries.firstOrNull { it.key.contains(normName) || normName.contains(it.key) }?.value

                    allFaculty.add(
                        FacultyEntity(
                            id = id,
                            name = name,
                            designation = cols[1].text().trim().ifBlank { null },
                            department = cols[2].text().trim().ifBlank { null },
                            email = apiData?.email,
                            office = apiData?.office,
                            subDepartment = null,
                            research = apiData?.research,
                            image = apiData?.image,
                            openHours = null
                        )
                    )
                }
            }
            allFaculty
        }
    }

    // 2. Deep Scrape: Still used dynamically by the Faculty Directory screen for Open Hours
    suspend fun fetchDetails(vtopClient: VtopClient, empId: Int): FacultyDetails? = withContext(Dispatchers.IO) {
        if (FacultyMemoryCache.cache.containsKey(empId)) return@withContext FacultyMemoryCache.cache[empId]

        val html = vtopClient.fetchFacultyDetailsRawHtml(empId.toString()) ?: return@withContext null
        val doc = Jsoup.parse(html)

        var email: String? = null
        var office: String? = null
        var research: String? = null
        val openHours = mutableListOf<FacultyOpenHour>()

        val tables = doc.select("table")
        for (table in tables) {
            val text = table.text().lowercase().replace(Regex("\\s+"), " ")

            if (text.contains("e-mail") || text.contains("email") || text.contains("cabin")) {
                table.select("tr").forEach { row ->
                    val tds = row.select("td, th")
                    if (tds.size >= 2) {
                        val header = tds[0].text().lowercase().trim()
                        val value = tds[1].text().trim()
                        if (header.contains("e-mail") || header.contains("email")) email = value
                        else if (header.contains("cabin") || header.contains("office")) office = value
                        else if (header.contains("research")) research = value
                    }
                }
            }

            if (text.contains("open hours") || text.contains("weekday") || text.contains("hours")) {
                table.select("tr").forEach { row ->
                    val tds = row.select("td, th")
                    if (tds.size >= 2) {
                        val day = tds[0].text().trim()
                        val time = tds[1].text().trim()
                        if (day.isNotBlank() && time.isNotBlank() && !day.equals("Weekday", true) && !day.equals("Day", true) && !day.equals("Hours", true)) {
                            val obj = FacultyOpenHour(day, time)
                            if (!openHours.contains(obj)) openHours.add(obj)
                        }
                    }
                }
            }
        }

        val details = FacultyDetails(email, office, research, openHours)
        FacultyMemoryCache.cache[empId] = details
        details
    }

    private fun fetchApiDataMap(): Map<String, ApiFacultyData> {
        val apiMap = mutableMapOf<String, ApiFacultyData>()
        var currentPage = 1
        var totalPages = 1

        while (currentPage <= totalPages) {
            val url = ApiConstants.FACULTY_API.toHttpUrl().newBuilder()
                .addQueryParameter("populate[Photo][populate]", "*")
                .addQueryParameter("pagination[page]", currentPage.toString())
                .addQueryParameter("pagination[pageSize]", "100")
                .build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer ${ApiConstants.FACULTY_BEARER}").build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val root = JSONObject(response.body?.string() ?: return@use)
                totalPages = root.optJSONObject("meta")?.optJSONObject("pagination")?.optInt("pageCount", 1) ?: 1

                val data = root.optJSONArray("data") ?: return@use
                for (i in 0 until data.length()) {
                    val attrs = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue

                    val photoUrl = attrs.optJSONObject("Photo")?.optJSONObject("data")?.optJSONObject("attributes")?.optString("url")
                    val email = attrs.optString("EMAIL").ifBlank { null }
                    val office = attrs.optString("Office_Address").ifBlank { null }
                    val research = attrs.optString("Research_area_of_specialization").ifBlank { null }
                    val name = attrs.optString("Name", "")

                    if (name.isNotBlank()) {
                        // Normalize API name to match with HTML base scrape
                        val normName = name.replace(Regex("(?i)\\b(dr|mr|ms|mrs|prof)\\b\\.?"), "")
                            .replace(Regex("[^a-zA-Z0-9 ]"), "").replace(Regex("\\s+"), " ").trim().lowercase()

                        apiMap[normName] = ApiFacultyData(photoUrl, email, office, research)
                    }
                }
            }
            currentPage++
        }
        return apiMap
    }
}