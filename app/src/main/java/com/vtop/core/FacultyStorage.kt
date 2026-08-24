package com.vtop.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vtop.models.FacultyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FacultyStorage {

    private const val FILE_NAME = "faculty.json"

    private fun facultyFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    suspend fun loadFaculty(context: Context): List<FacultyEntity> = withContext(Dispatchers.IO) {
        try {
            val file = facultyFile(context)
            if (!file.exists()) {
                return@withContext emptyList()
            }
            val json = file.readText(Charsets.UTF_8)
            if (json.isBlank()) {
                return@withContext emptyList()
            }
            Gson().fromJson(
                json,
                object : TypeToken<List<FacultyEntity>>() {}.type
            ) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveFaculty(context: Context, faculty: List<FacultyEntity>) = withContext(Dispatchers.IO) {
        val json = Gson().toJson(faculty)
        facultyFile(context).writeText(json, Charsets.UTF_8)
    }

    suspend fun clearFaculty(context: Context) = withContext(Dispatchers.IO) {
        facultyFile(context).delete()
    }

    suspend fun hasFaculty(context: Context): Boolean = withContext(Dispatchers.IO) {
        facultyFile(context).exists()
    }
}