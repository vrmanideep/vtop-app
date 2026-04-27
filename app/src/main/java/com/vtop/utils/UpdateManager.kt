package com.vtop.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.vtop.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: String
)

object UpdateManager {

    suspend fun checkForGitHubUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            // Hit the GitHub API for your specific repository
            val url = URL("https://api.github.com/repos/vrmanideep/vtop-app/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext UpdateInfo(false, "", "", "")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            // Extract tag (e.g. "v1.0.0" becomes "1.0.0")
            val tagName = json.getString("tag_name").replace("v", "").trim()
            val body = json.optString("body", "Bug fixes and performance improvements.")

            // Find the .apk file in the release assets
            var apkUrl = ""
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (apkUrl.isEmpty()) {
                return@withContext UpdateInfo(false, "", "", "")
            }

            val currentVersion = BuildConfig.VERSION_NAME.replace("v", "").trim()
            val isNewer = compareVersions(tagName, currentVersion) > 0

            return@withContext UpdateInfo(isNewer, tagName, apkUrl, body)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateInfo(false, "", "", "")
        }
    }

    // Semantic Versioning Comparer (1.0.0 > 0.5.0)
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 > p2) return 1
            if (p1 < p2) return -1
        }
        return 0
    }

    fun downloadAndInstallUpdate(context: Context, downloadUrl: String, version: String) {
        try {
            Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()

            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("VTOP Update v$version")
                .setDescription("Downloading latest version")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "vtop_update_$version.apk")
                .setMimeType("application/vnd.android.package-archive")

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            // Register receiver to automatically trigger install when download finishes
            val onComplete = object : BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        installApk(ctxt, "vtop_update_$version.apk")
                        ctxt.unregisterReceiver(this)
                    }
                }
            }
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)

        } catch (e: Exception) {
            Toast.makeText(context, "Failed to start download.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(context: Context, fileName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        }
    }
}