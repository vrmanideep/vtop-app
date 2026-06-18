package com.vtop.services

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object H2P {



    // INCREASED TIMEOUTS: Render free tier takes 50+ seconds to wake up from sleep.
    private val client = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private const val SERVER_URL =
        "https://html-to-png-pqzs.onrender.com"

    suspend fun htmlToPng(
        context: Context,
        html: String
    ): Result<Uri> {

        return try {

            Log.d(
                "TT_EXPORT",
                "Creating temporary HTML"
            )

            val htmlFile =
                File(
                    context.cacheDir,
                    "index.html"
                )

            htmlFile.writeText(html)

            Log.d(
                "TT_EXPORT",
                "Uploading to Gotenberg"
            )

            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)

                    .addFormDataPart(
                        "files",
                        "index.html",
                        htmlFile.asRequestBody(
                            "text/html".toMediaType()
                        )
                    )

                    .addFormDataPart(
                        "width",
                        "1920"
                    )

                    .addFormDataPart(
                        "height",
                        "1080"
                    )

                    .addFormDataPart(
                        "printBackground",
                        "true"
                    )

                    .build()

            val request =
                Request.Builder()
                    .url(
                        "$SERVER_URL/forms/chromium/screenshot/html"
                    )
                    .post(body)
                    .build()

            val response =
                client
                    .newCall(request)
                    .execute()

            if (!response.isSuccessful) {

                Log.e(
                    "TT_EXPORT",
                    response.body?.string()
                        ?: "No error body"
                )

                error(
                    "Server error ${response.code}"
                )
            }

            val bytes =
                response.body?.bytes()
                    ?: error("PNG bytes missing")

            Log.d(
                "TT_EXPORT",
                "Received PNG bytes"
            )

            val resolver =
                context.contentResolver

            val values =
                ContentValues().apply {

                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        "timetable.png"
                    )

                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        "image/png"
                    )

                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                    )
                }

            val collection =
                if (android.os.Build.VERSION.SDK_INT >= 29) {

                    MediaStore.Downloads.EXTERNAL_CONTENT_URI

                } else {

                    MediaStore.Files.getContentUri("external")
                }

            val uri =
                resolver.insert(
                    collection,
                    values
                ) ?: error(
                    "Failed creating MediaStore entry"
                )

            resolver.openOutputStream(uri)?.use {

                it.write(bytes)
            }

            Log.d(
                "TT_EXPORT",
                "PNG saved successfully"
            )

            Result.success(uri)

        } catch (e: Exception) {

            Log.e(
                "TT_EXPORT",
                "Gotenberg export failed",
                e
            )

            Result.failure(e)
        }
    }
}