package com.vtop.utils

import android.content.Context
import com.vtop.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object OtaManager {
    private const val TAG = "OTA_MANAGER"

    // Ensure these are the RAW github links to your files
    private const val FACULTY_URL = "https://raw.githubusercontent.com/vrmanideep/vtop-app/main/app/src/main/assets/faculty.json"
    private const val CALENDAR_URL = "https://raw.githubusercontent.com/vrmanideep/vtop-app/main/app/src/main/assets/academic_calendar.json"

    /**
     * Reads the faculty JSON. Prioritizes the downloaded internal storage file.
     * Falls back to the packaged assets file if the OTA file doesn't exist yet.
     */
    // ... keep your TAG and URL constants at the top ...

    /**
     * Checks if the downloaded OTA file is actually newer than the app itself.
     * If you just hit "Run" in Android Studio, the app update time will be newer,
     * so it deletes the stale OTA file and forces the app to use your fresh assets!
     */
    private fun getValidOtaFile(context: Context, fileName: String): File? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null

        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val appUpdateTime = packageInfo.lastUpdateTime

            if (file.lastModified() < appUpdateTime) {
                // The APK was updated AFTER this file was downloaded! The asset is fresher.
                Log.d(TAG, "Stale OTA file detected for $fileName. Wiping to use fresh APK assets.")
                file.delete()

                // Clear the ETag so the OTA manager knows to do a fresh check next time
                context.getSharedPreferences("OTA_PREFS", Context.MODE_PRIVATE)
                    .edit()
                    .remove("${fileName}_etag")
                    .apply()
                null
            } else {
                file // File is newer than the APK install time, it's safe to use
            }
        } catch (e: Exception) {
            file // Fallback
        }
    }

    fun getFacultyJson(context: Context): String {
        // KILLSWITCH: Always force local assets when testing in Android Studio
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG MODE: Forcing local assets/faculty.json")
            return try { context.assets.open("faculty.json").bufferedReader().use { it.readText() } } catch (e: Exception) { "[]" }
        }

        val file = getValidOtaFile(context, "faculty.json")
        return try {
            if (file != null) file.readText() else context.assets.open("faculty.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "[]" }
    }

    fun getCalendarJson(context: Context): String {
        // KILLSWITCH: Always force local assets when testing in Android Studio
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG MODE: Forcing local assets/academic_calendar.json")
            return try { context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() } } catch (e: Exception) { "{}" }
        }

        val file = getValidOtaFile(context, "academic_calendar.json")
        return try {
            if (file != null) file.readText() else context.assets.open("academic_calendar.json").bufferedReader().use { it.readText() }
        } catch (e: Exception) { "{}" }
    }

    // ... keep your checkForOtaUpdates and downloadIfModified functions exactly the same ...

    /**
     * Call this asynchronously on app launch to silently update data files.
     */
    suspend fun checkForOtaUpdates(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Checking for OTA JSON updates...")
        downloadIfModified(context, FACULTY_URL, "faculty.json")
        downloadIfModified(context, CALENDAR_URL, "academic_calendar.json")
    }

    private fun downloadIfModified(context: Context, urlString: String, fileName: String) {
        try {
            val prefs = context.getSharedPreferences("OTA_PREFS", Context.MODE_PRIVATE)
            val etagKey = "${fileName}_etag"
            val savedEtag = prefs.getString(etagKey, "")

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            // If we have an ETag from a previous download, ask GitHub if it has changed
            if (!savedEtag.isNullOrEmpty()) {
                connection.setRequestProperty("If-None-Match", savedEtag)
            }

            when (connection.responseCode) {
                304 -> { // 304 Not Modified
                    Log.d(TAG, "$fileName hasn't changed on GitHub. Downloaded 0 KB.")
                }
                200 -> { // 200 OK (New file found)
                    val json = connection.inputStream.bufferedReader().use { it.readText() }

                    // Safety check: Don't save empty files or HTML error pages
                    if (json.isNotBlank() && (json.trim().startsWith("{") || json.trim().startsWith("["))) {
                        File(context.filesDir, fileName).writeText(json)

                        // Save the new ETag fingerprint for next time
                        val newEtag = connection.getHeaderField("ETag")
                        if (newEtag != null) {
                            prefs.edit().putString(etagKey, newEtag).apply()
                        }
                        Log.d(TAG, "Successfully downloaded and updated OTA file: $fileName")
                    }
                }
                else -> Log.e(TAG, "Failed to check $fileName. HTTP Code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception while checking $fileName", e)
        }
    }
}