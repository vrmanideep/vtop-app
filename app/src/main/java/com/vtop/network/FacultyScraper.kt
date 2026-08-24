package com.vtop.network

import com.vtop.models.FacultyEntity
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

// Lightweight containers for caches
private data class ApiFacultyData(val image: String?)
data class FacultyDetails(val email: String?, val office: String?, val research: String?, val openHours: List<Pair<String, String>>)

object FacultyMemoryCache {
    val cache = ConcurrentHashMap<Int, FacultyDetails>()
}

object FacultyScraper {

    private val client = OkHttpClient()

    private fun normalizeName(name: String): String = name
        .replace(Regex("(?i)\\b(dr|mr|ms|mrs|prof)\\b\\.?"), "")
        .replace(Regex("[^a-zA-Z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim().lowercase()

    // 1. Base Scrape: Saves to Disk
    suspend fun download(vtopClient: VtopClient): List<FacultyEntity> = withContext(Dispatchers.IO) {
        Telemetry.log(TelemetryStatus.INFO, "Faculty", "Hybrid scrape started.", TelemetryModule.NETWORK)

        coroutineScope {
            val htmlDeferred = async { vtopClient.fetchFacultiesRawHtml() }
            val apiDeferred = async { fetchApiDataMap() }

            val html = htmlDeferred.await() ?: throw IOException("Failed to fetch HTML payload")
            val apiDataMap = apiDeferred.await()

            val allFaculty = mutableListOf<FacultyEntity>()
            val rows = Jsoup.parse(html).select("div#4a table tr")

            for (i in 1 until rows.size) {
                val cols = rows[i].select("td")
                if (cols.size >= 4) {
                    val name = cols[0].text().trim()
                    val normName = normalizeName(name)
                    val id = cols[3].select("button").attr("id").toIntOrNull() ?: continue
                    val apiData = apiDataMap[normName] ?: apiDataMap.entries.firstOrNull { it.key.contains(normName) || normName.contains(it.key) }?.value

                    allFaculty.add(
                        FacultyEntity(
                            id = id,
                            name = name,
                            designation = cols[1].text().trim().ifBlank { null },
                            department = cols[2].text().trim().ifBlank { null },
                            email = null,
                            office = null,
                            subDepartment = null,
                            research = null,
                            image = apiData?.image
                        )
                    )
                }
            }
            Telemetry.log(TelemetryStatus.SUCCESS, "Faculty", "Merged ${allFaculty.size} faculty profiles.", TelemetryModule.NETWORK)
            allFaculty
        }
    }

    // 2. Deep Scrape: Saves to Volatile Memory
    suspend fun fetchDetails(vtopClient: VtopClient, empId: Int): FacultyDetails? = withContext(Dispatchers.IO) {
        if (FacultyMemoryCache.cache.containsKey(empId)) return@withContext FacultyMemoryCache.cache[empId]

        val html = vtopClient.fetchFacultyDetailsRawHtml(empId.toString()) ?: return@withContext null
        val doc = Jsoup.parse(html)

        var email: String? = null
        var office: String? = null
        var research: String? = null
        val openHours = mutableListOf<Pair<String, String>>()

        val tables = doc.select("table.table-bordered")
        if (tables.isNotEmpty()) {
            tables[0].select("tr").forEach { row ->
                val tds = row.select("td")
                if (tds.size >= 2) {
                    val header = tds[0].text().lowercase().trim()
                    val value = tds[1].text().trim()
                    if (header.contains("e-mail") || header.contains("email")) email = value
                    else if (header.contains("cabin") || header.contains("office")) office = value
                    else if (header.contains("research")) research = value
                }
            }

            // Extract the Open Hours block
            if (tables.size > 1) {
                val hoursTable = tables[1]
                if (hoursTable.text().contains("OPEN HOURS", ignoreCase = true)) {
                    hoursTable.select("tbody tr").forEach { row ->
                        val tds = row.select("td")
                        if (tds.size >= 2) {
                            val day = tds[0].text().trim()
                            val time = tds[1].text().trim()
                            if (day.isNotBlank() && time.isNotBlank()) {
                                openHours.add(Pair(day, time))
                            }
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
            val url = ApiConstants.FACULTY_API.toHttpUrl().newBuilder().addQueryParameter("populate[Photo][populate]", "*").addQueryParameter("pagination[page]", currentPage.toString()).addQueryParameter("pagination[pageSize]", "100").build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer ${ApiConstants.FACULTY_BEARER}").build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use
                val root = JSONObject(response.body?.string() ?: return@use)
                totalPages = root.optJSONObject("meta")?.optJSONObject("pagination")?.optInt("pageCount", 1) ?: 1

                val data = root.optJSONArray("data") ?: return@use
                for (i in 0 until data.length()) {
                    val attrs = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
                    val photoUrl = attrs.optJSONObject("Photo")?.optJSONObject("data")?.optJSONObject("attributes")?.optString("url")
                    val name = attrs.optString("Name", "")

                    if (name.isNotBlank()) {
                        apiMap[normalizeName(name)] = ApiFacultyData(photoUrl)
                    }
                }
            }
            currentPage++
        }
        return apiMap
    }
}