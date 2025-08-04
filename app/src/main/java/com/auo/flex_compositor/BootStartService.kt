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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.auo.flex_compositor.pCMDJson.CmdProtocol
import com.auo.flex_compositor.pCMDJson.GetEnv
import com.auo.flex_compositor.pCMDJson.MuxStatus
import com.auo.flex_compositor.pCMDJson.SourceMux
import com.auo.flex_compositor.pCMDJson.SourceSwitcher
import com.auo.flex_compositor.pCMDJson.SwitcherStatus
import com.auo.flex_compositor.pCMDJson.cCmdNotify
import com.auo.flex_compositor.pCMDJson.cCmdReqRes
import com.auo.flex_compositor.pCMDJson.cTestSocketClient
import com.auo.flex_compositor.pCMDJson.jsonRequest
import com.auo.flex_compositor.pCMDJson.jsonResponse
import com.auo.flex_compositor.pCMDJson.jsonStatus
import com.auo.flex_compositor.pFilter.cMediaDecoder
import com.auo.flex_compositor.pFilter.cMediaEncoder
import com.auo.flex_compositor.pFilter.cViewMux
import com.auo.flex_compositor.pFilter.cViewSwitch
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pNtpTimeHelper.NtpTimeHelper
import com.auo.flex_compositor.pParse.cElementType
import com.auo.flex_compositor.pParse.cFlexDecoder
import com.auo.flex_compositor.pParse.cFlexDisplayView
import com.auo.flex_compositor.pParse.cFlexEncoder
import com.auo.flex_compositor.pParse.cFlexMux
import com.auo.flex_compositor.pParse.cFlexSwitch
import com.auo.flex_compositor.pParse.cFlexVirtualDisplay
import com.auo.flex_compositor.pParse.cParseFlexCompositor
import com.auo.flex_compositor.pParse.eElementType
import com.auo.flex_compositor.pSink.cDisplayView
import com.auo.flex_compositor.pSource.cVirtualDisplay
import com.auo.flex_compositor.pView.cContolButton


class BootStartService : Service() {

    private val m_VirtualDisplays = mutableListOf<cVirtualDisplay>()
    private val m_DisplayViews = mutableListOf<cDisplayView>()
    private val m_MediaDecoders = mutableListOf<cMediaDecoder>()
    private val m_MediaEncoders =  mutableListOf<cMediaEncoder>()
    private val m_Switches =  mutableListOf<cViewSwitch>()
    private val m_Muxs =  mutableListOf<cViewMux>()
    private val m_ControlButoon = mutableListOf<cContolButton>()
    private var m_envMap: Map<String, String> = mapOf<String, String>()
    private var m_CmdReqRes: cCmdReqRes? = null
    private var m_CmdNotify: cCmdNotify? = null
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
//        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        am.setTimeZone("Asia/Taipei")
        syncSystemTime()
        reStart()
        return START_NOT_STICKY
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

    private fun syncSystemTime(){
        val ntpTimeHelper: NtpTimeHelper = NtpTimeHelper(this)
        ntpTimeHelper.syncSystemTimeWithNtp()
    }

