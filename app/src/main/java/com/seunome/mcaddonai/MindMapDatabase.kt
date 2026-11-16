package com.seunome.mcaddonai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class FileTask(val id: Int, val filePath: String, val fileType: String)

class MindMapDatabase(context: Context) : SQLiteOpenHelper(context, "mindmap_engine.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE project_files (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT UNIQUE,
                file_type TEXT,
                status TEXT,
                route_summary TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE learned_knowledge (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                addon_source TEXT,
                blueprint_data TEXT
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun clearCurrentProject() {
        writableDatabase.execSQL("DELETE FROM project_files")
    }

    fun registerFileTask(path: String, type: String) {
        val values = ContentValues().apply {
            put("file_path", path)
            put("file_type", type)
            put("status", "PENDING")
            put("route_summary", "")
        }
        writableDatabase.insertWithOnConflict("project_files", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun getNextPendingTask(): FileTask? {
        val cursor = readableDatabase.rawQuery(
            "SELECT id, file_path, file_type FROM project_files WHERE status = 'PENDING' ORDER BY id ASC LIMIT 1",
            null
        )
        var task: FileTask? = null
        if (cursor.moveToFirst()) {
            task = FileTask(cursor.getInt(0), cursor.getString(1), cursor.getString(2))
        }
        cursor.close()
        return task
    }

    fun markAsGenerated(id: Int, summary: String) {
        val values = ContentValues().apply {
            put("status", "GENERATED")
            put("route_summary", summary)
        }
        writableDatabase.update("project_files", values, "id = ?", arrayOf(id.toString()))
    }

    fun getTraveledRouteContext(): String {
        val cursor = readableDatabase.rawQuery(
            "SELECT file_path, route_summary FROM project_files WHERE status != 'PENDING' AND route_summary != ''",
            null
        )
        val sb = StringBuilder()
        while (cursor.moveToNext()) {
            sb.append("- ${cursor.getString(0)}: ${cursor.getString(1)}\n")
        }
        cursor.close()
        return sb.toString()
    }

    fun saveLearnedAddon(sourceName: String, summary: String) {
        val values = ContentValues().apply {
            put("addon_source", sourceName)
            put("blueprint_data", summary)
        }
        writableDatabase.insert("learned_knowledge", null, values)
    }

    fun getLearnedPatterns(): String {
        val cursor = readableDatabase.rawQuery("SELECT addon_source, blueprint_data FROM learned_knowledge", null)
        val sb = StringBuilder()
        while (cursor.moveToNext()) {
            sb.append("Fonte [${cursor.getString(0)}]: ${cursor.getString(1)}\n")
        }
        cursor.close()
        return sb.toString()
    }
}
