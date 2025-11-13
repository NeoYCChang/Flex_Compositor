package com.auo.flex_compositor.pPIPView

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.auo.flex_compositor.pFilter.cMediaDecoder
import com.auo.flex_compositor.pInterface.eCodecType
import com.auo.flex_compositor.pInterface.vCropTextureArea
import com.auo.flex_compositor.pInterface.vPos_Size
import com.auo.flex_compositor.pSink.cDisplayView
import com.auo.flex_compositor.ui.theme.Flex_CompositorTheme


class UiChildActivity : ChildActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            Flex_CompositorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    Greeting2(
//                        name = "Android",
//                        modifier = Modifier.padding(innerPadding)
//                    )
                    VideoSurfaceView(getViewModel(), modifier = Modifier.padding(innerPadding))
                }
//                FullScreenImageSecurity()
            }
        }
    }

    override fun attachedParentActivity(): Class<*> {
        return UiParentActivity::class.java
    }

    override fun attachedChildActivity(): Class<*> {
        return UiChildActivity::class.java
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ChildActivity_test", "onStop - ChildActivity_test is now hidden or in background")
    }
}

@Composable
fun Greeting2(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

//@Composable
//fun FullScreenImageSecurity() {
//    Image(
//        painter = painterResource(id = R.drawable.security),
//        contentDescription = "Background Image",
//        modifier = Modifier.fillMaxSize(),
//        contentScale = ContentScale.FillBounds
//    )
//}

@Composable
fun VideoSurfaceView(viewModel: ChildViewModel, modifier: Modifier = Modifier) {
    val displayView = viewModel.childView
    displayView?.let {
        // Use `key` with the displayView instance to force AndroidView
        // to recreate its underlying Android View when displayView changes.
        key(it) {
            AndroidView(
                factory = { context ->
                    it
                },
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }
    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview2() {
    Flex_CompositorTheme {
        Greeting2("Android")
    }
}