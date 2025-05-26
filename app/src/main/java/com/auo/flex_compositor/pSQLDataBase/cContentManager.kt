package com.auo.flex_compositor.pSQLDataBase

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.util.Log

class cContentManager {

    private lateinit var m_contentResolver: ContentResolver

    companion object {
        private val m_contentProvider = cContentProvider()
    }

    constructor(context: Context){
        m_contentResolver = context.contentResolver
//        val values = ContentValues().apply {
//            put("commandLine", ";screenName=interface-number,resolution,attrib(value…)")
//        }

//        val rowsDeleted = m_contentResolver.delete(
//            m_contentProvider.CONTENT_URI,
//            null,
//            null
//        )

//        val rowsDeleted = m_contentResolver.delete(
//            m_contentProvider.CONTENT_URI,
//            "id = ?",
//            arrayOf("1")
//        )

//        val rowsUpdated = m_contentResolver.update(
//            m_contentProvider.CONTENT_URI,
//            values,
//            "id = ?",
//            arrayOf("1")
//        )

//        var uri = m_contentResolver.insert(m_contentProvider.CONTENT_URI, values)
//        uri = m_contentResolver.insert(m_contentProvider.CONTENT_URI, values)


    }

    fun getCommandLines(): List<String>{
        val list: MutableList<String> = mutableListOf()
        val cursor = m_contentResolver.query(m_contentProvider.CONTENT_URI, null, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                val commandLine = it.getString(it.getColumnIndexOrThrow("commandLine"))
                list.add(commandLine)
            }
        }
        return list.toList()
    }

    fun updateCommandLines(commandLines: List<String>) {
        m_contentResolver.delete(
            m_contentProvider.CONTENT_URI,
            null,
            null
        )
        for(commandLine in commandLines){
            val values = ContentValues().apply {
                put("commandLine", commandLine)
            }
            m_contentResolver.insert(m_contentProvider.CONTENT_URI, values)
        }
    }
}