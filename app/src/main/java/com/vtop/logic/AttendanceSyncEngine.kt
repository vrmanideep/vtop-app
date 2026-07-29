package com.vtop.logic

import android.content.Context
import android.util.Log
import com.vtop.network.VtopClient
import com.vtop.ui.core.AppBridge
import com.vtop.utils.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AttendanceSyncMode {
    OPTIMIZED,
    FORCE_FULL
}

data class AttendanceSyncResult(
    val total: Int,
    val detailFetched: Int,
    val cacheReused: Int,
    val successful: Boolean
)

object AttendanceSyncEngine {

    suspend fun sync(
        context: Context,
        client: VtopClient,
        semId: String,
        authorizedId: String,
        mode: AttendanceSyncMode = AttendanceSyncMode.OPTIMIZED,
        logTag: String = "ATT_OPT"
    ): AttendanceSyncResult {

        val html = client.fetchAttendanceRawHtml(semId, null) ?: ""
        val parsedSummary = AttendanceParser.parseSummary(html)
        val cachedAttendance = Vault.getAttendance(context).orEmpty()

        if (parsedSummary.isEmpty()) {
            if (cachedAttendance.isNotEmpty()) {
                Log.w(logTag, "SUMMARY EMPTY - preserving ${cachedAttendance.size} cached courses")
                withContext(Dispatchers.Main) {
                    AppBridge.attendanceState.value = cachedAttendance
                }
            } else {
                Log.w(logTag, "SUMMARY EMPTY - no cache to preserve")
            }
            return AttendanceSyncResult(0, 0, 0, false)
        }

        val summary = parsedSummary
        val cachedMap = cachedAttendance.associateBy { "${it.courseId}|${it.courseType}" }

        var syncSuccessful = true
        var fetched = 0
        var reused = 0

        for (course in summary) {
            val key = "${course.courseId}|${course.courseType}"
            val old = cachedMap[key]

            val summaryChanged = old == null ||
                    old.attendedClasses != course.attendedClasses ||
                    old.totalClasses != course.totalClasses ||
                    old.attendancePercentage != course.attendancePercentage

            val cachedTotal = old?.totalClasses?.toIntOrNull() ?: 0
            val historyMissing = old == null || (cachedTotal > 0 && old.history.isNullOrEmpty())

            val shouldFetchDetail = (mode == AttendanceSyncMode.FORCE_FULL) || summaryChanged || historyMissing

            if (shouldFetchDetail) {
                val cId = course.courseId
                val cType = course.courseType

                if (cId.isNullOrBlank() || cType.isNullOrBlank()) {
                    syncSuccessful = false
                    Log.w(logTag, "DETAIL INVALID ${course.courseCode} $cType - courseId/type missing")

                    if (old != null) {
                        course.history = ArrayList(old.history)
                        if (!old.classId.isNullOrBlank()) {
                            course.classId = old.classId
                        }
                    }
                    continue
                }

                val detailHtml = client.fetchAttendanceDetailRawHtml(
                    semId, cId, cType, authorizedId, null
                )

                val detailParsed = if (!detailHtml.isNullOrBlank()) {
                    AttendanceParser.parseDetailAndUpdate(detailHtml, course)
                } else {
                    false
                }

                if (detailParsed) {
                    fetched++
                    val forceStr = (mode == AttendanceSyncMode.FORCE_FULL)
                    Log.d(logTag, "FETCH ${course.courseCode} $cType changed=$summaryChanged missing=$historyMissing force=$forceStr")
                } else {
                    syncSuccessful = false
                    if (old != null) {
                        course.history = ArrayList(old.history)
                        if (!old.classId.isNullOrBlank()) {
                            course.classId = old.classId
                        }
                    }
                    Log.w(logTag, "DETAIL REJECTED ${course.courseCode} $cType - preserving cached history")
                }
            } else {
                course.history = ArrayList(old!!.history)
                if (!old.classId.isNullOrBlank()) {
                    course.classId = old.classId
                }
                reused++
                Log.d(logTag, "CACHE ${course.courseCode} ${course.courseType}")
            }
        }

        Vault.saveAttendance(context, summary)

        withContext(Dispatchers.Main) {
            AppBridge.attendanceState.value = summary
        }

        Log.i(logTag, "Attendance sync complete: total=${summary.size}, detailFetched=$fetched, cacheReused=$reused, mode=$mode, successful=$syncSuccessful")

        return AttendanceSyncResult(
            total = summary.size,
            detailFetched = fetched,
            cacheReused = reused,
            successful = syncSuccessful
        )
    }
}