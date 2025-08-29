package com.auo.flex_compositor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.auo.flex_compositor.pSQLDataBase.cContentManager

class MainActivity : ComponentActivity() {

    private var REQUEST_CODE_SCREEN_CAPTURE = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        val contentManager: cContentManager = cContentManager(this)

//        val intent = Intent(
//            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//            Uri.parse("package:${packageName}")
//        )
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        startActivity(intent)

        val bootStartService = Intent(this, BootStartService::class.java)
        startForegroundService(bootStartService)
        when (BuildConfig.TARGET_PLATFORM) {
            "DEBUG" ->{
                Log.d("MainActivity", "DEBUG")
            }
            "RCAR_ZDC" -> {
                Log.d("MainActivity", "RCAR_ZDC")
            }
            "SA8295" -> {
                Log.d("MainActivity", "SA8295")
            }
            else -> {
                Log.d("MainActivity", "else")
            }
        }
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
                ){
                    UiModifier()
                }
            }
        }

        window.insetsController?.hide(WindowInsets.Type.systemBars())
        window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // moveTaskToBack(true)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        super.onTouchEvent(event)
        Log.d("MainActivity", "Touch down at:")
        event?.let {
            when (it.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.d("MainActivity", "Touch down at: ${it.x}, ${it.y}")
                }
                MotionEvent.ACTION_MOVE -> {
                    Log.d("MainActivity", "Touch move at: ${it.x}, ${it.y}")
                }
                MotionEvent.ACTION_UP -> {
                    Log.d("MainActivity", "Touch up at: ${it.x}, ${it.y}")
                }

                else -> {}
            }
        }
        return true
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

@Composable
fun UiModifier() {
    val context = LocalContext.current
    val contentManager = remember { cContentManager(context) }
    val textState = remember { mutableStateOf(TextFieldValue("")) }
    val scrollState = rememberScrollState()
    val extraLines = "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n"

    //Automatically trigger Read when entering the screen for the first time.
    LaunchedEffect(Unit) {
        val commandLines = contentManager.getCommandLines()
        val displayText = commandLines.joinToString("\n")
        textState.value = TextFieldValue(displayText)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // 顯示 commandLine 的輸入與顯示區域
        BasicTextField(
            value = textState.value.text + extraLines,
            onValueChange = { newValue -> textState.value = TextFieldValue(newValue) },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .verticalScroll(scrollState)
                .background(Color.Gray.copy(alpha = 0.2f))
                .padding(16.dp),
            textStyle = LocalTextStyle.current.copy(color = Color.White)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            // Read Button
            Button(
                modifier = Modifier
                    .width(200.dp)
                    .height(70.dp)
                    .padding(end = 16.dp),
                onClick = {
                    val commandLines = contentManager.getCommandLines()
                    val displayText = commandLines.joinToString("\n")
                    textState.value = TextFieldValue(displayText)
//                    Log.d("MainActivity", "Read commandLines: $displayText")
                }
            ) {
                Text(text = "Read DataBase")
            }

            // Update Button
            Button(
                modifier = Modifier
                    .width(200.dp)
                    .height(70.dp)
                    .padding(end = 16.dp),
                onClick = {
                    val inputText = textState.value.text
                    val lines = inputText.split("\n").filter { it.isNotEmpty() }
                    contentManager.updateCommandLines(lines)
                    //Log.d("MainActivity", "Updated commandLines: $lines")
                }
            ) {
                Text(text = "Update DataBase")
            }

            // Restart Button
            Button(
                modifier = Modifier
                    .width(200.dp)
                    .height(70.dp),
                onClick = {
                    val intent = Intent("com.auo.flex_compositor.UPDATE_DATA")
                    intent.putExtra("restart", true)
                    context.sendBroadcast(intent)
//                    Log.d("MainActivity", "Restart: Sent broadcast with restart = true")
                }
            ) {
                Text(text = "Restart")
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Flex_CompositorTheme {
        Greeting("Android")
    }
}