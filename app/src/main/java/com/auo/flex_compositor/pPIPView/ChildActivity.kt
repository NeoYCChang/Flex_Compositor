package com.auo.flex_compositor.pPIPView

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.auo.flex_compositor.pCMDJson.CmdProtocol
import com.auo.flex_compositor.pCMDJson.SecurityEvent
import com.auo.flex_compositor.pCMDJson.cCmdReqRes
import com.auo.flex_compositor.pCMDJson.cTestSocketClient
import com.auo.flex_compositor.pSink.cDisplayView
import java.io.OutputStream

interface ChildInterface {
    /**
     * Retrieves the aspect ratio for Picture-in-Picture (PiP) mode.
     *
     * @return A Pair containing:
     *         - First: numerator of the aspect ratio
     *         - Second: denominator of the aspect ratio
     */
    fun getPiPAspectRatio() : Pair<Int, Int>

    /**
     * Configure the ParentActivity to be associated with the child.
     *
     * @return UI ParentActivity:
     */
    fun attachedParentActivity() : Class<*>

    /**
     * Allows subclasses of ChildActivity to be brought back to the foreground by the parent
     * in special situations, such as receiving a warning event
     *
     * @return UI ChildActivity:
     */
    fun attachedChildActivity() : Class<*>
}

open class ChildActivity() : ComponentActivity(), ChildInterface {

    private val m_tag: String = "ChildActivity"
    private var m_startParentActivity = false
    private var m_needToCheckIfEnterPIP = false
    private var m_isReceivedSecurityEvent = false
    private var m_isInForeground = true
    private var m_tmpOutput : OutputStream? = null
    private var m_tmpJsonSecurityEvent : List<SecurityEvent>? = null
    private var m_pipViewBinder: MainPIP.PIPViewBinder? = null
    private val m_childViewModel by viewModels<ChildViewModel>{ChildViewModel.provideFactory(null)}

    // Triggered to bring the main screen to the front when a security event is received
    //private val m_CmdReqRes: cCmdReqRes = cCmdReqRes()
    /**
     * Called when the activity is starting.
     * Immediately enters Picture-in-Picture (PiP) mode upon creation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        procIntentPIP_Binder()
        checkIfEnterPIPMode()
//        m_CmdReqRes.securityEvent += { event, output ->
//            onSecurityEventCallback(event, output)
//        }
//        m_CmdReqRes.start()
//        val cTestSocketClient = cTestSocketClient()
    }

    private fun procIntentPIP_Binder(){
        val pipViewBinder = intent.extras?.getBinder(IntentInfo.PIPBINDER.name)
        Log.d(m_tag, "procIntentPIP_Binder0")
        if(pipViewBinder != null){
            Log.d(m_tag, "procIntentPIP_Binder1")
            if(pipViewBinder is MainPIP.PIPViewBinder){
                Log.d(m_tag, "procIntentPIP_Binder2")
                m_pipViewBinder = pipViewBinder
                m_pipViewBinder!!.securityEvent = { event, output ->
                    onSecurityEventCallback(event, output)
                }
                Log.d(m_tag, "procIntentPIP_Binder ${pipViewBinder.getDisplayView()}")
                m_childViewModel.changeDisplayView(pipViewBinder.getDisplayView())
            }
        }
    }

    protected fun getViewModel(): ChildViewModel{
        return m_childViewModel
    }

    private fun enterPIPMode() {
        val (numerator, denominator) = getPiPAspectRatio()
        val aspectRatio = Rational(numerator, denominator)
        val pipBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
        enterPictureInPictureMode(pipBuilder.build())
    }

    private fun checkIfEnterPIPMode(): Boolean{
        val needToPIP = intent.extras?.getBoolean(IntentInfo.NEEDTOPIP.name)
        if(needToPIP == true) {
            enterPIPMode()
            return true
        }
        return false
    }

    open override fun getPiPAspectRatio() : Pair<Int, Int> {
        val numerator = 16
        val denominator = 9
        return Pair<Int, Int>(numerator, denominator)
    }

    open override fun attachedParentActivity(): Class<*> {
        return ParentActivity::class.java
    }

    open override fun attachedChildActivity(): Class<*> {
        return ChildActivity::class.java
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        Log.d(m_tag, "onPictureInPictureModeChanged $isInPictureInPictureMode")

        startParentActivityAndNotifyEnterPIP(isInPictureInPictureMode)
    }


    /**
     * If the child is NOT in Picture-in-Picture (PiP) mode,
     * this method will reopen the parent activity and notify it to enter PiP mode.
     *
     * @param isInPictureInPictureMode Boolean indicating whether the child is currently in PiP mode.
     */
    private fun startParentActivityAndNotifyEnterPIP(isInPictureInPictureMode: Boolean){
        if(!isInPictureInPictureMode){
            m_startParentActivity = true
        }
    }

    private fun startParentActivity(){
        val intent: Intent = Intent(this, attachedParentActivity())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK )
        val bundle = Bundle().apply {
            putBoolean(IntentInfo.NEEDTOPIP.name, !isInPictureInPictureMode)
            putBinder(IntentInfo.PIPBINDER.name, m_pipViewBinder)
        }
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun moveToFront(){
        val intent: Intent = Intent(this, attachedChildActivity())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun onSecurityEventCallback(jsonEvents: List<SecurityEvent>, output: OutputStream){
        Log.d(m_tag, "onSecurityEventCallback")
        if(isInPictureInPictureMode || !m_isInForeground){
            m_isReceivedSecurityEvent = true
            m_tmpJsonSecurityEvent = jsonEvents
            m_tmpOutput = output
            moveToFront()
        }
        else{
            CmdProtocol.replySecurityReply(jsonEvents, output, true)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(m_tag, "onPause - Activity is no longer in the foreground")
        m_isInForeground = false
    }

    override fun onStop() {
        super.onStop()
        Log.d(m_tag, "onStop - Activity is now hidden or in background")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        m_needToCheckIfEnterPIP = true
        procIntentPIP_Binder()
    }

    override fun onResume() {
        super.onResume()
        m_isInForeground = true
        Log.d(m_tag, "onResume - Activity is now visible and interactive")
        if(m_needToCheckIfEnterPIP) {
            m_needToCheckIfEnterPIP = false
            val enable = checkIfEnterPIPMode()
            if(enable){
                m_startParentActivity = false
            }
        }
        if(m_startParentActivity) {
            m_startParentActivity = false
            startParentActivity()
        }
        if(m_isReceivedSecurityEvent){
            m_isReceivedSecurityEvent = false
            if(m_tmpJsonSecurityEvent != null && m_tmpOutput != null) {
                CmdProtocol.replySecurityReply(m_tmpJsonSecurityEvent!!, m_tmpOutput!!, true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
//        m_CmdReqRes.close()
    }

}
