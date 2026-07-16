package com.vtop.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.vtop.network.VtopClient
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object TTExport {

    suspend fun exportCurrentSemesterTimetable(
        context: Context,
        client: VtopClient
    ): Result<Uri> {

        return withContext(Dispatchers.IO) {

            try {

                Log.d(
                    "TT_EXPORT",
                    """
                    client=$client
                    auth=${client.authorizedId}
                    csrf=${client.csrfToken}
                    """.trimIndent()
                )

                val currentSemesterName =
                    Vault.getSelectedSemester(context)[1]
                        ?: error("Semester unavailable")

                val semesterOptions =
                    Vault.getSemesterOptions(context)

                val semesterSubId =
                    semesterOptions.firstOrNull {
                        it.name == currentSemesterName
                    }?.id
                        ?: error("Semester ID unavailable")

                Log.d(
                    "TT_EXPORT",
                    "Semester ID = $semesterSubId"
                )

                val fragment =
                    client.fetchTimetableRawHtml(
                        semesterSubId,
                        null
                    ) ?: error("Failed fetching timetable")

                Log.d(
                    "TT_EXPORT",
                    "Fragment length = ${fragment.length}"
                )

                val html =
                    buildExportHtml(fragment)

                Log.d(
                    "TT_EXPORT",
                    "Sending HTML to CloudConvert"
                )

                H2P.htmlToPng(
                    context,
                    html
                )

            } catch (e: Exception) {

                Log.e(
                    "TT_EXPORT",
                    "Export failed",
                    e
                )

                Result.failure(e)
            }
        }
    }

    // =========================================================
    private fun buildExportHtml(
        fragment: String
    ): String {

        val document =
            Jsoup.parse(fragment)

        document.select("script").remove()
        document.select("nav").remove()
        document.select(".navbar").remove()
        document.select(".sidebar").remove()
        document.select(".footer").remove()

        val timetableContainer =
            document.getElementById("studentDetailsList")
                ?: document.body()

        val contentHtml =
            timetableContainer.outerHtml()

        return """
        <!DOCTYPE html>

        <html>

        <head>

            <meta charset="utf-8"/>

            <meta
                name="viewport"
                content="width=1920, initial-scale=1.0"
            />

            <style>

                * {
                    box-sizing: border-box;
                }

                html,
                body {

                    margin: 0;
                    padding: 10px;

                    background: white !important;

                    width: 1920px !important;
                    min-width: 1920px !important;

                    overflow: hidden !important;

                    font-family: Arial, sans-serif;
                }

                body {

                    display: inline-block;
                }

                body,
                #studentDetailsList {

                    height: auto !important;
                }

                #studentDetailsList {

                    width: 100% !important;

                    overflow: visible !important;
                }

                .table-responsive {

                    width: 100% !important;

                    overflow: visible !important;
                }

                table {

                    width: 100% !important;

                    border-collapse: collapse !important;

                    background: white !important;

                    margin-bottom: 12px !important;
                }

                td,
                th {

                    border: 1px solid #3c8dbc !important;

                    text-align: center !important;

                    vertical-align: middle !important;

                    color: black !important;

                    word-break: break-word !important;
                }

                th {

                    background: #3c8dbc !important;

                    color: white !important;

                    font-weight: bold !important;
                }

                /* hide dropdown */

                select {

                    display: none !important;
                }

                /* registration table */

                table:not(#timeTableStyle) {

                    table-layout: auto !important;

                    width: 100% !important;

                    margin-bottom: 8px !important;
                }

                table:not(#timeTableStyle) td,
                table:not(#timeTableStyle) th {

                    font-size: 11px !important;

                    padding: 3px !important;

                    line-height: 1.1 !important;

                    white-space: normal !important;
                }

                /* timetable */

                #timeTableStyle {

                    width: 100% !important;

                    table-layout: fixed !important;

                    border: 2px solid #3c8dbc !important;
                }

                #timeTableStyle td,
                #timeTableStyle th {

                    width: 4.3% !important;

                    font-size: 11px !important;

                    padding: 3px !important;

                    line-height: 1.25 !important;

                    overflow-wrap: break-word !important;
                }

                p {

                    margin: 0 !important;
                }

                ul {

                    margin-top: 4px !important;

                    margin-bottom: 10px !important;
                }

                li {

                    font-size: 12px !important;
                }

            </style>

        </head>

        <body>

            $contentHtml

        </body>

        </html>
    """.trimIndent()
    }
}