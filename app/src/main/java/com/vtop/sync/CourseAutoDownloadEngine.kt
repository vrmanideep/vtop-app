package com.vtop.sync

import android.content.Context
import android.util.Log
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vtop.logic.CoursePageParser
import com.vtop.models.CourseDetailData
import com.vtop.models.CourseMaterialItem
import com.vtop.network.VtopClient
import com.vtop.utils.AutoDownloadSettings
import com.vtop.utils.CourseAutoDownloadConfig
import com.vtop.utils.CourseFileManager
import com.vtop.utils.DownloadedFileRecord
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object CourseAutoDownloadEngine {
    private const val TAG = "COURSE_AUTO_DL"
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Overlap guard across manual, interval, and specific-time triggers
    private val runningGuard = AtomicBoolean(false)

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _progressText = MutableStateFlow("")
    val progressText = _progressText.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    private data class ResolvedTarget(
        val semId: String,
        val erpId: String,
        val classId: String,
        val label: String
    )

    fun startManualRun(context: Context, client: VtopClient?) {
        if (client == null) {
            CourseAutoDownloadConfig.updateLastRun(context, "Skipped (no active session)")
            return
        }
        if (!runningGuard.compareAndSet(false, true)) {
            Log.d(TAG, "Auto-download already running; ignoring duplicate trigger.")
            return
        }
        activeJob = engineScope.launch {
            executeInternal(context.applicationContext, client, notifyOnNewFiles = false)
        }
    }

    /**
     * Suspending entry point for WorkManager. Respects the same overlap guard.
     */
    suspend fun runBlockingForWorker(context: Context, client: VtopClient): Boolean {
        if (!runningGuard.compareAndSet(false, true)) {
            Log.d(TAG, "Auto-download already running; worker skipped.")
            return true
        }
        return withContext(Dispatchers.IO) {
            executeInternal(context.applicationContext, client, notifyOnNewFiles = true)
        }
    }

    fun cancel() {
        activeJob?.cancel()
        _progressText.value = "Cancelling..."
    }

    fun findDeletedRecords(context: Context): List<DownloadedFileRecord> {
        val settings = CourseAutoDownloadConfig.load(context)
        return CourseFileManager.getAllDownloadRecords(context)
            .filter { record ->
                record.key !in settings.forgottenKeys &&
                        CourseFileManager.findExistingUri(context, record) == null
            }
            .sortedByDescending { it.downloadedAt }
    }

    suspend fun redownloadSelectedRecords(
        context: Context,
        client: VtopClient,
        records: List<DownloadedFileRecord>,
        onProgress: (String) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        var failCount = 0

        records.forEachIndexed { idx, record ->
            onProgress("Re-downloading ${idx + 1}/${records.size}: ${record.fileName}")
            val tempFile = File(context.cacheDir, "redownload_${System.currentTimeMillis()}")
            val res = client.downloadCourseMaterial(
                record.relativePath,
                record.isCoursePlan,
                record.semSubId,
                record.classId,
                tempFile
            )
            if (res.success) {
                val uri = CourseFileManager.persistDownloadedFile(
                    context = context,
                    tempFile = tempFile,
                    finalFileName = record.fileName,
                    subPath = record.subPath,
                    mimeType = res.mimeType
                )
                if (uri != null) {
                    CourseFileManager.recordDownloadedFile(
                        context,
                        record.copy(downloadedAt = System.currentTimeMillis())
                    )
                    successCount++
                } else {
                    tempFile.delete()
                    failCount++
                }
            } else {
                tempFile.delete()
                failCount++
            }
            delay(100.milliseconds)
        }
        successCount to failCount
    }

    private suspend fun executeInternal(
        context: Context,
        client: VtopClient,
        notifyOnNewFiles: Boolean
    ): Boolean {
        _isRunning.value = true
        _progressText.value = "Preparing targets..."
        var downloadedCount = 0
        var skippedCount = 0
        var failedCount = 0
        val updatedCourses = linkedSetOf<String>()

        try {
            if (!CourseFileManager.isSaveToDiskEnabled(context)) {
                CourseAutoDownloadConfig.updateLastRun(context, "Skipped (save to disk is off)")
                return true
            }

            val settings = CourseAutoDownloadConfig.load(context)
            val targets = resolveAllTargets(client, settings)
            if (targets.isEmpty()) {
                CourseAutoDownloadConfig.updateLastRun(context, "No active courses selected")
                return true
            }

            val total = targets.size
            targets.forEachIndexed { idx, target ->
                currentCoroutineContext().ensureActive()
                _progressText.value = "Checking ${target.label} (${idx + 1}/$total)..."

                val detailHtml = client.fetchCourseDetailRawHtml(target.semId, target.erpId, target.classId)
                val detail = detailHtml?.let { CoursePageParser.parseCourseDetail(it) }
                if (detail == null) {
                    Log.w(TAG, "Failed to parse detail for ${target.label} (htmlLen=${detailHtml?.length ?: 0})")
                    failedCount++
                    return@forEachIndexed
                }
                Log.d(
                    TAG,
                    "${detail.courseCode}: syllabus=${!detail.syllabusPath.isNullOrBlank()}, " +
                            "general=${detail.generalMaterials.size}, lectures=${detail.lectures.size}"
                )

                val multiFaculty = CourseAutoDownloadConfig.countActiveFacultiesForCourse(
                    settings = settings,
                    courseCode = detail.courseCode,
                    courseType = detail.courseType
                ) > 1

                currentCoroutineContext().ensureActive()
                // 1. Syllabus (always saved in course root folder)
                detail.syllabusPath?.takeIf { it.isNotBlank() }?.let { path ->
                    val item = CourseMaterialItem("Syllabus", path, "Syllabus.pdf")
                    when (downloadIfNeeded(context, client, settings, detail, item, isSyllabus = true, includeFacultySuffix = false)) {
                        DownloadOutcome.DOWNLOADED -> {
                            downloadedCount++
                            updatedCourses.add(detail.courseCode)
                        }
                        DownloadOutcome.SKIPPED -> skippedCount++
                        DownloadOutcome.FAILED -> failedCount++
                    }
                }

                // 2. General Materials (skips Course Plan & Download All ZIP)
                detail.generalMaterials.forEachIndexed { gmIdx, mat ->
                    currentCoroutineContext().ensureActive()
                    if (mat.isWebLink || mat.isCoursePlan || mat.isZip) return@forEachIndexed
                    when (
                        downloadIfNeeded(
                            context = context,
                            client = client,
                            settings = settings,
                            detail = detail,
                            item = mat,
                            slNo = (gmIdx + 1).toString(),
                            topic = mat.label,
                            isGeneral = true,
                            includeFacultySuffix = multiFaculty
                        )
                    ) {
                        DownloadOutcome.DOWNLOADED -> {
                            downloadedCount++
                            updatedCourses.add(detail.courseCode)
                        }
                        DownloadOutcome.SKIPPED -> skippedCount++
                        DownloadOutcome.FAILED -> failedCount++
                    }
                }

                // 3. Lecture Materials
                detail.lectures.forEach { lec ->
                    lec.materials.forEach { mat ->
                        currentCoroutineContext().ensureActive()
                        if (mat.isWebLink || mat.isCoursePlan || mat.isZip) return@forEach
                        when (
                            downloadIfNeeded(
                                context = context,
                                client = client,
                                settings = settings,
                                detail = detail,
                                item = mat,
                                slNo = lec.slNo,
                                date = lec.date,
                                topic = lec.topic,
                                isLecture = true,
                                includeFacultySuffix = multiFaculty
                            )
                        ) {
                            DownloadOutcome.DOWNLOADED -> {
                                downloadedCount++
                                updatedCourses.add(detail.courseCode)
                            }
                            DownloadOutcome.SKIPPED -> skippedCount++
                            DownloadOutcome.FAILED -> failedCount++
                        }
                    }
                }

                delay(150.milliseconds)// light breather between courses
            }

            val summary = when {
                downloadedCount > 0 -> "$downloadedCount new file${if (downloadedCount == 1) "" else "s"}"
                failedCount > 0 -> "0 new · $failedCount failed"
                else -> "Up to date"
            }
            CourseAutoDownloadConfig.updateLastRun(context, summary)
            if (notifyOnNewFiles && downloadedCount > 0) {
                postSummaryNotification(context, downloadedCount, updatedCourses)
            }
            return true
        } catch (_: CancellationException) {
            CourseAutoDownloadConfig.updateLastRun(context, "Cancelled ($downloadedCount downloaded)")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Auto-download error: ${e.message}", e)
            CourseAutoDownloadConfig.updateLastRun(context, "Error: ${e.message ?: "Unknown"}")
            return false
        } finally {
            _progressText.value = ""
            _isRunning.value = false
            activeJob = null
            runningGuard.set(false)
        }
    }

    private fun resolveAllTargets(client: VtopClient, settings: AutoDownloadSettings): List<ResolvedTarget> {
        val result = mutableListOf<ResolvedTarget>()
        val seen = mutableSetOf<String>()

        val trackedSems = (listOf(settings.activeSemId) + settings.extraSemIds).filter { it.isNotBlank() }.toSet()

        // 1. Registered / multi-faculty course configs
        settings.courseConfigs
            .filter { it.enabled && it.semId in trackedSems }
            .forEach { cfg ->
                if (cfg.faculties.isNotEmpty()) {
                    cfg.faculties.forEach { fac ->
                        val dedupeKey = "${cfg.semId}_${fac.classId}_${fac.erpId}"
                        if (seen.add(dedupeKey)) {
                            result.add(ResolvedTarget(cfg.semId, fac.erpId, fac.classId, cfg.courseCode))
                        }
                    }
                } else {
                    // Resolve default registered faculty from slot list
                    val html = client.fetchSlotSectionsRawHtml(cfg.semId, cfg.defaultClassId)
                    val sections = html?.let { CoursePageParser.parseSlotSections(it) } ?: emptyList()
                    val matched = sections.firstOrNull { it.classId.equals(cfg.defaultClassId, ignoreCase = true) }
                    if (matched != null) {
                        val dedupeKey = "${cfg.semId}_${matched.classId}_${matched.erpId}"
                        if (seen.add(dedupeKey)) {
                            result.add(ResolvedTarget(cfg.semId, matched.erpId, matched.classId, cfg.courseCode))
                        }
                    }
                }
            }

        // 2. Custom Class ID targets
        settings.customClassTargets
            .filter { it.enabled }
            .forEach { custom ->
                val dedupeKey = "${custom.semId}_${custom.classId}_${custom.erpId}"
                if (seen.add(dedupeKey)) {
                    result.add(
                        ResolvedTarget(
                            semId = custom.semId,
                            erpId = custom.erpId,
                            classId = custom.classId,
                            label = custom.courseCode.ifBlank { custom.classId }
                        )
                    )
                }
            }

        return result
    }

    private enum class DownloadOutcome { DOWNLOADED, SKIPPED, FAILED }

    private fun downloadIfNeeded(
        context: Context,
        client: VtopClient,
        settings: AutoDownloadSettings,
        detail: CourseDetailData,
        item: CourseMaterialItem,
        slNo: String = "",
        date: String = "",
        topic: String = "",
        isLecture: Boolean = false,
        isGeneral: Boolean = false,
        isSyllabus: Boolean = false,
        includeFacultySuffix: Boolean = false
    ): DownloadOutcome {
        val key = item.relativePath.ifBlank { item.label }
        if (key in settings.forgottenKeys) {
            Log.d(TAG, "SKIP (forgotten): $key")
            return DownloadOutcome.SKIPPED
        }

        val existingRecord = CourseFileManager.getDownloadRecord(context, key)
        if (existingRecord != null) {
            val existsOnDisk = CourseFileManager.findExistingUri(context, existingRecord) != null
            if (existsOnDisk) {
                Log.d(TAG, "SKIP (exists on disk): ${existingRecord.fileName}")
                return DownloadOutcome.SKIPPED
            }
            if (!settings.redownloadDeleted) {
                Log.d(TAG, "SKIP (deleted & redownload off): ${existingRecord.fileName}")
                return DownloadOutcome.SKIPPED
            }
        }

        _progressText.value = "Downloading ${detail.courseCode}: ${item.label}..."
        val tempFile = File(context.cacheDir, "auto_download_${System.currentTimeMillis()}")
        val res = client.downloadCourseMaterial(item.relativePath, false, detail.semSubId, detail.classId, tempFile)
        if (!res.success) {
            Log.w(TAG, "FAIL download: ${item.label} (${item.relativePath})")
            tempFile.delete()
            return DownloadOutcome.FAILED
        }

        val ext = CourseFileManager.extractExtension(res.serverFileName, item.fileNameHint, "pdf")
        val finalName = CourseFileManager.buildMaterialFileName(
            courseCode = detail.courseCode,
            slot = detail.slot,
            facultyName = detail.facultyName,
            label = item.label,
            ext = ext,
            slNo = slNo,
            date = date,
            topic = topic,
            relativePath = item.relativePath,
            isLecture = isLecture,
            isGeneral = isGeneral,
            includeFacultySuffix = includeFacultySuffix
        )
        val subPath = CourseFileManager.buildCourseSubPath(
            courseCode = detail.courseCode,
            courseTitle = detail.courseTitle,
            courseType = detail.courseType,
            isSyllabus = isSyllabus
        )

        val uri = CourseFileManager.persistDownloadedFile(context, tempFile, finalName, subPath, res.mimeType)
        if (uri != null) {
            CourseFileManager.recordDownloadedFile(
                context,
                DownloadedFileRecord(
                    key = key,
                    courseCode = detail.courseCode,
                    courseTitle = detail.courseTitle,
                    courseType = detail.courseType,
                    semSubId = detail.semSubId,
                    classId = detail.classId,
                    relativePath = item.relativePath,
                    fileName = finalName,
                    subPath = subPath,
                    isSyllabus = isSyllabus,
                    isCoursePlan = false
                )
            )
            return DownloadOutcome.DOWNLOADED
        }
        tempFile.delete()
        return DownloadOutcome.FAILED
    }

    private fun postSummaryNotification(
        context: Context,
        downloadedCount: Int,
        courses: Set<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "course_auto_dl_summary"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Course Auto-Download",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Summary notifications when scheduled auto-download fetches new course files"
            }
            manager.createNotificationChannel(channel)
        }

        val folderUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fvtop_course_files")
        val openFolderIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val tapIntent = if (openFolderIntent.resolveActivity(context.packageManager) != null) {
            openFolderIntent
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        }

        val pendingIntent = tapIntent?.let {
            PendingIntent.getActivity(
                context,
                4021,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val title = "$downloadedCount new course file${if (downloadedCount == 1) "" else "s"} downloaded"
        val body = if (courses.isNotEmpty()) {
            courses.joinToString(", ")
        } else {
            "Saved to Downloads/vtop_course_files"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .apply { if (pendingIntent != null) setContentIntent(pendingIntent) }
            .build()

        manager.notify(4021, notification)
    }
}