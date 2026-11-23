package com.vtop.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AutoDownloadFacultyTarget(
    val erpId: String,
    val classId: String,
    val facultyName: String,
    val slot: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("erpId", erpId)
        put("classId", classId)
        put("facultyName", facultyName)
        put("slot", slot)
    }

    companion object {
        fun fromJson(o: JSONObject): AutoDownloadFacultyTarget = AutoDownloadFacultyTarget(
            erpId = o.optString("erpId"),
            classId = o.optString("classId"),
            facultyName = o.optString("facultyName"),
            slot = o.optString("slot")
        )
    }
}

data class AutoDownloadCourseConfig(
    val semId: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val defaultClassId: String,
    val enabled: Boolean = true,
    val faculties: List<AutoDownloadFacultyTarget> = emptyList()
) {
    val configKey: String get() = "${semId}_${courseCode}_${courseType.trim().uppercase()}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("semId", semId)
        put("courseCode", courseCode)
        put("courseTitle", courseTitle)
        put("courseType", courseType)
        put("defaultClassId", defaultClassId)
        put("enabled", enabled)
        put("faculties", JSONArray().apply { faculties.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): AutoDownloadCourseConfig {
            val facArr = o.optJSONArray("faculties") ?: JSONArray()
            val facs = (0 until facArr.length()).mapNotNull { i ->
                facArr.optJSONObject(i)?.let(AutoDownloadFacultyTarget::fromJson)
            }
            return AutoDownloadCourseConfig(
                semId = o.optString("semId"),
                courseCode = o.optString("courseCode"),
                courseTitle = o.optString("courseTitle"),
                courseType = o.optString("courseType"),
                defaultClassId = o.optString("defaultClassId"),
                enabled = o.optBoolean("enabled", true),
                faculties = facs
            )
        }
    }
}

data class CustomClassIdTarget(
    val semId: String,
    val classId: String,
    val erpId: String,
    val facultyName: String,
    val slot: String,
    val courseCode: String,
    val courseTitle: String,
    val courseType: String,
    val enabled: Boolean = true
) {
    val targetKey: String get() = "${semId}_${classId}_${erpId}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("semId", semId)
        put("classId", classId)
        put("erpId", erpId)
        put("facultyName", facultyName)
        put("slot", slot)
        put("courseCode", courseCode)
        put("courseTitle", courseTitle)
        put("courseType", courseType)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject): CustomClassIdTarget = CustomClassIdTarget(
            semId = o.optString("semId"),
            classId = o.optString("classId"),
            erpId = o.optString("erpId"),
            facultyName = o.optString("facultyName"),
            slot = o.optString("slot"),
            courseCode = o.optString("courseCode"),
            courseTitle = o.optString("courseTitle"),
            courseType = o.optString("courseType"),
            enabled = o.optBoolean("enabled", true)
        )
    }
}

data class LastRunStatus(
    val timestamp: Long = 0L,
    val summary: String = ""
)

data class AutoDownloadSettings(
    val enabled: Boolean = false,
    val activeSemId: String = "",
    val extraSemIds: List<String> = emptyList(),
    val intervalEnabled: Boolean = true,
    val intervalHours: Int = 6,
    val specificTimeEnabled: Boolean = false,
    val specificTimes: List<String> = emptyList(), // "HH:mm" 24-hr format
    val wifiOnly: Boolean = true,
    val redownloadDeleted: Boolean = false,
    val courseConfigs: List<AutoDownloadCourseConfig> = emptyList(),
    val customClassTargets: List<CustomClassIdTarget> = emptyList(),
    val forgottenKeys: Set<String> = emptySet(),
    val lastRun: LastRunStatus = LastRunStatus()
)

object CourseAutoDownloadConfig {
    private const val PREFS_NAME = "VTOP_COURSE_AUTO_DOWNLOAD"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ACTIVE_SEM = "active_sem_id"
    private const val KEY_EXTRA_SEMS = "extra_sem_ids"
    private const val KEY_INTERVAL_ENABLED = "interval_enabled"
    private const val KEY_INTERVAL_HOURS = "interval_hours"
    private const val KEY_SPECIFIC_TIME_ENABLED = "specific_time_enabled"
    private const val KEY_SPECIFIC_TIMES = "specific_times"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_REDOWNLOAD_DELETED = "redownload_deleted"
    private const val KEY_COURSE_CONFIGS = "course_configs"
    private const val KEY_CUSTOM_TARGETS = "custom_class_targets"
    private const val KEY_FORGOTTEN_KEYS = "forgotten_keys"
    private const val KEY_LAST_RUN_TIME = "last_run_time"
    private const val KEY_LAST_RUN_SUMMARY = "last_run_summary"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AutoDownloadSettings {
        val p = prefs(context)
        val defaultSem = Vault.getSelectedSemester(context)[0] ?: ""
        val activeSem = p.getString(KEY_ACTIVE_SEM, null)?.takeIf { it.isNotBlank() } ?: defaultSem

        val extraSems = parseStringList(p.getString(KEY_EXTRA_SEMS, null))
        val specificTimes = parseStringList(p.getString(KEY_SPECIFIC_TIMES, null))

        val coursesArr = try { JSONArray(p.getString(KEY_COURSE_CONFIGS, "[]")) } catch (_: Exception) { JSONArray() }
        val courseConfigs = (0 until coursesArr.length()).mapNotNull { i ->
            coursesArr.optJSONObject(i)?.let(AutoDownloadCourseConfig::fromJson)
        }

        val customArr = try { JSONArray(p.getString(KEY_CUSTOM_TARGETS, "[]")) } catch (_: Exception) { JSONArray() }
        val customTargets = (0 until customArr.length()).mapNotNull { i ->
            customArr.optJSONObject(i)?.let(CustomClassIdTarget::fromJson)
        }

        val forgotten = p.getStringSet(KEY_FORGOTTEN_KEYS, emptySet())?.toSet() ?: emptySet()

        return AutoDownloadSettings(
            enabled = p.getBoolean(KEY_ENABLED, false),
            activeSemId = activeSem,
            extraSemIds = extraSems,
            intervalEnabled = p.getBoolean(KEY_INTERVAL_ENABLED, true),
            intervalHours = p.getInt(KEY_INTERVAL_HOURS, 6).coerceAtLeast(1),
            specificTimeEnabled = p.getBoolean(KEY_SPECIFIC_TIME_ENABLED, false),
            specificTimes = specificTimes,
            wifiOnly = p.getBoolean(KEY_WIFI_ONLY, true),
            redownloadDeleted = p.getBoolean(KEY_REDOWNLOAD_DELETED, false),
            courseConfigs = courseConfigs,
            customClassTargets = customTargets,
            forgottenKeys = forgotten,
            lastRun = LastRunStatus(
                timestamp = p.getLong(KEY_LAST_RUN_TIME, 0L),
                summary = p.getString(KEY_LAST_RUN_SUMMARY, "") ?: ""
            )
        )
    }

