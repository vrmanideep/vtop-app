package com.vtop.logic

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object HolidaySync {

    private const val PREFS_NAME = "VTOP_PREFS"
    private const val CACHE_KEY = "BUNK_CACHE_JSON"

    // 1. Instantly load the holidays we already have saved on the phone
    fun getCachedHolidays(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(CACHE_KEY, "{}") ?: "{}"
        return parseJsonToMap(jsonString)
    }

    // 2. Fetch the latest JSON from your GitHub in the background
    suspend fun fetchFromGitHub(context: Context, rawGithubUrl: String): Map<String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(rawGithubUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }

                    // Save to offline cache for next time
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(CACHE_KEY, response)
                        .apply()

                    return@withContext parseJsonToMap(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext emptyMap()
        }
    }

    private fun parseJsonToMap(jsonString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("blocked_dates")) {
                val blocked = jsonObject.getJSONObject("blocked_dates")
                val keys = blocked.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = blocked.getString(key)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }
}