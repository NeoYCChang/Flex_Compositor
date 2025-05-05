package com.auo.flex_compositor.pSource

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
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceControl
import com.auo.flex_compositor.pEGLFunction.EGLHelper
import javax.microedition.khronos.egl.EGLContext
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pInterface.iTouchMapper
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pView.cSurfaceTexture
import java.lang.reflect.Method
import com.auo.flex_compositor.pInterface.iElement
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.vSize

class cVirtualDisplay(override val e_name: String,override val e_id: Int): iSurfaceSource {

    companion object {
        private val m_tag = "cVirtualDisplay"
    }

    private var m_virtual_display: VirtualDisplay? = null
    private val m_eglcontext: EGLContext? = StaticVariable.public_eglcontext
    private var m_SurfaseTexture: cSurfaceTexture? = null
    private var m_Surface: Surface? = null
    private var m_injectInputEventMethod : Method? = null
    private var m_motionSetDisplayIdMethod : Method? = null
    private var m_inputManager: InputManager? = null
    private var m_appName: String? = ""


    constructor(context: Context, name: String, id: Int, size: vSize, appName: String?) : this(name, id)  {
        val textureid: Int = EGLRender.createOESTextureObject()
        m_SurfaseTexture = cSurfaceTexture(textureid)
        m_SurfaseTexture?.setDefaultBufferSize(size.width, size.height)
        m_Surface = Surface(m_SurfaseTexture)
        m_appName = appName
        val display_manager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
        m_virtual_display = display_manager.createVirtualDisplay(
            name,
            size.width,
            size.height,
            240,
            m_Surface,
            0
        )
        Log.d(m_tag, "Create a Virtual Display {textureid: $textureid}")

        if(m_appName != null) {
            val app_split = m_appName!!.split('/')
            if(app_split.size == 2) {
                val package_name = app_split[0]
                val activity_path = app_split[1]
                val intent = Intent()
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val comp = ComponentName(package_name, activity_path)
                intent.setComponent(comp);
                val options: ActivityOptions = ActivityOptions.makeBasic()

                // Try to resolve the intent to see if there's a matching activity
                options.launchDisplayId =
                    m_virtual_display!!.display.displayId  // Here, fill in the DisplayId you want to specify.
                try {
                    context.startActivity(intent, options.toBundle())
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
        m_inputManager = context.getSystemService(INPUT_SERVICE) as InputManager
    }

    fun destroyed(){
        m_virtual_display?.release()
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
    override fun injectMotionEvent(motionEvent: MotionEvent) {
        if(m_motionSetDisplayIdMethod ==  null) {
            // Get the MotionEvent class
            val motionEventClass = MotionEvent::class.java
            m_motionSetDisplayIdMethod = motionEventClass.getMethod(
                "setDisplayId",
                Int::class.java      // DisplayID
            )
        }
        m_motionSetDisplayIdMethod!!.invoke(motionEvent, m_virtual_display!!.display.displayId)
        m_injectInputEventMethod?.invoke(m_inputManager, motionEvent, 0)
        Log.d(m_tag, "touch ")
    }


    fun getVirtualDisplay(): VirtualDisplay?
    {
        return m_virtual_display
    }

    override fun getEGLContext(): EGLContext?
    {
        return m_eglcontext
    }

    override fun getSurfaceTexture(): cSurfaceTexture?{
        return m_SurfaseTexture
    }

    fun getTextureID(): Int?{
        return m_SurfaseTexture?.getTextureID()
    }

    private class StaticVariable {
        companion object {
            // Static-like function
            private val m_surfaceControl = SurfaceControl.Builder()
                .setName("AUOSurface")
                .setBufferSize(20, 20)
                .build()
            private val m_surface = Surface(m_surfaceControl)
            private val m_eglHelper: EGLHelper? = EGLHelper()
            var public_eglcontext: EGLContext? = null

            init {
                m_eglHelper?.initEgl(m_surface, null)
                public_eglcontext = m_eglHelper!!.getmEglContext()
            }
        }
    }
}