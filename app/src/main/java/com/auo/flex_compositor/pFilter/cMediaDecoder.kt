package com.auo.flex_compositor.pFilter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.Surface
import android.view.SurfaceControl
import com.auo.flex_compositor.pEGLFunction.EGLHelper
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pInterface.SerializablePointerCoords
import com.auo.flex_compositor.pInterface.SerializablePointerProperties
import com.auo.flex_compositor.pInterface.cMotionEvent
import com.auo.flex_compositor.pInterface.cParseH264Codec
import com.auo.flex_compositor.pInterface.cParseH265Codec
import com.auo.flex_compositor.pInterface.eBufferType
import com.auo.flex_compositor.pInterface.eCodecType
import com.auo.flex_compositor.pInterface.iParseCodec
import com.auo.flex_compositor.pInterface.iSurfaceSource
import com.auo.flex_compositor.pInterface.vSize
import com.auo.flex_compositor.pView.cSurfaceTexture
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.URI
import javax.microedition.khronos.egl.EGLContext

class cMediaDecoder(context: Context, override val e_name: String, override val e_id: Int, size: vSize, serverip: String, serverport: String
                    , codecType: eCodecType = eCodecType.H264): iSurfaceSource {
    private var mMediaCodec: MediaCodec? = null  // MediaCodec object for decoding
    private var m_video_width: Int = 1920  // Video width (default 1920)
    private var m_video_height: Int = 1080  // Video height (default 1080)
    private val DECODE_TIME_OUT: Long = 10000  // Timeout for decoding (10 seconds)
    private val SCREEN_FRAME_RATE = 20  // Frame rate for the video
    private val SCREEN_FRAME_INTERVAL = 1  // Interval for I-frames (default is 1)
    private val m_tag = "cMediaDecoder"  // Tag for logging
    private var m_isGetVPS: Boolean = false  // Flag to check if VPS (Video Parameter Set) is received
    private val m_codecType: eCodecType = codecType
    private var m_parseCodec: iParseCodec? = null

    private val m_eglcontext: EGLContext? = StaticVariable.public_eglcontext
    private var m_SurfaseTexture: cSurfaceTexture? = null
    private var m_Surface: Surface? = null
    private var m_size = size

    // websocket client
    private val m_serverip  = serverip
    private val m_socket_port: String = serverport
    private var m_webSocketClient: cWebSocketClient? = null

    init {
        startDecode()
        val uri = URI("ws://"+m_serverip+":" + m_socket_port)
        val callback = object :  cWebSocketClient.SocketCallback{
            override fun onReceiveData(data: ByteArray?) {
                if(data != null) {
                    decodeData(data)
                }
            }
        }
        m_webSocketClient = cWebSocketClient(callback, uri)
        m_webSocketClient!!.connect()
    }

    // This function is commented out but would list all available decoders in the system.
// It can be useful to find hardware-accelerated decoders.
    fun getAvailableDecoders(): List<String> {
        val availableDecoders = mutableListOf<String>()

        val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecs = mediaCodecList.codecInfos

        for (codecInfo in codecs) {
            if (codecInfo.isEncoder) { // 跳过编码器，只关注解码器
                codecInfo.name
                val supportedTypes = codecInfo.supportedTypes
                for (type in supportedTypes) {
                    if (codecInfo.isHardwareAccelerated) {
                        availableDecoders.add(codecInfo.name)
                    }
                }
            }
        }

        return availableDecoders
    }

    // Starts decoding by configuring the MediaCodec for the specific video format
    fun startDecode() {
//        val decoders = getAvailableDecoders()
//        for (decoder in decoders) {
//            println("Available eecoder: $decoder")
//        }
        try {
            val textureid: Int = EGLRender.createOESTextureObject()
            m_SurfaseTexture = cSurfaceTexture(textureid)
            m_SurfaseTexture?.setDefaultBufferSize(m_size.width, m_size.height)
            m_Surface = Surface(m_SurfaseTexture)

            m_isGetVPS = false
            // config MediaCodec
            //mMediaCodec = MediaCodec.createByCodecName("c2.android.hevc.decoder")
            var mimetype: String = MediaFormat.MIMETYPE_VIDEO_HEVC
            when (m_codecType) {
                eCodecType.H265 -> {
                    m_parseCodec = cParseH265Codec()
                    mimetype = MediaFormat.MIMETYPE_VIDEO_HEVC
                    Log.d(m_tag, "Construct h265 Decoder")
                }
                eCodecType.H264 -> {
                    m_parseCodec = cParseH264Codec()
                    mimetype = MediaFormat.MIMETYPE_VIDEO_AVC
                    Log.d(m_tag, "Construct h264 Decoder")
                }
            }
            mMediaCodec = MediaCodec.createDecoderByType(mimetype)
            val mediaFormat =
                MediaFormat.createVideoFormat(
                    mimetype,
                    m_video_width,
                    m_video_height
                )
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, m_video_width * m_video_height * 2)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, SCREEN_FRAME_RATE)
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, SCREEN_FRAME_INTERVAL)
            //mediaFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020);



            mMediaCodec!!.configure(mediaFormat, m_Surface, null, 0)
            mMediaCodec!!.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    // This function decodes the video data frame by frame
    fun decodeData(data: ByteArray) {
        // If VPS has not been received yet, check for VPS in the data
        if(!m_isGetVPS){
            if(data.size < 5) return
            var type = eBufferType.BUFFER_NULL
            if(m_parseCodec != null) {
                type = m_parseCodec!!.getBufferType(data)
            }
            if(type == eBufferType.BUFFER_FLAG_CODEC_CONFIG){
                m_isGetVPS = true
            }
            else{
                return
            }
        }
        // Dequeue input buffer for the MediaCodec to process the incoming data
        val index = mMediaCodec!!.dequeueInputBuffer(DECODE_TIME_OUT)
        if (index >= 0) {
            val inputBuffer = mMediaCodec!!.getInputBuffer(index)
            inputBuffer!!.clear()
            inputBuffer.put(data, 0, data.size)
            mMediaCodec!!.queueInputBuffer(index, 0, data.size, System.currentTimeMillis(), 0)
        }
        val bufferInfo = MediaCodec.BufferInfo()
        // Dequeue output buffer from the MediaCodec to get decoded video frames
        var outputBufferIndex = mMediaCodec!!.dequeueOutputBuffer(bufferInfo, DECODE_TIME_OUT)
        while (outputBufferIndex > 0) {
            mMediaCodec!!.releaseOutputBuffer(outputBufferIndex, true)
            outputBufferIndex = mMediaCodec!!.dequeueOutputBuffer(bufferInfo, 0)
        }
    }

    fun stopDecode() {
        if (mMediaCodec != null) {
            mMediaCodec!!.release()
        }
        if (m_webSocketClient != null) {
            m_webSocketClient!!.close()
        }
    }

    override fun injectMotionEvent(motionEvent: MotionEvent) {

        val pointerCount = motionEvent.pointerCount
        val pointerProperties =Array(pointerCount) { i ->
            SerializablePointerProperties(
                id =  motionEvent.getPointerId(i),
                toolType = motionEvent.getToolType(i)
            )
        }
        val pointerCoords = Array(pointerCount) { i ->
            SerializablePointerCoords(
                x = motionEvent.getX(i),
                y = motionEvent.getY(i),
                pressure = motionEvent.getPressure(i),
                size = motionEvent.getSize(i)
            )
        }

        val cMotionEvent: cMotionEvent = cMotionEvent(
            com.auo.flex_compositor.pInterface.start_byte, m_size.width,m_size.height,
            motionEvent.downTime, motionEvent.eventTime,
            motionEvent.action,motionEvent.pointerCount,pointerProperties,pointerCoords,
            motionEvent.metaState,motionEvent.buttonState,motionEvent.xPrecision,motionEvent.yPrecision,
            0,motionEvent.edgeFlags,motionEvent.source,motionEvent.flags)

        val motionEventBytes = Json.encodeToString(cMotionEvent).encodeToByteArray()

        m_webSocketClient?.sendData(motionEventBytes)

        Log.d(m_tag, "touch ")
    }

    override fun getEGLContext(): EGLContext?
    {
        return m_eglcontext
    }

    override fun getSurfaceTexture(): cSurfaceTexture?{
        return m_SurfaseTexture
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