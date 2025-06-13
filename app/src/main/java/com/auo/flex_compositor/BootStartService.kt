package com.auo.flex_compositor

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.auo.flex_compositor.pCMDJson.cCmdThread
import com.auo.flex_compositor.pCMDJson.cTestSocketClient
import com.auo.flex_compositor.pCMDJson.jsonRequest
import com.auo.flex_compositor.pFilter.cMediaDecoder
import com.auo.flex_compositor.pFilter.cMediaEncoder
import com.auo.flex_compositor.pFilter.cViewSwitch
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pParse.cFlexDecoder
import com.auo.flex_compositor.pParse.cFlexDisplayView
import com.auo.flex_compositor.pParse.cFlexEncoder
import com.auo.flex_compositor.pParse.cFlexVirtualDisplay
import com.auo.flex_compositor.pParse.cParseFlexCompositor
import com.auo.flex_compositor.pParse.eElementType
import com.auo.flex_compositor.pSink.cDisplayView
import com.auo.flex_compositor.pSource.cVirtualDisplay
import com.auo.flex_compositor.pView.cContolButton
import kotlin.concurrent.thread

class BootStartService : Service() {

    private val m_VirtualDisplays = mutableListOf<cVirtualDisplay>()
    private val m_DisplayViews = mutableListOf<cDisplayView>()
    private val m_MediaDecoders = mutableListOf<cMediaDecoder>()
    private val m_MediaEncoders =  mutableListOf<cMediaEncoder>()
    private val m_Switchs =  mutableListOf<cViewSwitch>()
    private val m_ControlButoon = mutableListOf<cContolButton>()
    private var m_CmdThread: cCmdThread? = null
    private val m_tag = "BootStartService"
    private val m_dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            var visible = intent.getBooleanExtra("visible", false)
            var restart = intent.getBooleanExtra("restart", false)
            if(restart){
                reStart()
            }else {
                setVisibility(visible)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.auo.flex_compositor.UPDATE_DATA")
        registerReceiver(m_dataReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BootStartService", "Running in foreground")
        ServiceStartForeground()
        reStart()
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
                                    (Downcasting.name+"_$i"),
                                    Downcasting.id,
                                    sourceElement,
                                    Downcasting.displayid,
                                    Downcasting.posSize[i],
                                    Downcasting.crop_texture[i],
                                    Downcasting.touchmapping[i],
                                    Downcasting.dewarpParameters[i],
                                )
                                m_DisplayViews.add(displayView)
                            }
                        }
                    }
                    for (i in 0 until Downcasting.switch.size) {
                        val switch = Downcasting.switch[i]
                        if(switch.source != null){
                            val sourceList = mutableListOf<iSurfaceSource>()
                            for (j in 0 until switch.source!!.size) {
                                val sourceID = switch.source!![j]
                                val sourceElement = findSource(sourceID)
                                if(sourceElement != null) {
                                    sourceList.add(sourceElement)
                                }
                            }
                            if(sourceList.size > 0){
                                val viewSwitch: cViewSwitch = cViewSwitch(
                                    switch.name,
                                    switch.id, sourceList, switch.channel,
                                    switch.posSize,
                                    switch.crop_texture,
                                    switch.touchmapping,
                                    switch.dewarpParameters
                                )
                                val displayView = cDisplayView(
                                    this,
                                    (Downcasting.name+"_$i"),
                                    Downcasting.id,
                                    viewSwitch,
                                    Downcasting.displayid,
                                    switch.posSize[switch.channel[0]],
                                    switch.crop_texture[switch.channel[0]],
                                    switch.touchmapping[switch.channel[0]],
                                    switch.dewarpParameters[switch.channel[0]]
                                )
                                m_Switchs.add(viewSwitch)
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
                                    Downcasting.dewarpParameters,
                                    Downcasting.codecType
                                )
                                m_MediaEncoders.add(encoder)
                            }
                        }
                    }
                    else if(Downcasting.switch != null){
                        val switch = Downcasting.switch
                        val sourceList = mutableListOf<iSurfaceSource>()
                        for (j in 0 until switch!!.source!!.size) {
                            val sourceID = switch!!.source!![j]
                            val sourceElement = findSource(sourceID)
                            if(sourceElement != null) {
                                sourceList.add(sourceElement)
                            }
                        }
                        if(sourceList.size > 0){
                            val viewSwitch: cViewSwitch = cViewSwitch(
                                switch.name,
                                switch.id, sourceList, switch.channel,
                                switch.posSize,
                                switch.crop_texture,
                                switch.touchmapping,
                                switch.dewarpParameters
                            )
                            val encoder = cMediaEncoder(
                                this,
                                Downcasting.name,
                                Downcasting.id,
                                viewSwitch,
                                Downcasting.size,
                                switch.crop_texture[switch.channel[0]],
                                switch.touchmapping[switch.channel[0]],
                                Downcasting.serverPort.toInt(),
                                switch.dewarpParameters[switch.channel[0]],
                                Downcasting.codecType
                            )
                            m_Switchs.add(viewSwitch)
                            m_MediaEncoders.add(encoder)
                        }
                    }
                }
            }
        }
        startCMDThread()
        //val testSocketClient: cTestSocketClient = cTestSocketClient()
        createControlButton()

    }

    private fun startCMDThread(){
        m_CmdThread = cCmdThread()
        val callbackCmd = object : cCmdThread.callbackCmd{
            override fun onReceiveJson(request: jsonRequest) {
                Log.d(m_tag, "onReceiveJson")
                parseCMDSwitchJson(request)
            }
        }
        m_CmdThread!!.setCallbackCmd(callbackCmd)
        m_CmdThread!!.start()
    }

    private fun createControlButton(){
        val display_manager = getSystemService(DISPLAY_SERVICE) as DisplayManager

        for(display in display_manager.displays) {
            if (display !== null) {
                // flag = 4 : Private Display
                if (display.flags != 132) {
                    val controlButton = cContolButton(this, display)
                    val clickevent: () -> Unit = {
                        for (switch in m_Switchs) {
                            switch.setDefaultChannel()
                        }
                    }
                    controlButton.clickEventSubscribe(clickevent)
                    m_ControlButoon.add(controlButton)
                }
            }
        }
    }

    private fun parseCMDSwitchJson(request: jsonRequest){
        if(request.sourceSwitchers != null){
            for(sourceSwitcher in request.sourceSwitchers){
                if(sourceSwitcher.id != null && sourceSwitcher.channel != null) {
                    val jsonSwitchId = sourceSwitcher.id
                    val jsonSwitchChannel = sourceSwitcher.channel
                    for (switch in m_Switchs) {
                        if (jsonSwitchId == switch.e_id){
                            switch.setChannel(jsonSwitchChannel)
                            Log.d(m_tag,"${switch} setIndex(${jsonSwitchChannel})")
                            break
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

    private fun reStart(){
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
        for (control in m_ControlButoon) {
            control.destroyed()
        }
        m_CmdThread?.close()
        m_DisplayViews.clear()
        m_MediaEncoders.clear()
        m_MediaDecoders.clear()
        m_VirtualDisplays.clear()
        m_ControlButoon.clear()
        m_Switchs.clear()
        generateElements()
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
        for (control in m_ControlButoon) {
            control.destroyed()
        }
        m_CmdThread?.close()
        m_DisplayViews.clear()
        m_MediaEncoders.clear()
        m_MediaDecoders.clear()
        m_VirtualDisplays.clear()
        m_ControlButoon.clear()
        m_Switchs.clear()
        Log.d(m_tag, "onDestroy")
        unregisterReceiver(m_dataReceiver)
        this.stopForeground(true)
    }


}