package com.vtop.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vtop.models.FacultyEntity
import java.io.File

object FacultyStorage {

    private const val FILE_NAME = "faculty.json"

    /**
     * Returns the app's working faculty.json file.
     */
    private fun facultyFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    /**
     * Copies the bundled assets/faculty.json into the app's internal
     * storage if it doesn't already exist.
     *
     * Call this before loading faculty data.
     */
    fun ensureFacultyExists(context: Context) {
        val file = facultyFile(context)

        if (file.exists()) return

        context.assets.open(FILE_NAME).use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Reads the working faculty.json from internal storage.
     */
    fun loadFaculty(context: Context): List<FacultyEntity> {
        ensureFacultyExists(context)

        return try {
            val json = facultyFile(context).readText(Charsets.UTF_8)

            Gson().fromJson(
                json,
                object : TypeToken<List<FacultyEntity>>() {}.type
            ) ?: emptyList()

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Saves a freshly scraped faculty list.
     */
    fun saveFaculty(
        context: Context,
        faculty: List<FacultyEntity>
    ) {
        val json = Gson().toJson(faculty)
        facultyFile(context).writeText(json, Charsets.UTF_8)
    }

    /**
     * Deletes the cached faculty file.
     * Mainly useful during development/testing.
     */
    fun clearFaculty(context: Context) {
        facultyFile(context).delete()
    }

    /**
     * Returns true if a cached faculty.json already exists.
     */
    fun hasFaculty(context: Context): Boolean {
        return facultyFile(context).exists()
    }
}