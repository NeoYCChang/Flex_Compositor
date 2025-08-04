package com.auo.flex_compositor.pSource

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Service.DISPLAY_SERVICE
import android.app.Service.INPUT_SERVICE
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import android.view.Surface
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pEGLFunction.StaticVariable
import com.auo.flex_compositor.pInterface.SerializablePointerCoords
import com.auo.flex_compositor.pInterface.SerializablePointerProperties
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.vSize
import com.auo.flex_compositor.pView.cSurfaceTexture
import java.lang.reflect.Method
import java.util.concurrent.locks.ReentrantLock
import android.opengl.EGLContext
import kotlin.concurrent.withLock

class cVirtualDisplay(override val e_name: String,override val e_id: Int): iSurfaceSource {

    companion object {
        private val m_tag = "cVirtualDisplay"
    }

    private var m_virtual_display: VirtualDisplay? = null
    private val m_eglcontext: EGLContext? = StaticVariable.public_eglcontext
    private lateinit var m_SurfaceTexture: cSurfaceTexture
    private var m_Surface: Surface? = null
    private var m_injectInputEventMethod : Method? = null
    private var m_motionSetDisplayIdMethod : Method? = null
    private var m_inputManager: InputManager? = null
    private var m_appName: String? = ""
    private var m_appisrunning: Boolean = false
    private val m_touchDevices = mutableMapOf<String, MutableMap<Int, Int>>()
    private var m_cMotionEvent: cMotionEvent? = null
    private var m_downTime: Long = 0
    private var m_eventTime: Long = 0
    private val m_MotionLock = ReentrantLock()
    private val m_renderTriggered = mutableListOf<(cSurfaceTexture) -> Unit>()
    private var m_package_name: String = ""
    private var m_context: Context? = null


