package com.auo.flex_compositor

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.auo.flex_compositor.pFilter.cMediaDecoder
import com.auo.flex_compositor.pFilter.cMediaEncoder
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pParse.cFlexDecoder
import com.auo.flex_compositor.pParse.cFlexDisplayView
import com.auo.flex_compositor.pParse.cFlexEncoder
import com.auo.flex_compositor.pParse.cFlexVirtualDisplay
import com.auo.flex_compositor.pParse.cParseFlexCompositor
import com.auo.flex_compositor.pParse.eElementType
import com.auo.flex_compositor.pSink.cDisplayView
import com.auo.flex_compositor.pSource.cVirtualDisplay

class BootStartService : Service() {

    private val m_VirtualDisplays = mutableListOf<cVirtualDisplay>()
    private val m_DisplayViews = mutableListOf<cDisplayView>()
    private val m_MediaDecoders = mutableListOf<cMediaDecoder>()
    private val m_MediaEncoders =  mutableListOf<cMediaEncoder>()
    private val m_tag = "BootStartService"
    private val m_dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var hideview = intent.getBooleanExtra("visible", false)
            setVisibility(hideview)
        }
    }


    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.auo.flex_compositor.UPDATE_DATA")
        registerReceiver(m_dataReceiver, filter, Context.RECEIVER_EXPORTED)
        ServiceStartForeground()
        generateElements()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BootStartService", "Running in foreground")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    fun ServiceStartForeground() {
        val notification = NotificationCompat.Builder(this, "channel_id")
            .setContentTitle("Starting Service")
            .setContentText("Running in foreground")
            .build()

        val channel = NotificationChannel(
            "channel_id", // must match builder
            "Boot Service Channel", // user-visible name
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        startForeground(1, notification) // 👈 Prevent crash
    }

    private fun generateElements(){
        val parse: cParseFlexCompositor = cParseFlexCompositor(this, "flexCompositor.ini")
        parse.parse()
        val all_elements = parse.getElements()
        for (element in all_elements) {
            when {
                element.type == eElementType.VIRTUALDISPLAY-> {
                    val Downcasting = element as cFlexVirtualDisplay
                    val virtualdisplay = cVirtualDisplay(this,Downcasting.name, Downcasting.id, Downcasting.size,Downcasting.app)
                    m_VirtualDisplays.add(virtualdisplay)
                }

                element.type == eElementType.STREAMDECODER-> {
                    val Downcasting = element as cFlexDecoder
                    val decoder =  cMediaDecoder(this, Downcasting.name, Downcasting.id, Downcasting.size, Downcasting.serverIP,Downcasting.serverPort,
                        Downcasting.codecType)
                    m_MediaDecoders.add(decoder)
                }
            }
        }


        for (element in all_elements) {
            when {
                element.type == eElementType.DISPLAYVIEW-> {
                    val Downcasting = element as cFlexDisplayView
                    if(Downcasting.source != null) {
                        for (i in 0 until Downcasting.source!!.size) {
                            val sourceID = Downcasting.source!![i]
                            val sourceElement = findSource(sourceID)

                            if(sourceElement != null) {
                                val displayView = cDisplayView(
                                    this,
                                    Downcasting.name,
                                    Downcasting.id,
                                    sourceElement,
                                    Downcasting.displayid,
                                    Downcasting.posSize[i],
                                    Downcasting.crop_texture[i],
                                    Downcasting.touchmapping[i],
                                    false,
                                )
                                m_DisplayViews.add(displayView)
                            }
                        }
                    }
                }
                element.type == eElementType.STREAMENCODER-> {
                    val Downcasting = element as cFlexEncoder
                    if(Downcasting.source != null) {
                        if (Downcasting.source!!.size > 0) {
                            val sourceID = Downcasting.source!![0]
                            val sourceElement = findSource(sourceID)
                            if(sourceElement != null) {
                                val encoder = cMediaEncoder(
                                    this,
                                    Downcasting.name,
                                    Downcasting.id,
                                    sourceElement,
                                    Downcasting.size,
                                    Downcasting.crop_texture,
                                    Downcasting.touchmapping,
                                    Downcasting.serverPort.toInt(),
                                    false,
                                    Downcasting.codecType
                                )
                                m_MediaEncoders.add(encoder)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findSource(sourceID: Int): iSurfaceSource?{
        val source = m_VirtualDisplays.find { it.e_id == sourceID }
        if(source == null){
            val source2 = m_MediaDecoders.find { it.e_id == sourceID }
            return source2
        }
        return source
    }

    private fun setVisibility(visible: Boolean){
        if(visible){
            for( element in m_DisplayViews){
                element.show()
            }
        }
        else{
            for( element in m_DisplayViews){
                element.hide()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for (element in m_DisplayViews) {
            element.destroyed()
        }
        for (element in m_MediaEncoders) {
            element.stopEncode()
        }
        for (element in m_MediaDecoders) {
            element.stopDecode()
        }
        for (element in m_VirtualDisplays) {
            element.destroyed()
        }
        Log.d(m_tag, "onDestroy")
        unregisterReceiver(m_dataReceiver)
        this.stopForeground(true)
    }


}