    private fun generateElements(){
        val parse: cParseFlexCompositor = cParseFlexCompositor(this, "flexCompositor.ini")
        parse.parse()
        m_envMap = parse.getEnvMap()
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
                }
            }
        }
        val switches = generateSwitch(all_elements, parse.getSwitches(), m_DisplayViews, m_MediaEncoders)
        m_Switches.addAll(switches.filterNotNull())
        val muxs = generateMux(all_elements, parse.getMuxs(), m_DisplayViews, m_MediaEncoders)
        m_Muxs.addAll(muxs)
    }
    private fun generateSwitch(elements: MutableList<cElementType>, switches: MutableList<cFlexSwitch>,
                               displayview: MutableList<cDisplayView>, encoders: MutableList<cMediaEncoder>) : MutableList<cViewSwitch?>{
        val out_switches =  mutableListOf<cViewSwitch?>()
        for (switch in switches) {
            if(switch.srcParm.size != 0){
                val element: cElementType? = findSink(switch.sink, elements)
                if(element != null) {
                    when {
                        element.type == eElementType.DISPLAYVIEW -> {
                            val Downcasting = element as cFlexDisplayView
                            val switchesParm = mutableListOf<cViewSwitch.viewSwitchParm>()
                            for (j in 0 until switch.srcParm.size) {
                                val sourceID = switch.srcParm[j].source
                                val sourceElement = findSource(sourceID)
                                val switchParm = cViewSwitch.viewSwitchParm(sourceElement, switch.srcParm[j].channel,
                                    switch.srcParm[j].crop_texture,switch.srcParm[j].touchmapping,switch.srcParm[j].dewarpParameters)
                                switchesParm.add(switchParm)
                            }
                            if(switchesParm.size > 0){
                                val viewSwitch: cViewSwitch = cViewSwitch(
                                    switch.name,
                                    switch.id, switchesParm
                                )
                                val firstChannel = viewSwitch.getChannelIndex(switchesParm[0].channel)
                                val displayView = cDisplayView(
                                    this,
                                    (Downcasting.name+"_switch_${switch.id}"),
                                    Downcasting.id,
                                    viewSwitch,
                                    Downcasting.displayid,
                                    switch.posSize,
                                    switchesParm[0].crop_texture,
                                    switchesParm[0].touchMapping,
                                    switchesParm[0].dewarpParameters
                                )
                                createControlButton(viewSwitch, Downcasting.displayid)
                                out_switches.add(viewSwitch)
                                displayview.add(displayView)
                            }
                        }
                        element.type == eElementType.STREAMENCODER-> {
                            val Downcasting = element as cFlexEncoder
                            val switchesParm = mutableListOf<cViewSwitch.viewSwitchParm>()
                            for (j in 0 until switch.srcParm.size) {
                                val sourceID = switch.srcParm[j].source
                                val sourceElement = findSource(sourceID)
                                val switchParm = cViewSwitch.viewSwitchParm(sourceElement, switch.srcParm[j].channel,
                                    switch.srcParm[j].crop_texture,switch.srcParm[j].touchmapping,switch.srcParm[j].dewarpParameters)
                                switchesParm.add(switchParm)
                            }

                            if(switchesParm.size > 0){
                                val viewSwitch: cViewSwitch = cViewSwitch(
                                    switch.name,
                                    switch.id, switchesParm
                                )
                                val firstChannel = viewSwitch.getChannelIndex(switchesParm[0].channel)
                                val encoder = cMediaEncoder(
                                    this,
                                    (Downcasting.name+"_switch_${switch.id}"),
                                    Downcasting.id,
                                    viewSwitch,
                                    Downcasting.size,
                                    switchesParm[0].crop_texture,
                                    switchesParm[0].touchMapping,
                                    Downcasting.serverPort.toInt(),
                                    switchesParm[0].dewarpParameters,
                                    Downcasting.codecType
                                )
                                out_switches.add(viewSwitch)
                                encoders.add(encoder)
                            }
                        }
                    }
                }
                else{
                    out_switches.add(null)
                }
            }
        }
        return out_switches
    }

    private fun generateMux(elements: MutableList<cElementType>, muxs: MutableList<cFlexMux>,
                            displayview: MutableList<cDisplayView>, encoders: MutableList<cMediaEncoder>) : MutableList<cViewMux>{
        val out_muxs =  mutableListOf<cViewMux>()
        for (mux in muxs) {
            val switches = generateSwitch(elements, mux.switch, displayview, encoders)
            val viewMux: cViewMux = cViewMux(mux.name, mux.id, switches, mux.channels)
            out_muxs.add(viewMux)
        }
        return out_muxs
    }

    private fun startCMDThread(){
        m_CmdReqRes = cCmdReqRes()
        val callbackCmd = object : cCmdReqRes.callbackCmd{
            override fun onReceiveJson(request: jsonRequest): jsonResponse {
                Log.d(m_tag, "onReceiveJson")
                return parseCMDSwitchJson(request)
            }
        }
        m_CmdReqRes!!.setCallbackCmd(callbackCmd)
        m_CmdReqRes!!.start()

        m_CmdNotify = cCmdNotify()
        m_CmdNotify!!.start()
    }

    private fun createControlButton(switch: cViewSwitch, displayID: Int){
        val display_manager = getSystemService(DISPLAY_SERVICE) as DisplayManager

        for(display in display_manager.displays) {
            if (display !== null) {
                // flag = 4 : Private Display
                if (displayID == display.displayId && (display.flags == 131 || display.flags == 139)) {
                    val controlButton = cContolButton(this, display)
                    val clickevent: () -> Unit = {
                        m_CmdNotify?.sendBackHome(switch)
                        switch.switchToDefaultChannel()
                    }
                    controlButton.clickEventSubscribe(clickevent)
                    m_ControlButoon.add(controlButton)
                    break
                }
            }
        }
    }

    private fun hideControlButton(){
        val mainHandler: Handler = Handler(Looper.getMainLooper())

        mainHandler.post(Runnable {
            // This runs on the main thread, so safe to update UI via Activity callback or broadcast
            for (btn in m_ControlButoon) {
                btn.hide()
            }
        })

    }

    private fun showControlButton(){
        val mainHandler: Handler = Handler(Looper.getMainLooper())

        mainHandler.post(Runnable {
            // This runs on the main thread, so safe to update UI via Activity callback or broadcast
            for (btn in m_ControlButoon) {
                btn.show()
            }
        })

    }

    private fun parseCMDSwitchJson(request: jsonRequest): jsonResponse {
        val response: jsonResponse = jsonResponse("ok")
        if(request.sourceSwitchers != null){
            parseSourceSwitcher(request.sourceSwitchers, response)
        }
        if(request.sourceMuxs != null){
            parseSourceMux(request.sourceMuxs, response)
        }
        if(request.getEnv != null){
            parseGetEnv(request.getEnv, response)
        }
        return  response
    }

    private fun parseSourceSwitcher(sourceSwitchers: List<SourceSwitcher>, response: jsonResponse){
        val statues: MutableList<jsonStatus> = mutableListOf<jsonStatus>()
        for(sourceSwitcher in sourceSwitchers){
            var id_exist = false
            if(sourceSwitcher.id != null && sourceSwitcher.channel != null) {
                val jsonSwitchId = sourceSwitcher.id
                val jsonSwitchChannel = sourceSwitcher.channel

                for (switch in m_Switches) {
                    if (jsonSwitchId == switch.e_id){
                        id_exist = true
                        val success = switch.switchToChannel(jsonSwitchChannel)
                        if(success){
                            statues.add(jsonStatus(SwitcherStatus.OK.status))
                        }
                        else{
                            statues.add(jsonStatus(SwitcherStatus.CHANNEL_OUT_OF_RANGE.status))
                        }
                        if(sourceSwitcher.homeSourceChannel != null){
                            switch.setDefaultChannel(sourceSwitcher.homeSourceChannel)
                        }
                        if(sourceSwitcher.homeStyle != null){
                            if(sourceSwitcher.homeStyle == 1){
                                hideControlButton()
                            }
                            else if(sourceSwitcher.homeStyle == 0){
                                showControlButton()
                            }
                        }
                        Log.d(m_tag,"${switch} setIndex(${jsonSwitchChannel})")
                        break
                    }
                }
            }
            if(!id_exist) {
                statues.add(jsonStatus(SwitcherStatus.ID_DOSE_NOT_EXIST.status))
            }
        }
        if(response.sourceSwitchers == null){
            response.sourceSwitchers = statues
        }
    }

    private fun parseSourceMux(sourceMuxs: List<SourceMux>, response: jsonResponse){
        val statues: MutableList<jsonStatus> = mutableListOf<jsonStatus>()
        for(sourceMux in sourceMuxs){
            var id_exist = false
            if(sourceMux.id != null && sourceMux.sinkChannel != null && sourceMux.sourceChannel != null) {
                val jsonMuxId = sourceMux.id
                val jsonSwitchSinkChannel = sourceMux.sinkChannel
                val jsonSwitchSourceChannel = sourceMux.sourceChannel
                for (mux in m_Muxs) {
                    if (jsonMuxId == mux.e_id){
                        id_exist = true
                        val success = mux.switchMux(jsonSwitchSourceChannel, jsonSwitchSinkChannel)
                        if(success){
                            statues.add(jsonStatus(MuxStatus.OK.status))
                        }
                        else{
                            statues.add(jsonStatus(MuxStatus.CHANNEL_OUT_OF_RANGE.status))
                        }
                        if(sourceMux.homeSourceChannel != null){
                            mux.setDefaultChannel(sourceMux.homeSourceChannel, jsonSwitchSinkChannel)
                        }
                        if(sourceMux.homeStyle != null){
                            if(sourceMux.homeStyle == 1){
                                hideControlButton()
                            }
                            else if(sourceMux.homeStyle == 0){
                                showControlButton()
                            }
                        }
                        break
                    }
                }
            }
            if(!id_exist) {
                statues.add(jsonStatus(MuxStatus.ID_DOSE_NOT_EXIST.status))
            }
        }
        if(response.sourceMuxs == null){
            response.sourceMuxs = statues
        }
    }

    private fun parseGetEnv(getEnv: List<GetEnv>, response: jsonResponse){
        val envMap: MutableMap<String, String> = mutableMapOf<String, String>()
        for(env in getEnv){
            if(env.obj != null) {
                if (env.obj.equals("all", ignoreCase = true)) {
                    for ((key, value) in m_envMap) {
                        envMap[key] = value
                    }
                    break
                }
                if(m_envMap.containsKey(env.obj)){
                    envMap[env.obj] = m_envMap[env.obj]!!
                }
                else{
                    envMap[env.obj] = "null"
                }
            }
        }
        if(response.getEnv == null){
            response.getEnv = envMap
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

    private fun findSink(sinkID: Int, elements: MutableList<cElementType>): cElementType?{
        val sink = elements.find { it.id == sinkID }
        return sink
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
        m_CmdReqRes?.close()
        m_CmdNotify?.close()
        m_DisplayViews.clear()
        m_MediaEncoders.clear()
        m_MediaDecoders.clear()
        m_VirtualDisplays.clear()
        m_ControlButoon.clear()
        m_Switches.clear()
        m_Muxs.clear()
        parseData_generate()
    }

    private fun parseData_generate(){
        generateElements()
        startCMDThread()
        //createControlButton()
        //val testSocketClient: cTestSocketClient = cTestSocketClient()
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
        m_CmdReqRes?.close()
        m_CmdNotify?.close()
        m_DisplayViews.clear()
        m_MediaEncoders.clear()
        m_MediaDecoders.clear()
        m_VirtualDisplays.clear()
        m_ControlButoon.clear()
        m_Switches.clear()
        m_Muxs.clear()
        Log.d(m_tag, "onDestroy")
        unregisterReceiver(m_dataReceiver)
        this.stopForeground(true)
    }


}