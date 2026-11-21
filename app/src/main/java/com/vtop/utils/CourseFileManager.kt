package com.vtop.utils

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

object CourseFileManager {
    private const val PREF_SAVE_TO_DISK = "SAVE_COURSE_FILES_TO_DISK"
    private const val CACHE_DIR_NAME = "course_materials"
    private const val MAX_CACHE_AGE_DAYS = 7L
    private const val MAX_CACHE_BYTES = 100L * 1024L * 1024L // 100 MB

    private const val PREF_FILE_MAP = "VTOP_COURSE_FILE_MAP"

    fun recordDownloadedFile(context: Context, key: String, fileName: String) {
        context.getSharedPreferences(PREF_FILE_MAP, Context.MODE_PRIVATE)
            .edit().putString(key, fileName).apply()
    }

    fun getSavedFileName(context: Context, key: String): String? =
        context.getSharedPreferences(PREF_FILE_MAP, Context.MODE_PRIVATE)
            .getString(key, null)

    fun getDownloadedKeys(context: Context, keys: List<String>): Set<String> {
        val prefs = context.getSharedPreferences(PREF_FILE_MAP, Context.MODE_PRIVATE)
        val result = mutableSetOf<String>()
        for (key in keys) {
            val savedName = prefs.getString(key, null) ?: continue
            if (findExistingUri(context, savedName) != null) {
                result.add(key)
            }
        }
        return result
    }

    fun isSaveToDiskEnabled(context: Context): Boolean =
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .getBoolean(PREF_SAVE_TO_DISK, true)

    fun setSaveToDiskEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SAVE_TO_DISK, enabled).apply()
    }

    fun getCacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR_NAME).apply { if (!exists()) mkdirs() }

    fun cleanupCache(context: Context) {
        val dir = getCacheDir(context)
        val files = dir.listFiles()?.toMutableList() ?: return
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_CACHE_AGE_DAYS)

        files.removeAll { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
                true
            } else false
        }

        var totalSize = files.sumOf { it.length() }
        if (totalSize > MAX_CACHE_BYTES) {
            files.sortBy { it.lastModified() }
            for (file in files) {
                if (totalSize <= MAX_CACHE_BYTES) break
                totalSize -= file.length()
                file.delete()
            }
        }
    }

    fun buildSafeFileName(courseCode: String, key: String, serverFileName: String?, fallbackExt: String = "pdf"): String {
        val cleanCode = courseCode.replace(Regex("[^A-Za-z0-9_-]"), "_")
        if (!serverFileName.isNullOrBlank()) {
            val cleanServer = serverFileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            return if (cleanServer.startsWith(cleanCode, true)) cleanServer else "${cleanCode}_$cleanServer"
        }
        val cleanKey = key.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
        return "${cleanCode}_${cleanKey}.$fallbackExt"
    }

    fun findExistingUri(context: Context, fileName: String): Uri? {
        // 1. Check cache subfolder first
        val cached = File(getCacheDir(context), fileName)
        if (cached.exists() && cached.length() > 0) {
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", cached)
        }
        // 2. Check Downloads/VTOP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
            val args = arrayOf(fileName, "%Download%VTOP%")
            context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val diskFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VTOP/$fileName")
            if (diskFile.exists() && diskFile.length() > 0) {
                return FileProvider.getUriForFile(context, "${context.packageName}.provider", diskFile)
            }
        }
        return null
    }

    fun persistDownloadedFile(context: Context, tempFile: File, finalFileName: String, mimeType: String?): Uri? {
        cleanupCache(context)
        val resolvedMime = mimeType?.substringBefore(";")?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(finalFileName.substringAfterLast('.', "pdf"))
            ?: "application/octet-stream"

        return if (isSaveToDiskEnabled(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VTOP")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(tempFile).use { it.copyTo(out) }
                    }
                    tempFile.delete()
                }
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VTOP").apply { mkdirs() }
                val dest = File(dir, finalFileName)
                tempFile.copyTo(dest, overwrite = true)
                tempFile.delete()
                FileProvider.getUriForFile(context, "${context.packageName}.provider", dest)
            }
        } else {
            val dest = File(getCacheDir(context), finalFileName)
            tempFile.copyTo(dest, overwrite = true)
            tempFile.delete()
            FileProvider.getUriForFile(context, "${context.packageName}.provider", dest)
        }
    }

    fun openFile(context: Context, uri: Uri, fileName: String) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No app found to open .$ext files", Toast.LENGTH_SHORT).show()
        }
    }

    fun openDownloadsFolder(context: Context) {
        try {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open Downloads folder", Toast.LENGTH_SHORT).show()
        }
    }

    fun sanitizePart(raw: String, maxLen: Int = 40): String =
        raw.trim()
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(maxLen)

    fun extractExtension(serverFileName: String?, fileNameHint: String, fallbackExt: String): String {
        val fromServer = serverFileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 6 }
        val fromHint = fileNameHint.substringAfterLast('.', "").takeIf { fileNameHint.contains(".") && it.isNotBlank() && it.length <= 6 }
        return (fromServer ?: fromHint ?: fallbackExt).lowercase()
    }

    fun isAppInForeground(): Boolean =
        androidx.lifecycle.ProcessLifecycleOwner.get()
            .lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
}