    constructor(context: Context, name: String, id: Int, size: vSize, appName: String?) : this(name, id)  {
        val textureid: Int = EGLRender.createOESTextureObject()
        m_SurfaceTexture = cSurfaceTexture(textureid)
        m_SurfaceTexture.setTriggerRender(::onTriggerRenderCallback)
        m_SurfaceTexture.setDefaultBufferSize(size.width, size.height)
        m_Surface = Surface(m_SurfaceTexture)
        m_appName = appName
        m_context = context

        val virtualDisplayCallback: VirtualDisplay.Callback = object : VirtualDisplay.Callback() {
            override fun onPaused() {
                super.onPaused()
                Log.d(m_tag, "VirtualDisplay $e_name : Paused")
            }

            override fun onResumed() {
                super.onResumed()
                Log.d(m_tag, "VirtualDisplay $e_name : Resumed")
            }

            override fun onStopped() {
                super.onStopped()
                Log.d(m_tag, "VirtualDisplay $e_name : Stopped")
            }
        }

        val display_manager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
        @SuppressLint("WrongConstant")
        m_virtual_display = display_manager.createVirtualDisplay(
            name,
            size.width,
            size.height,
            240,
            m_Surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE or
            0x100 or //virtual displays will always destroy their content on removal
            0x400,  //Indicates that the display is trusted
            virtualDisplayCallback,
            null
        )
        Log.d(m_tag, "Create a Virtual Display {displayId: ${m_virtual_display!!.display.displayId} textureid: $textureid  flag: ${m_virtual_display!!.display.flags}}")
        val displayContext: Context = context.createDisplayContext(m_virtual_display!!.display)
        if(m_appName != null) {
            val app_split = m_appName!!.split('/')
            if(app_split.size == 2) {

                m_package_name = app_split[0]
                val activity_path = app_split[1]
                stopAppByForce(context, m_package_name)
//                try {
//                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop ${m_package_name}"))
//                    process.waitFor()
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//                val process = Runtime.getRuntime().exec("am force-stop ${m_package_name}")
//                process.waitFor()
                val intent = Intent()
                intent.setFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
                val comp = ComponentName(m_package_name, activity_path)
                intent.setComponent(comp);
                val options: ActivityOptions = ActivityOptions.makeBasic()
                // Try to resolve the intent to see if there's a matching activity
                options.launchDisplayId =
                    m_virtual_display!!.display.displayId  // Here, fill in the DisplayId you want to specify.
                try {
                    displayContext.startActivity(intent, options.toBundle())
                    m_appisrunning = true
                }catch (e: ActivityNotFoundException){
                    Log.e(m_tag,"The APP ${m_appName} doesn't exist.")
                }

            }
        }
        // Get the InputManager class
        val inputManagerClass = Class.forName("android.hardware.input.InputManager")
        // Get the Method object for the injectInputEvent method
        m_injectInputEventMethod = inputManagerClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType
        )
        // Get the MotionEvent class
        val motionEventClass = MotionEvent::class.java
        m_motionSetDisplayIdMethod = motionEventClass.getMethod(
            "setDisplayId",
            Int::class.java      // DisplayID
        )
        m_inputManager = displayContext.getSystemService(INPUT_SERVICE) as InputManager
    }

    fun destroyed(){
        stopAppByForce(m_context, m_package_name)

        m_SurfaceTexture.setTriggerRender(null)

        synchronized(m_renderTriggered) {
            m_renderTriggered.clear()
        }

        // Detach Surface
        m_virtual_display?.surface = null

        m_SurfaceTexture.release()

        m_Surface?.release()

        m_virtual_display?.release()
        m_virtual_display = null
    }

    /**
     * Injects a MotionEvent into the input event stream by setting its display ID and invoking the input manager.
     *
     * This method uses reflection to access the private `setDisplayId` method of the `MotionEvent` class
     * and injects the MotionEvent into the input manager, simulating a touch event with a specified display ID.
     *
     * @param motionEvent the MotionEvent to be injected, which represents a touch or input event.
     * @param displayid the ID of the display to associate the MotionEvent with, used to specify which screen the event is for.
     */
    override fun injectMotionEvent(cmotionEvent: cMotionEvent) {
        m_MotionLock.withLock {
            if (m_motionSetDisplayIdMethod == null) {
                // Get the MotionEvent class
                val motionEventClass = MotionEvent::class.java
                m_motionSetDisplayIdMethod = motionEventClass.getMethod(
                    "setDisplayId",
                    Int::class.java      // DisplayID
                )
            }
            if (m_appisrunning && cmotionEvent.start == com.auo.flex_compositor.pInterface.start_byte) {
                m_cMotionEvent = manageCMotionEvent(m_touchDevices, cmotionEvent, m_cMotionEvent)
                val pointerCount = m_cMotionEvent!!.pointerCount
                val pointerProperties =
                    arrayOfNulls<MotionEvent.PointerProperties>(pointerCount)
                val pointerCoords = arrayOfNulls<MotionEvent.PointerCoords>(pointerCount)
                for (i in 0 until pointerCount) {
                    pointerProperties[i] = MotionEvent.PointerProperties()
                    pointerProperties[i]!!.id = m_cMotionEvent!!.pointerProperties[i].id
                    pointerProperties[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER
                    pointerCoords[i] = MotionEvent.PointerCoords()
                    pointerCoords[i]!!.x =
                        m_cMotionEvent!!.pointerCoords[i].x
                    pointerCoords[i]!!.y =
                        m_cMotionEvent!!.pointerCoords[i].y
                    pointerCoords[i]!!.pressure = m_cMotionEvent!!.pointerCoords[i].pressure
                    pointerCoords[i]!!.size = m_cMotionEvent!!.pointerCoords[i].size
                }
                val maskedAction = m_cMotionEvent!!.action and MotionEvent.ACTION_MASK
                if (maskedAction == MotionEvent.ACTION_DOWN) {
                    m_downTime = SystemClock.uptimeMillis()
                    m_eventTime = SystemClock.uptimeMillis()
                } else {
                    m_eventTime = SystemClock.uptimeMillis()
                }
                val newevent: MotionEvent = MotionEvent.obtain(
                    m_downTime,
                    m_eventTime,
                    m_cMotionEvent!!.action,
                    m_cMotionEvent!!.pointerCount,
                    pointerProperties,
                    pointerCoords,
                    m_cMotionEvent!!.metaState,
                    m_cMotionEvent!!.buttonState,
                    m_cMotionEvent!!.xPrecision,
                    m_cMotionEvent!!.yPrecision,
                    m_cMotionEvent!!.deviceId,
                    m_cMotionEvent!!.edgeFlags,
                    InputDevice.SOURCE_TOUCHSCREEN,
                    m_cMotionEvent!!.flags
                )

                m_motionSetDisplayIdMethod!!.invoke(newevent, m_virtual_display!!.display.displayId)
                m_injectInputEventMethod?.invoke(m_inputManager, newevent, 0)
            }
        }
    }

    private fun manageCMotionEvent(touchDevices: MutableMap<String, MutableMap<Int, Int>>, newMotionEvent: cMotionEvent, oldMotionEvent: cMotionEvent?)
    : cMotionEvent{
        var touchIDs_count = 0
        var touchIDs_max = 0
        for ((device, pointID) in touchDevices) {
            for ((devicePointID, transID) in pointID) {
                if(transID >= touchIDs_count){
                    touchIDs_max = transID + 1
                }
                touchIDs_count++
            }
        }
        val maskedAction = newMotionEvent.action and MotionEvent.ACTION_MASK
        val maskedindex = (newMotionEvent.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr MotionEvent.ACTION_POINTER_INDEX_SHIFT

        val keysToRemove = mutableListOf<Int>()

        if (newMotionEvent.name in touchDevices) {
            val pointerCount = newMotionEvent.pointerCount
            //Search for pointerID that is no longer present : 1
            for ((devicePointID, transID) in touchDevices[newMotionEvent.name]!!) {
                for (i in 0 until pointerCount) {
                    val id = newMotionEvent.pointerProperties[i].id
                    if (id == devicePointID) {
                        break
                    }
                    else if ((i == pointerCount -1) && (id != devicePointID)){
                        keysToRemove.add(devicePointID)
                    }
                }
            }
            //Search for pointerID that is no longer present : 2
            for(key in keysToRemove){
                touchDevices[newMotionEvent.name]!!.remove(key)
                touchIDs_count--
            }
            //Add new pointerID
            for (i in 0 until pointerCount) {
                val id = newMotionEvent.pointerProperties[i].id
                if (id !in touchDevices[newMotionEvent.name]!!) {
                    touchDevices[newMotionEvent.name]!![id] = touchIDs_max
                    touchIDs_max++
                    touchIDs_count++
                }
            }
        }
        else{
            touchDevices[newMotionEvent.name] = mutableMapOf<Int,Int>()
            val pointerCount = newMotionEvent.pointerCount
            for (i in 0 until pointerCount) {
                val id = newMotionEvent.pointerProperties[i].id
                touchDevices[newMotionEvent.name]!![id] = touchIDs_max
                touchIDs_max++
                touchIDs_count++
            }
        }

        val pointerProperties = Array(touchIDs_count){
            SerializablePointerProperties(
                id =  0,
                toolType = 0
            )
        }
        val pointerCoords = Array(touchIDs_count){
            SerializablePointerCoords(
                x = 0.0f,
                y = 0.0f,
                pressure = 0.0f,
                size = 0.0f
            )
        }
        var index = 0
        var actionIndex = 0
        //Add the modified touch from this update
        for ((devicePointID, transID) in touchDevices[newMotionEvent.name]!!){
            val pointerCount = newMotionEvent.pointerCount
            for (i in 0 until pointerCount) {
                val id = newMotionEvent.pointerProperties[i].id
                if (id == devicePointID) {
                    pointerProperties[index].id =  transID
                    pointerProperties[index].toolType = newMotionEvent.pointerProperties[i].toolType
                    pointerCoords[index].x = newMotionEvent.pointerCoords[i].x
                    pointerCoords[index].y = newMotionEvent.pointerCoords[i].y
                    pointerCoords[index].pressure = newMotionEvent.pointerCoords[i].pressure
                    pointerCoords[index].size = newMotionEvent.pointerCoords[i].size
                    if(maskedindex == i){
                        actionIndex = index
                    }
                    index++
                }
            }
        }
        //Add the previous information
        if(oldMotionEvent != null) {
            for ((device, pointID) in touchDevices) {
                if (device != newMotionEvent.name) {
                    for ((devicePointID, transID) in pointID) {
                        val pointerCount = oldMotionEvent!!.pointerCount
                        for (i in 0 until pointerCount) {
                            val id = oldMotionEvent!!.pointerProperties[i].id
                            if (id == transID) {
                                pointerProperties[index].id = transID
                                pointerProperties[index].toolType =
                                    oldMotionEvent!!.pointerProperties[i].toolType
                                pointerCoords[index].x = oldMotionEvent!!.pointerCoords[i].x
                                pointerCoords[index].y = oldMotionEvent!!.pointerCoords[i].y
                                pointerCoords[index].pressure =
                                    oldMotionEvent!!.pointerCoords[i].pressure
                                pointerCoords[index].size = oldMotionEvent!!.pointerCoords[i].size
                                index++
                            }
                        }
                    }
                }
            }
        }
        var newmaskedAction = maskedAction

        // Remove any device that has received ACTION_CANCEL or ACTION_UP.
        if(maskedAction == MotionEvent.ACTION_UP || maskedAction == MotionEvent.ACTION_CANCEL){
            touchDevices.remove(newMotionEvent.name)
        }
        //Because this combines inputs from multiple devices, we must still take other devices into account
        // even if this particular device receives an ACTION_DOWN or ACTION_UP.
        if(maskedAction == MotionEvent.ACTION_DOWN && touchIDs_count > 1){
            newmaskedAction = MotionEvent.ACTION_POINTER_DOWN
        }
        else if(maskedAction == MotionEvent.ACTION_UP && touchIDs_count > 1){
            newmaskedAction = MotionEvent.ACTION_POINTER_UP
        }
        var newaction = newmaskedAction or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val cMotionEvent: cMotionEvent = cMotionEvent(
            com.auo.flex_compositor.pInterface.start_byte, newMotionEvent.name,newMotionEvent.decoder_width,
            newMotionEvent.decoder_height, newMotionEvent.downTime, newMotionEvent.eventTime,
            newaction,touchIDs_count,pointerProperties,pointerCoords,
            newMotionEvent.metaState,newMotionEvent.buttonState,newMotionEvent.xPrecision,newMotionEvent.yPrecision,
            0,newMotionEvent.edgeFlags,newMotionEvent.source,newMotionEvent.flags)

        return  cMotionEvent
    }


    fun getVirtualDisplay(): VirtualDisplay?
    {
        return m_virtual_display
    }

    @Synchronized
    private fun onTriggerRenderCallback(surfaceTexture: cSurfaceTexture) {
        m_renderTriggered.forEach { it(surfaceTexture) }
    }

    override fun getEGLContext(): EGLContext?
    {
        return m_eglcontext
    }

    override fun getSurfaceTexture(): cSurfaceTexture{
        return m_SurfaceTexture
    }
    @Synchronized
    override fun triggerRenderSubscribe(handler: (cSurfaceTexture?) -> Unit){
        m_renderTriggered.add(handler)
    }
    @Synchronized
    override fun triggerRenderUnsubscribe(handler: (cSurfaceTexture?) -> Unit){
        m_renderTriggered.remove(handler)
    }

    fun getTextureID(): Int?{
        return m_SurfaceTexture.getTextureID()
    }

    private fun stopAppByForce(context: Context?, packageName: String){
        if(context != null) {
            val activityManger: ActivityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method: Method = Class.forName("android.app.ActivityManager")
                .getMethod("forceStopPackage", String::class.java)
            method.invoke(activityManger, packageName)
        }
    }

}