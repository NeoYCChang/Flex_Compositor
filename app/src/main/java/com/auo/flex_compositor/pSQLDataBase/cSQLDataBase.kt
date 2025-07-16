package com.auo.flex_compositor.pSQLDataBase

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

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

//        val commandLines = listOf(
//            ";8295 flexCompositor.ini",
//            "",
//            "[screen]",
//            "mainDisplay=virtual-0,1920x1080,\\",
//            " app(com.AUO.PHUD/com.epicgames.unreal.GameActivity)",
//            "gameDisplay=virtual-1,3200x2000,\\",
//            " app(com.YourCompany.QuickSample/com.epicgames.unreal.GameActivity)",
//            "youtubeDisplay=virtual-2,3200x2000,\\",
//            " app(com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.MainActivity)",
//            "spotifyDisplay=virtual-3,3200x2000,\\",
//            " app(com.spotify.music/com.spotify.music.MainActivity)",
//            "orinStream0=stream-0,1920x1080,\\",
//            " server(192.168.1.6,50000),\\",
//            " codec(h264)",
//            "orinStream1=stream-1,1920x1080,\\",
//            " server(192.168.1.6,50001),\\",
//            " codec(h264)",
//            "rcarStream0=stream-2,1920x1080,\\",
//            " server(192.168.1.3,50000),\\",
//            " codec(h264)",
//            "rcarStream1=stream-3,1920x1080,\\",
//            " server(192.168.1.3,50001),\\",
//            " codec(h264)",
//            "dpDisplay0=dp-0,1920x1080",
//            "dpDisplay2=dp-2,5500x650",
//            "dpDisplay3=dp-3,3200x2000",
//            "",
//            "[mapping]",
//            "; mainDisplay(0,227,1365,853)->dpDisplay3(0,0,3200,2000),switch(1,1)",
//            "; youtubeDisplay(0,0,3200,2000)->dpDisplay3(0,0,3200,2000),switch(1,3)",
//            "; spotifyDisplay(0,0,3200,2000)->dpDisplay3(0,0,3200,2000),switch(1,4)",
//            "; gameDisplay(0,0,3200,2000)->dpDisplay3(0,0,3200,2000),switch(1,5)",
//            "; orinStream1(0,0,1920,1080)->dpDisplay3(0,0,3200,2000),switch(1,2)",
//            "; mainDisplay(0,0,1920,227)->dpDisplay2(0,0,5500,650),switch(0,1)",
//            "; orinStream0(0,0,1920,1080)->dpDisplay2(0,0,5500,650),switch(0,2)",
//            "mainDisplay(0,0,1920,1080)->dpDisplay0(0,0,1920,1080),switch(1,1)",
//            "youtubeDisplay(0,0,3200,2000)->dpDisplay0(0,0,1920,1080),switch(1,3)",
//            "spotifyDisplay(0,0,3200,2000)->dpDisplay0(0,0,1920,1080),switch(1,4)",
//            "gameDisplay(0,0,3200,2000)->dpDisplay0(0,0,1920,1080),switch(1,5)",
//            "orinStream1(0,0,1920,1080)->dpDisplay0(0,0,1920,1080),switch(1,2)",
//            "mainDisplay(1365,227,555,426)->rcarStream0(0,0,1920,1080),switch(2,1)",
//            "youtubeDisplay(0,0,3200,2000)->rcarStream0(0,0,1920,1080),switch(2,2)",
//            "spotifyDisplay(0,0,3200,2000)->rcarStream0(0,0,1920,1080),switch(2,3)",
//            "gameDisplay(0,0,3200,1000)->rcarStream0(0,0,1920,1080),switch(2,4)",
//            "mainDisplay(1365,653,555,427)->rcarStream1(0,0,1920,1080),switch(3,1)",
//            "youtubeDisplay(0,0,3200,2000)->rcarStream1(0,0,1920,1080),switch(3,2)",
//            "spotifyDisplay(0,0,3200,2000)->rcarStream1(0,0,1920,1080),switch(3,3)",
//            "gameDisplay(0,1000,3200,1000)->rcarStream1(0,0,1920,1080),switch(3,4)"
//        )
//        val insertSQL = "INSERT INTO flexCompositor (commandLine) VALUES (?)"
//        commandLines.forEach { cmd ->
//            db?.execSQL(insertSQL, arrayOf(cmd))
//        }
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // Program for upgrading the database
    }
}