    fun save(context: Context, settings: AutoDownloadSettings) {
        val coursesJson = JSONArray().apply { settings.courseConfigs.forEach { put(it.toJson()) } }.toString()
        val customJson = JSONArray().apply { settings.customClassTargets.forEach { put(it.toJson()) } }.toString()
        val extraSemsJson = JSONArray(settings.extraSemIds.distinct()).toString()
        val timesJson = JSONArray(settings.specificTimes.distinct()).toString()

        prefs(context).edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_ACTIVE_SEM, settings.activeSemId)
            .putString(KEY_EXTRA_SEMS, extraSemsJson)
            .putBoolean(KEY_INTERVAL_ENABLED, settings.intervalEnabled)
            .putInt(KEY_INTERVAL_HOURS, settings.intervalHours.coerceAtLeast(1))
            .putBoolean(KEY_SPECIFIC_TIME_ENABLED, settings.specificTimeEnabled)
            .putString(KEY_SPECIFIC_TIMES, timesJson)
            .putBoolean(KEY_WIFI_ONLY, settings.wifiOnly)
            .putBoolean(KEY_REDOWNLOAD_DELETED, settings.redownloadDeleted)
            .putString(KEY_COURSE_CONFIGS, coursesJson)
            .putString(KEY_CUSTOM_TARGETS, customJson)
            .putStringSet(KEY_FORGOTTEN_KEYS, settings.forgottenKeys)
            .putLong(KEY_LAST_RUN_TIME, settings.lastRun.timestamp)
            .putString(KEY_LAST_RUN_SUMMARY, settings.lastRun.summary)
            .apply()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun updateLastRun(context: Context, summary: String, timestamp: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_LAST_RUN_TIME, timestamp)
            .putString(KEY_LAST_RUN_SUMMARY, summary)
            .apply()
    }

    fun addForgottenKeys(context: Context, keys: Collection<String>) {
        val current = prefs(context).getStringSet(KEY_FORGOTTEN_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.addAll(keys)
        prefs(context).edit().putStringSet(KEY_FORGOTTEN_KEYS, current).apply()
    }

    fun removeForgottenKeys(context: Context, keys: Collection<String>) {
        val current = prefs(context).getStringSet(KEY_FORGOTTEN_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.removeAll(keys.toSet())
        prefs(context).edit().putStringSet(KEY_FORGOTTEN_KEYS, current).apply()
    }

    /**
     * Counts distinct active faculties targeting the same (courseCode, courseType)
     * across both multi-faculty course configs and custom Class ID targets.
     */
    fun countActiveFacultiesForCourse(
        settings: AutoDownloadSettings,
        courseCode: String,
        courseType: String
    ): Int {
        val normCode = courseCode.trim().uppercase()
        val normType = normalizeComponentType(courseType)
        val activeFacultyIds = mutableSetOf<String>()

        settings.courseConfigs
            .filter { it.enabled && it.courseCode.trim().uppercase() == normCode && normalizeComponentType(it.courseType) == normType }
            .forEach { cfg ->
                if (cfg.faculties.isEmpty()) {
                    activeFacultyIds.add("default_${cfg.defaultClassId}")
                } else {
                    cfg.faculties.forEach { f -> activeFacultyIds.add("${f.classId}_${f.erpId}") }
                }
            }

        settings.customClassTargets
            .filter { it.enabled && it.courseCode.trim().uppercase() == normCode && normalizeComponentType(it.courseType) == normType }
            .forEach { custom ->
                activeFacultyIds.add("${custom.classId}_${custom.erpId}")
            }

        return activeFacultyIds.size
    }

    fun hasMultipleFaculties(context: Context, courseCode: String, courseType: String): Boolean =
        countActiveFacultiesForCourse(load(context), courseCode, courseType) > 1

    private fun normalizeComponentType(rawType: String): String {
        val u = rawType.trim().uppercase()
        return when {
            u == "ETH" || u == "TH" || u.contains("THEORY") -> "THEORY"
            u == "ELA" || u == "LO" || u.contains("LAB") -> "LAB"
            else -> u
        }
    }

    private fun parseStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }
}