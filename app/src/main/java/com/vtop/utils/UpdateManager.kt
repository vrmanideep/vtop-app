package com.vtop.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.android.gms.tasks.Task
import com.vtop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateManager {
    private const val TAG = "UPDATE_MANAGER"

    private const val GITHUB_API_URL = "https://api.github.com/repos/vrmanideep/vtop-app/releases/latest"

    suspend fun checkForUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting Dual-Engine Update Check...")

        val firebaseResult = fetchFromFirebase()
        if (firebaseResult.isUpdateAvailable) {
            Log.d(TAG, "Update found via Firebase Remote Config.")
            return@withContext firebaseResult
        }

        Log.d(TAG, "No Firebase update found. Falling back to GitHub API...")
        val githubResult = fetchFromGitHub()
        if (githubResult.isUpdateAvailable) {
            Log.d(TAG, "Update found via GitHub Releases.")
            return@withContext githubResult
        }

        Log.d(TAG, "App is completely up to date.")
        return@withContext UpdateInfo(false, "", "", "", "")
    }

    private suspend fun fetchFromFirebase(): UpdateInfo = suspendCancellableCoroutine { continuation ->
        val remoteConfig = FirebaseRemoteConfig.getInstance()

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(0)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task: Task<Boolean> ->
            if (task.isSuccessful) {
                val latestVersion = remoteConfig.getString("latest_version")
                val downloadUrl = remoteConfig.getString("download_url")
                val releaseNotes = remoteConfig.getString("release_notes")

                if (latestVersion.isNotBlank() && downloadUrl.isNotBlank()) {
                    val isNewer = isVersionGreater(latestVersion, BuildConfig.VERSION_NAME)
                    continuation.resume(
                        UpdateInfo(
                            isUpdateAvailable = isNewer,
                            latestVersion = latestVersion,
                            releaseTitle = "Version $latestVersion Available",
                            releaseNotes = releaseNotes,
                            downloadUrl = downloadUrl
                        )
                    )
                } else {
                    continuation.resume(UpdateInfo(false, "", "", "", ""))
                }
            } else {
                Log.e(TAG, "Firebase fetch failed")
                continuation.resume(UpdateInfo(false, "", "", "", ""))
            }
        }
    }

    private suspend fun fetchFromGitHub(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "").replace("v", "")
                val releaseName = json.optString("name", "New Update")
                val body = json.optString("body", "Bug fixes and performance improvements.")

                var downloadUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    val asset = assets.getJSONObject(0)
                    downloadUrl = asset.optString("browser_download_url", "")
                }

                if (tagName.isNotBlank() && downloadUrl.isNotBlank()) {
                    val isNewer = isVersionGreater(tagName, BuildConfig.VERSION_NAME)
                    return@withContext UpdateInfo(
                        isUpdateAvailable = isNewer,
                        latestVersion = tagName,
                        releaseTitle = releaseName,
                        releaseNotes = body,
                        downloadUrl = downloadUrl
                    )
                }
            }
            return@withContext UpdateInfo(false, "", "", "", "")
        } catch (e: Exception) {
            Log.e(TAG, "GitHub API fetch failed", e)
            return@withContext UpdateInfo(false, "", "", "", "")
        }
    }

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String, version: String) {
        try {
            val fileName = "vtop_update_v$version.apk"

            // Delete old file if it exists
            val destinationFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (destinationFile.exists()) destinationFile.delete()

            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Downloading VTOP Update")
                .setDescription("Version $version is downloading...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            Toast.makeText(context, "Downloading update in background...", Toast.LENGTH_LONG).show()

            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (downloadId == id) {
                        installApk(ctxt, fileName)
                        ctxt.unregisterReceiver(this)
                    }
                }
            }

            // CRITICAL FIX: System broadcasts MUST be EXPORTED in Android 14+
            ContextCompat.registerReceiver(
                context,
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context, fileName: String) {
        try {
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Update file not found. Please try again.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            Toast.makeText(context, "Please install the APK manually from your Downloads folder.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isVersionGreater(v1: String, v2: String): Boolean {
        val parts1 = v1.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.replace("v", "").split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 > p2) return true
            if (p1 < p2) return false
        }
        return false
    }
}