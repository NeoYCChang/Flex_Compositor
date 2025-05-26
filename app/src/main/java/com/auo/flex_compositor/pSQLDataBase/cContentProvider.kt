package com.auo.flex_compositor.pSQLDataBase

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

class cContentProvider : ContentProvider() {

    private lateinit var m_dataBae: cSQLDataBase
    private val AUTHORITY = "com.auo.flexCompositor.provider"
    val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/${cSQLDataBase.table_name}")
    override fun onCreate(): Boolean {
        m_dataBae = cSQLDataBase(context!!)
        return true
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val db = m_dataBae.readableDatabase
        return db.query(
            cSQLDataBase.table_name,
            projection, selection, selectionArgs, null, null, sortOrder
        )
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val db = m_dataBae.writableDatabase
        val id = db.insert(cSQLDataBase.table_name, null, values)
        context?.contentResolver?.notifyChange(uri, null)
        return uri
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = m_dataBae.writableDatabase
        return db.update(cSQLDataBase.table_name, values, selection, selectionArgs)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val db = m_dataBae.writableDatabase
        return db.delete(cSQLDataBase.table_name, selection, selectionArgs)
    }

    override fun getType(uri: Uri): String? {
        return "vnd.android.cursor.dir/vnd.$AUTHORITY.items"
    }
}