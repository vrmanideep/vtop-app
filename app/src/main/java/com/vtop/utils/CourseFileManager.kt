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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

data class DownloadedFileRecord(
    val key: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val semSubId: String,
    val classId: String,
    val relativePath: String,
    val fileName: String,
    val subPath: String,
    val isSyllabus: Boolean = false,
    val isCoursePlan: Boolean = false,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): String = JSONObject().apply {
        put("key", key)
        put("courseCode", courseCode)
        put("courseTitle", courseTitle)
        put("courseType", courseType)
        put("semSubId", semSubId)
        put("classId", classId)
        put("relativePath", relativePath)
        put("fileName", fileName)
        put("subPath", subPath)
        put("isSyllabus", isSyllabus)
        put("isCoursePlan", isCoursePlan)
        put("downloadedAt", downloadedAt)
    }.toString()

    companion object {
        fun fromJson(raw: String): DownloadedFileRecord? = try {
            val o = JSONObject(raw)
            DownloadedFileRecord(
                key = o.optString("key"),
                courseCode = o.optString("courseCode"),
                courseTitle = o.optString("courseTitle"),
                courseType = o.optString("courseType"),
                semSubId = o.optString("semSubId"),
                classId = o.optString("classId"),
                relativePath = o.optString("relativePath"),
                fileName = o.optString("fileName"),
                subPath = o.optString("subPath"),
                isSyllabus = o.optBoolean("isSyllabus", false),
                isCoursePlan = o.optBoolean("isCoursePlan", false),
                downloadedAt = o.optLong("downloadedAt", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}

object CourseFileManager {
    private const val PREF_SAVE_TO_DISK = "SAVE_COURSE_FILES_TO_DISK"
    private const val PREF_AUTO_OPEN = "AUTO_OPEN_COURSE_FILES"
    private const val PREF_DEV_OPTIONS = "DEV_OPTIONS_ENABLED"
    private const val PREF_FILE_HISTORY = "VTOP_COURSE_FILE_HISTORY_V2"
    private const val ROOT_FOLDER = "vtop_course_files"
    private const val CACHE_DIR_NAME = "course_materials"
    private const val MAX_CACHE_AGE_DAYS = 7L
    private const val MAX_CACHE_BYTES = 100L * 1024L * 1024L // 100 MB

    // --- Developer Options Flag ---

    fun isDevOptionsEnabled(context: Context): Boolean =
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .getBoolean(PREF_DEV_OPTIONS, false)

    fun setDevOptionsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_DEV_OPTIONS, enabled).apply()
    }

    fun computeCourseFilesFolderSizeBytes(context: Context): Long {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                var total = 0L
                val projection = arrayOf(android.provider.MediaStore.Downloads.SIZE)
                val selection = "${android.provider.MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("Download/vtop_course_files%")
                context.contentResolver.query(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val sizeCol = cursor.getColumnIndex(android.provider.MediaStore.Downloads.SIZE)
                    if (sizeCol != -1) {
                        while (cursor.moveToNext()) {
                            total += cursor.getLong(sizeCol).coerceAtLeast(0L)
                        }
                    }
                }
                total
            } else {
                val root = File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    "vtop_course_files"
                )
                if (!root.exists()) 0L
                else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> "%.2f GB".format(gb)
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.0f KB".format(kb)
            else -> "$bytes B"
        }
    }

    // --- Storage Mode ---
    fun isSaveToDiskEnabled(context: Context): Boolean =
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .getBoolean(PREF_SAVE_TO_DISK, true)

    fun setSaveToDiskEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SAVE_TO_DISK, enabled).apply()
    }

    fun isAutoOpenEnabled(context: Context): Boolean =
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .getBoolean(PREF_AUTO_OPEN, true)

    fun setAutoOpenEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("VTOP_PREFS", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_AUTO_OPEN, enabled).apply()
    }

    // --- Rich Download History ---

    fun recordDownloadedFile(context: Context, record: DownloadedFileRecord) {
        context.getSharedPreferences(PREF_FILE_HISTORY, Context.MODE_PRIVATE)
            .edit().putString(record.key, record.toJson()).apply()
    }

    fun getDownloadRecord(context: Context, key: String): DownloadedFileRecord? {
        val raw = context.getSharedPreferences(PREF_FILE_HISTORY, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        return DownloadedFileRecord.fromJson(raw)
    }

    fun getAllDownloadRecords(context: Context): List<DownloadedFileRecord> {
        val all = context.getSharedPreferences(PREF_FILE_HISTORY, Context.MODE_PRIVATE).all
        return all.values.mapNotNull { (it as? String)?.let(DownloadedFileRecord::fromJson) }
    }

    fun removeDownloadRecord(context: Context, key: String) {
        context.getSharedPreferences(PREF_FILE_HISTORY, Context.MODE_PRIVATE)
            .edit().remove(key).apply()
    }

    fun getDownloadedKeys(context: Context, keys: List<String>): Set<String> {
        val prefs = context.getSharedPreferences(PREF_FILE_HISTORY, Context.MODE_PRIVATE)
        val result = mutableSetOf<String>()
        for (key in keys) {
            val raw = prefs.getString(key, null) ?: continue
            val record = DownloadedFileRecord.fromJson(raw) ?: continue
            if (findExistingUri(context, record) != null) {
                result.add(key)
            }
        }
        return result
    }

    // --- Folder Path Builder ---

    fun sanitizeFolderName(raw: String): String =
        raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)

    /**
     * Builds the relative path under `Downloads/vtop_course_files`:
     * - Course root: `{courseCode}- {courseTitle}`
     * - Syllabus: always in course root
     * - Embedded courses (ETH / ELA): `Theory` or `Lab` subfolder (created lazily on save)
     * - Single-component courses (TH, LO, SS, PJT, etc.): flat in course root
     */
    fun buildCourseSubPath(
        courseCode: String,
        courseTitle: String,
        courseType: String,
        isSyllabus: Boolean = false
    ): String {
        val cleanCode = sanitizeFolderName(courseCode)
        val cleanTitle = sanitizeFolderName(courseTitle)
        val courseDir = if (cleanTitle.isNotBlank()) "$cleanCode- $cleanTitle" else cleanCode
        if (isSyllabus) return courseDir

        val typeUpper = courseType.trim().uppercase()
        val componentDir = when {
            typeUpper == "ETH" || typeUpper.contains("EMBEDDED THEORY") -> "Theory"
            typeUpper == "ELA" || typeUpper.contains("EMBEDDED LAB") -> "Lab"
            else -> null
        }
        return if (componentDir != null) "$courseDir/$componentDir" else courseDir
    }

    fun buildMaterialFileName(
        courseCode: String,
        slot: String,
        facultyName: String,
        label: String,
        ext: String,
        slNo: String = "",
        date: String = "",
        topic: String = "",
        relativePath: String = "",
        isZip: Boolean = false,
        isCoursePlan: Boolean = false,
        isLecture: Boolean = false,
        isGeneral: Boolean = false,
        includeFacultySuffix: Boolean = false
    ): String {
        val code = sanitizePart(courseCode, 20)
        val snoPadded = slNo.toIntOrNull()?.let { "%02d".format(it) } ?: slNo.padStart(2, '0')
        val customDate = sanitizePart(date, 20)
        val safeTopic = sanitizePart(topic.ifBlank { label }, 40)
        val safeSlot = sanitizePart(slot, 20)
        val facOnly = facultyName.split(" - ").getOrNull(1) ?: facultyName
        val safeFac = sanitizePart(facOnly, 30)
        val facSuffix = if (includeFacultySuffix) {
            listOf(safeSlot, safeFac).filter { it.isNotBlank() }.joinToString("_")
        } else ""

        val baseName = when {
            isZip -> "${code}_all_${safeSlot}_${safeFac}"
            isLecture -> listOf("${code}_L${snoPadded}", customDate, safeTopic, facSuffix)
                .filter { it.isNotBlank() }
                .joinToString("_")
            isGeneral -> {
                val pathDate = relativePath.substringAfterLast('/', "").takeIf { it.contains("-") } ?: ""
                val gmDate = sanitizePart(date.ifBlank { pathDate }, 20)
                listOf("${code}_GM${snoPadded}", gmDate, safeTopic, facSuffix)
                    .filter { it.isNotBlank() }
                    .joinToString("_")
            }
            isCoursePlan -> "${code}_${safeSlot}_${safeFac}_CoursePlan"
            else -> "${code}_${sanitizePart(label, 30)}"
        }
        return "$baseName.$ext"
    }

    // --- Cache Management ---

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

    // --- Lookup & Persistence ---

    fun findExistingUri(context: Context, record: DownloadedFileRecord): Uri? {
        return findExistingUri(context, record.fileName, record.subPath)
    }

    fun findExistingUri(context: Context, fileName: String, subPath: String = ""): Uri? {
        // 1. Check cache subfolder first
        val cached = File(getCacheDir(context), fileName)
        if (cached.exists() && cached.length() > 0) {
            return FileProvider.getUriForFile(context, "${context.packageName}.provider", cached)
        }

        val cleanSub = subPath.trim('/')
        // 2. Check Downloads/vtop_course_files/{subPath}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val expectedRelPath = if (cleanSub.isNotEmpty()) {
                "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER/$cleanSub/"
            } else {
                "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER/"
            }
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
            val args = arrayOf(fileName, expectedRelPath)
            context.contentResolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    return Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ROOT_FOLDER)
            val dir = if (cleanSub.isNotEmpty()) File(base, cleanSub) else base
            val diskFile = File(dir, fileName)
            if (diskFile.exists() && diskFile.length() > 0) {
                return FileProvider.getUriForFile(context, "${context.packageName}.provider", diskFile)
            }
        }
        return null
    }

    fun persistDownloadedFile(
        context: Context,
        tempFile: File,
        finalFileName: String,
        subPath: String,
        mimeType: String?
    ): Uri? {
        cleanupCache(context)
        val resolvedMime = mimeType?.substringBefore(";")?.takeIf { it.isNotBlank() }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(finalFileName.substringAfterLast('.', "pdf"))
            ?: "application/octet-stream"

        return if (isSaveToDiskEnabled(context)) {
            val cleanSub = subPath.trim('/')
            val relPath = if (cleanSub.isNotEmpty()) {
                "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER/$cleanSub"
            } else {
                "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_FOLDER"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
                    put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, relPath)
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
                val base = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ROOT_FOLDER)
                val dir = (if (cleanSub.isNotEmpty()) File(base, cleanSub) else base).apply { mkdirs() }
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
            val folderUri = Uri.parse(
                "content://com.android.externalstorage.documents/document/primary:Download%2F$ROOT_FOLDER"
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(context, "Unable to open Downloads/$ROOT_FOLDER", Toast.LENGTH_SHORT).show()
            }
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