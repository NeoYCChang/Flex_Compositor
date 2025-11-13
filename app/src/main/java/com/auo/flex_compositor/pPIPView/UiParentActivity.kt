package com.auo.flex_compositor.pPIPView

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.auo.flex_compositor.R
import com.auo.flex_compositor.ui.theme.Flex_CompositorTheme

class UiParentActivity : ParentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "Activity - onCreate")
        enableEdgeToEdge()
        setContent {
            Flex_CompositorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
                //FullScreenImagePad()
            }
        }
    }

    override fun attachedChildActivity(): Class<*> {
        return UiChildActivity::class.java
    }
}

fun openYouTube(context: Context) {
    val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://"))
    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))

    try {
        context.startActivity(youtubeIntent)
    } catch (e: Exception) {
        context.startActivity(webIntent)
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
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        FullScreenImagePad()
        Button(onClick = {
            openYouTube(context = context)
        }) {
            Text("Open YouTube")
        }
    }
}

@Composable
fun FullScreenImagePad() {
    Image(
        painter = painterResource(id = R.drawable.pad),
        contentDescription = "Background Image",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.FillBounds
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Flex_CompositorTheme {
        Greeting("Android")
    }
}