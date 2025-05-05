package com.auo.flex_compositor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver   : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Handle the received broadcast here
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Received BOOT_COMPLETED")
            //val serviceIntent = Intent(context, BootStartService::class.java)
            //ContextCompat.startForegroundService(context, serviceIntent)

            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(activityIntent)
        }
    }
}