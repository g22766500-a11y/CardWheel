package com.example.cardwheel

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.nio.file.Files

/** Keep database paths below the Windows native SQLite path limit on CI hosts. */
internal class DatabaseTestContext(base: Context) : ContextWrapper(base) {
    private val directory = Files.createTempDirectory("cw-db-").toFile()
    override fun getApplicationContext(): Context = this
    override fun getDatabasePath(name: String): File = File(directory, name)
    override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).absolutePath, factory, handler)
}
