package com.auo.flex_compositor.pInterface

import android.view.MotionEvent
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer

data class vTouchMapping(var offsetX: Int, var offsetY: Int, var width: Int, var height: Int)
interface iTouchMapper {
    fun injectMotionEvent(motionEvent: cMotionEvent)
}

fun funtoBytes(value: Long): ByteArray {
    return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
}

fun funtoBytes(value: Int): ByteArray {
    return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
}

fun funtoBytes(value: Float): ByteArray {
    return ByteBuffer.allocate(Float.SIZE_BYTES).putFloat(value).array()
}

fun funtoBytes(value: Byte): ByteArray {
    return ByteBuffer.allocate(Byte.SIZE_BYTES).put(value).array()
}

@Serializable
data class SerializablePointerProperties(
    var id: Int,
    var toolType: Int
)

@Serializable
data class SerializablePointerCoords(
    var x: Float,
    var y: Float,
    var pressure: Float,
    var size: Float
)

@Serializable
data class cMotionEvent(
    val start: Byte,
    var name: String,
    var decoder_width: Int,
    var decoder_height: Int,
    val downTime: Long,
    val eventTime: Long,
    val action: Int,
    val pointerCount: Int,
    val pointerProperties: Array<SerializablePointerProperties>,
    val pointerCoords: Array<SerializablePointerCoords>,
    val metaState: Int,
    val buttonState: Int,
    val xPrecision: Float,
    val yPrecision: Float,
    val deviceId: Int,
    val edgeFlags: Int,
    val source: Int,
    val flags: Int
)

val start_byte: Byte = 0b01010101