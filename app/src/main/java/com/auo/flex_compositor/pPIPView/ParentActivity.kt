package com.auo.flex_compositor.pPIPView

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.auo.flex_compositor.pSink.cDisplayView

interface ParentInterface {
    /**
     * Retrieves the aspect ratio for Picture-in-Picture (PiP) mode.
     *
     * @return A Pair containing:
     *         - First: numerator of the aspect ratio
     *         - Second: denominator of the aspect ratio
     */
    fun getPiPAspectRatio() : Pair<Int, Int>

    /**
     * Configure the ChildActivity to be associated with the parent.
     *
     * @return UI ChildActivity:
     */
    fun attachedChildActivity() : Class<*>
}



enum class IntentInfo(val label: String) {
    NEEDTOPIP("needToPIP"),
    PIPBINDER("PIPBinder")
}

open class ParentActivity : ComponentActivity(), ParentInterface {

    private val m_tag: String = "ParentActivity"
    private var m_needToCheckIfEnterPIP = false
    private var m_startChildActivity = false
    private var m_pipViewBinder: MainPIP.PIPViewBinder? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(m_tag, "Activity - onCreate")
        procIntentPIP_Binder()
        startChildActivity()
    }

    // If PIP_Binder can be retrieved from the Intent
    private fun procIntentPIP_Binder(): Boolean{
        val pipViewBinder = intent.extras?.getBinder(IntentInfo.PIPBINDER.name)
        if(pipViewBinder != null){
            if(pipViewBinder is MainPIP.PIPViewBinder){
                m_pipViewBinder = pipViewBinder
                return true
            }
        }
        return false
    }

    open override fun getPiPAspectRatio() : Pair<Int, Int> {
        val numerator = 16
        val denominator = 9
        return Pair<Int, Int>(numerator, denominator)
    }


    open override fun attachedChildActivity(): Class<*> {
        return ChildActivity::class.java
    }

    private fun startChildActivity(){
        val intent: Intent = Intent(this, attachedChildActivity())
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val bundle = Bundle().apply {
            putBoolean(IntentInfo.NEEDTOPIP.name, !isInPictureInPictureMode)
            putBinder(IntentInfo.PIPBINDER.name, m_pipViewBinder)
        }
        intent.putExtras(bundle)
        startActivity(intent)
    }


    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        startChildActivityAndNotifyEnterPIP(isInPictureInPictureMode)
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

    /**
     * If the parent is NOT in Picture-in-Picture (PiP) mode,
     * this method will reopen the child activity and notify it to enter PiP mode.
     *
     * @param isInPictureInPictureMode Boolean indicating whether the child is currently in PiP mode.
     */
    private fun startChildActivityAndNotifyEnterPIP(isInPictureInPictureMode: Boolean){
        if(!isInPictureInPictureMode){
            m_startChildActivity = true
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(m_tag, "onPause - Activity is no longer in the foreground")
    }

    override fun onStop() {
        super.onStop()
        Log.d(m_tag, "onStop - Activity is now hidden or in background")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        m_needToCheckIfEnterPIP = true
        m_startChildActivity = procIntentPIP_Binder()
    }


    override fun onResume() {
        super.onResume()
        if(m_needToCheckIfEnterPIP) {
            m_needToCheckIfEnterPIP = false
            val enable = checkIfEnterPIPMode()
            if(enable){
                m_startChildActivity = false
            }
        }
        if(m_startChildActivity) {
            m_startChildActivity = false
            startChildActivity()
        }
        Log.d(m_tag, "onResume - Activity is now visible and interactive")
    }

}

