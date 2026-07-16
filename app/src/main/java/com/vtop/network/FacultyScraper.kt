package com.vtop.network
import com.vtop.telemetry.Telemetry

import com.google.gson.Gson
import com.vtop.models.FacultyEntity
import com.vtop.telemetry.model.TelemetryModule
import com.vtop.telemetry.model.TelemetryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object FacultyScraper {

    private val client = OkHttpClient()

    suspend fun download(): List<FacultyEntity> =
        withContext(Dispatchers.IO) {
            Telemetry.log(
                level = TelemetryStatus.INFO,
                tag = "Faculty",
                message = "Faculty scrape started.",
                module = TelemetryModule.NETWORK
            )

            val allFaculty = mutableListOf<FacultyEntity>()

            var currentPage = 1
            var totalPages = 1

            while (currentPage <= totalPages) {

                val root = fetchPage(currentPage)
                Telemetry.log(
                    level = TelemetryStatus.INFO,
                    tag = "Faculty",
                    message = "Fetching faculty page $currentPage.",
                    module = TelemetryModule.NETWORK
                )

                val meta = root.getJSONObject("meta")
                val pagination = meta.getJSONObject("pagination")

                totalPages = pagination.getInt("pageCount")
                Telemetry.log(
                    level = TelemetryStatus.INFO,
                    tag = "Faculty",
                    message = "Detected $totalPages page(s).",
                    module = TelemetryModule.NETWORK
                )

                val data = root.getJSONArray("data")

                for (i in 0 until data.length()) {

                    val item = data.getJSONObject(i)
                    val attrs = item.getJSONObject("attributes")

                    var image: String? = null

                    if (!attrs.isNull("Photo")) {

                        val photo = attrs.optJSONObject("Photo")

                        val photoData = photo
                            ?.optJSONObject("data")

                        val photoAttrs = photoData
                            ?.optJSONObject("attributes")

                        image = photoAttrs?.optString("url")
                    }

                    allFaculty += FacultyEntity(
                        id = item.optInt("id"),
                        name = attrs.optString("Name"),
                        designation = attrs.optString("Designation")
                            .ifBlank { null },
                        email = attrs.optString("EMAIL")
                            .ifBlank { null },
                        office = attrs.optString("Office_Address")
                            .ifBlank { null },
                        department = attrs.optString("Department")
                            .ifBlank { null },
                        subDepartment = attrs.optString("sub_department")
                            .ifBlank { null },
                        research = attrs.optString("Research_area_of_specialization")
                            .ifBlank { null },
                        image = image
                    )
                }

                currentPage++
                Telemetry.log(
                    level = TelemetryStatus.INFO,
                    tag = "Faculty",
                    message = "Parsed page $currentPage. Total faculty: ${allFaculty.size}",
                    module = TelemetryModule.PARSER
                )

                if (currentPage <= totalPages) {
                    delay(1500)
                }
            }
            Telemetry.log(
                level = TelemetryStatus.SUCCESS,
                tag = "Faculty",
                message = "Downloaded ${allFaculty.size} faculty profiles.",
                module = TelemetryModule.NETWORK
            )

            allFaculty
        }

    private fun fetchPage(
        page: Int
    ): JSONObject {

        val url = ApiConstants.FACULTY_API
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("populate[Patents][populate]", "*")
            .addQueryParameter("populate[Awards_and_Recognitions][populate]", "*")
            .addQueryParameter("populate[Projects][populate]", "*")
            .addQueryParameter("populate[Photo][populate]", "*")
            .addQueryParameter("pagination[page]", page.toString())
            .addQueryParameter("pagination[pageSize]", "100")
            .build()

        val request = Request.Builder()
            .url(url)
            .header(
                "Authorization",
                "Bearer ${ApiConstants.FACULTY_BEARER}"
            )
            .build()

        client.newCall(request).execute().use { response ->

            if (!response.isSuccessful) {
                throw IOException(
                    "Faculty API failed (${response.code})"
                )
            }

            val body = response.body?.string()
                ?: throw IOException("Empty response")

            return JSONObject(body)
        }

    }

}
