package com.auo.flex_compositor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.auo.flex_compositor.pFilter.cMediaDecoder
import com.auo.flex_compositor.pFilter.cMediaEncoder
import com.auo.flex_compositor.pInterface.vTouchMapping
import com.auo.flex_compositor.pSink.cDisplayView
import com.auo.flex_compositor.ui.theme.Flex_CompositorTheme
import com.auo.flex_compositor.pSource.cVirtualDisplay
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pInterface.vSize
import com.auo.flex_compositor.pParse.cParseFlexCompositor

class MainActivity : ComponentActivity() {

    private var REQUEST_CODE_SCREEN_CAPTURE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bootStartService = Intent(this, BootStartService::class.java)
        startForegroundService(bootStartService)

        val size = vSize(1920,1080)
//        val test_virtualdisplay = cVirtualDisplay(this,"test",size,"test")
        val size2 = vSize(480,270)
        val possize = vPos_Size(0,0,700,500)
        val cropTextureArea = vCropTextureArea(960,540,960,540)
        val cropTextureArea2 = vCropTextureArea(0,0,480,270)
        val toucharea = vTouchMapping(960,540,960,540)
        val toucharea2 = vTouchMapping(0,0,480,270)
//        val test_cMediaEncoder = cMediaEncoder(this,"test",test_virtualdisplay, size2, cropTextureArea, toucharea,false)
//        val test_cMediaDecoder = cMediaDecoder(this, "test2",size2,"127.0.0.1")
//
//
//        test_displayview = cDisplayView(this,"123",test_virtualdisplay, 0, possize, cropTextureArea, toucharea,false)

//        val possize2 = vPos_Size(960,0,960,540)
//        val cropTextureArea2 = vCropTextureArea(960,0,960,1080)
//        val toucharea2 = vTouchMapping(960,0,960,1080)
//        val test_displayview2 = cDisplayView(this,"123",test_virtualdisplay, 0, possize2, cropTextureArea2, toucharea,true)
        enableEdgeToEdge()
        setContent {
            Flex_CompositorTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
//                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black) // 畫面設為黑色
                )
            }
        }

        window.insetsController?.hide(WindowInsets.Type.systemBars())
        window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onPause() {
        super.onPause()
        // Activity 快要被隱藏或失去焦點
        Log.d("MainActivity", "onPause - Activity is no longer in the foreground ${REQUEST_CODE_SCREEN_CAPTURE}")
        REQUEST_CODE_SCREEN_CAPTURE++
        val intent = Intent("com.auo.flex_compositor.UPDATE_DATA")
        intent.putExtra("visible", false)
        sendBroadcast(intent)
    }

    override fun onStop() {
        super.onStop()
        // Activity 完全不可見（進入背景）
        Log.d("MainActivity", "onStop - Activity is now hidden or in background ${REQUEST_CODE_SCREEN_CAPTURE}")
        REQUEST_CODE_SCREEN_CAPTURE++
    }

    override fun onResume() {
        super.onResume()
        // Activity 回到前景
        Log.d("MainActivity", "onResume - Activity is now visible and interactive ${REQUEST_CODE_SCREEN_CAPTURE}")
        REQUEST_CODE_SCREEN_CAPTURE++
        val intent = Intent("com.auo.flex_compositor.UPDATE_DATA")
        intent.putExtra("visible", true)
        sendBroadcast(intent)
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Flex_CompositorTheme {
        Greeting("Android")
    }
}