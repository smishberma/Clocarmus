package com.example.clocarmus.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.clocarmus.ui.player.PlaylistLibrary
import java.io.*

/** Accessed on the repository's IO dispatcher, under its mutex. Schema changes require migration. */
internal class LibraryDatabase(context: Context) : SQLiteOpenHelper(context, "local-library.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE directories (id TEXT PRIMARY KEY, payload BLOB NOT NULL)")
        db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY, location TEXT NOT NULL UNIQUE, payload BLOB NOT NULL)")
        db.execSQL("CREATE TABLE state (id INTEGER PRIMARY KEY CHECK(id=1), version INTEGER NOT NULL, playlists BLOB NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Library migration required")
    fun records(): List<LibraryRecord> = readableDatabase.rawQuery("SELECT payload FROM tracks", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(decode<LibraryRecord>(cursor.getBlob(0))) }
    }
    fun directories(): List<LibraryDirectory> = readableDatabase.rawQuery("SELECT payload FROM directories", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(decode<LibraryDirectory>(cursor.getBlob(0))) }
    }
    fun playlists(): PlaylistLibrary? = readableDatabase.rawQuery("SELECT playlists FROM state WHERE id=1", null).use {
        if (it.moveToFirst()) decode<PlaylistLibrary>(it.getBlob(0)) else null
    }
    fun version(): Long = readableDatabase.rawQuery("SELECT version FROM state WHERE id=1", null).use { if (it.moveToFirst()) it.getLong(0) else 0 }
    fun commit(records: Collection<LibraryRecord>, removed: Collection<String>, playlists: PlaylistLibrary, directories: List<LibraryDirectory>? = null): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            directories?.let { entries ->
                db.delete("directories", null, null)
                entries.forEach { directory -> db.insertOrThrow("directories", null, ContentValues().apply {
                    put("id", directory.id); put("payload", encode(directory))
                }) }
            }
            removed.forEach { db.delete("tracks", "id=?", arrayOf(it)) }
            records.forEach { record ->
                db.insertWithOnConflict("tracks", null, ContentValues().apply {
                    put("id", record.id); put("location", record.location); put("payload", encode(record))
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            val next = version() + 1
            db.insertWithOnConflict("state", null, ContentValues().apply {
                put("id", 1); put("version", next); put("playlists", encode(playlists))
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            return next
        } finally { db.endTransaction() }
    }
}
private fun encode(value: Serializable): ByteArray = ByteArrayOutputStream().use { bytes ->
    ObjectOutputStream(bytes).use { it.writeObject(value) }; bytes.toByteArray()
}
@Suppress("UNCHECKED_CAST")
private fun <T> decode(bytes: ByteArray): T = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as T }
