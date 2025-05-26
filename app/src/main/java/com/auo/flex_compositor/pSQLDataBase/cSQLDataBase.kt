package com.auo.flex_compositor.pSQLDataBase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class cSQLDataBase (context: Context) :
    SQLiteOpenHelper(context, name, CursorFactory, version) {
    companion object {
        val name = "flexCompositor.db"
        val CursorFactory = null
        val version = 1
        val table_name = "flexCompositor"
    }

    // You can override the onCreate and onUpgrade methods to initialize and upgrade the database
    override fun onCreate(db: SQLiteDatabase?) {
        // Create your tables here
        val createTableSQL = "CREATE TABLE IF NOT EXISTS flexCompositor (id INTEGER PRIMARY KEY, commandLine TEXT)"
        db?.execSQL(createTableSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Program for upgrading the database
    }
}