package com.auo.flex_compositor.pPIPView

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.auo.flex_compositor.pSink.cDisplayView

class ChildViewModel(displayView: cDisplayView?): ViewModel() {
    var childView by mutableStateOf<cDisplayView?>(null)
        private set

    fun changeDisplayView(c_displayView: cDisplayView){
        childView = c_displayView
    }

    // Define ViewModel factory in a companion object
    // To create a ViewModel with constructor parameters,
    // you need to implement a custom ViewModelProvider.Factory
    // https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories/?utm_source=chatgpt.com
    companion object {
        fun provideFactory(displayView: cDisplayView?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChildViewModel(displayView) as T
                }
        }
    }
}