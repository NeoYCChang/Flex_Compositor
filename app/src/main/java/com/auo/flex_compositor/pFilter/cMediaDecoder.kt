package com.auo.flex_compositor.pFilter

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.auo.flex_compositor.pEGLFunction.EGLRender
import com.auo.flex_compositor.pEGLFunction.StaticVariable
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
import java.util.concurrent.locks.ReentrantLock
import android.opengl.EGLContext
import com.auo.flex_compositor.pInterface.eCodecState
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

class cMediaDecoder(context: Context, override val e_name: String, override val e_id: Int, size: vSize, serverip: String, serverport: String
                    , codecType: eCodecType = eCodecType.H264): iSurfaceSource {
    private var mMediaCodec: MediaCodec? = null  // MediaCodec object for decoding
    private val DECODE_TIME_OUT: Long = 10000  // Timeout for decoding (10 seconds)
    private val SCREEN_FRAME_RATE = 30  // Frame rate for the video
    private val SCREEN_FRAME_INTERVAL = 1  // Interval for I-frames (default is 1)
    private val m_tag = "cMediaDecoder"  // Tag for logging
    private var m_isGetVPS: Boolean = false  // Flag to check if VPS (Video Parameter Set) is received
    private val m_codecType: eCodecType = codecType
    private var m_parseCodec: iParseCodec? = null
    private val m_renderTriggered = mutableListOf<(cSurfaceTexture) -> Unit>()

    private val m_eglcontext: EGLContext? = StaticVariable.public_eglcontext
    private lateinit var m_SurfaceTexture: cSurfaceTexture
    private var m_Surface: Surface? = null
    private var m_size = size
    private var m_isCallStop = false
    private var m_codecState = eCodecState.RELEASED

    // websocket client
    private val m_serverip  = serverip
    private val m_socket_port: String = serverport
    private var m_webSocketClient: cWebSocketClient? = null
    private val m_dataDeque = ArrayDeque<ByteArray>()
    private val m_inputIndexDeque = ArrayDeque<Int>()
    private val m_decodeDataLock = ReentrantLock()
    private val m_MotionLock = ReentrantLock()
    private var m_codecGeneration = 0

    // codec thread
    private val m_codecThread: HandlerThread = HandlerThread("MediaCodecCallbackThread");
    private var m_codecHandle: Handler? = null


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

        //listAllCodecInstanceLimits()
    }

    fun listAllCodecInstanceLimits() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos

        for (codecInfo in codecInfos) {
            val isEncoder = codecInfo.isEncoder
            val codecType = if (isEncoder) "Encoder" else "Decoder"
            val name = codecInfo.name
            val types = codecInfo.supportedTypes

            println("=== $codecType: $name ===")
            for (mime in types) {
                try {
                    val caps = codecInfo.getCapabilitiesForType(mime)
                    val maxInstances = caps.maxSupportedInstances
                    println("  • MIME: $mime, Max Supported Instances: $maxInstances")
                } catch (e: Exception) {
                    println("  • MIME: $mime, Failed to get capabilities: ${e.message}")
                }
            }
        }
    }

    // This function is commented out but would list all available decoders in the system.
// It can be useful to find hardware-accelerated decoders.
    fun getAvailableDecoders(): List<String> {
        val availableDecoders = mutableListOf<String>()

        val mediaCodecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecs = mediaCodecList.codecInfos

        for (codecInfo in codecs) {
            if (!codecInfo.isEncoder) { // 跳过编码器，只关注解码器
                codecInfo.name
                val supportedTypes = codecInfo.supportedTypes
                for (type in supportedTypes) {
                    //if (codecInfo.isHardwareAccelerated) {
                        availableDecoders.add(codecInfo.name)
                   // }
                }
            }
        }

        return availableDecoders
    }

    // Starts decoding by configuring the MediaCodec for the specific video format
    private fun startDecode() {
//        val decoders = getAvailableDecoders()
//        for (decoder in decoders) {
//            println("Available eecoder: $decoder")
//        }
        try {
                if (!::m_SurfaceTexture.isInitialized) {
                    val textureid: Int = EGLRender.createOESTextureObject()
                    m_SurfaceTexture = cSurfaceTexture(textureid)
                    m_SurfaceTexture.setTriggerRender(::onTriggerRenderCallback)
                    m_SurfaceTexture.setDefaultBufferSize(m_size.width, m_size.height)
                    m_Surface = Surface(m_SurfaceTexture)
                }


                m_isGetVPS = false
                m_inputIndexDeque.clear()
                m_dataDeque.clear()
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
                        m_size.width,
                        m_size.height
                    )
                mediaFormat.setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    m_size.width * m_size.height * m_parseCodec!!.BITRATE_MULTIPLIER
                )
                mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, SCREEN_FRAME_RATE)
                mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, SCREEN_FRAME_INTERVAL)
                //mediaFormat.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020);
                if (m_codecHandle == null) {
                    m_codecHandle = startThread(m_codecThread)
                }
                m_codecGeneration++
                val currentGeneration = m_codecGeneration
                mMediaCodec?.setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        if (m_codecGeneration != currentGeneration) return
                        tryToDecodeData(index)
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        if (m_codecGeneration != currentGeneration) return
                        try {
                            codec.releaseOutputBuffer(index, true)
                        } catch (e: IllegalStateException) {
                            Log.e(m_tag, "releaseOutputBuffer failed: ${e.message}")
                        }
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        Log.d(m_tag, "Decoder output format changed: $format")
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Log.e(m_tag, "Decoder error: ${e.message}")
                        m_codecState = eCodecState.ERROR
                        reStart()
                    }
                }, m_codecHandle)

                mMediaCodec!!.configure(mediaFormat, m_Surface, null, 0)
                m_codecState = eCodecState.CONFIGURED
                mMediaCodec!!.start()
                m_codecState = eCodecState.STARTED
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }



    // This function decodes the video data frame by frame
    private fun decodeData(data: ByteArray) {
        // If VPS has not been received yet, check for VPS in the data
        if(m_codecState != eCodecState.STARTED){
            return
        }
        if(!m_isGetVPS){
            m_dataDeque.clear()
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
        tryToDecodeData(data)
    }

    private fun tryToDecodeData(data: ByteArray){
        m_decodeDataLock.withLock {
            organize_buffer(data, m_dataDeque)
            while (m_dataDeque.size > 0 && m_inputIndexDeque.size > 0) {
                val data = m_dataDeque.removeFirst()
                val inputIndex = m_inputIndexDeque.removeFirst()
                if (inputIndex >= 0) {
                    val inputBuffer = mMediaCodec!!.getInputBuffer(inputIndex)
                    inputBuffer!!.clear()
                    inputBuffer.put(data, 0, data.size)
                    mMediaCodec!!.queueInputBuffer(
                        inputIndex,
                        0,
                        data.size,
                        System.currentTimeMillis(),
                        0
                    )
                }
            }
        }
    }

    private fun tryToDecodeData(index: Int){
        m_decodeDataLock.withLock {
            m_inputIndexDeque.addLast(index)
//            while (m_dataDeque.size > 0 && m_inputIndexDeque.size > 0) {
//                val data = m_dataDeque.removeFirst()
//                val inputIndex = m_inputIndexDeque.removeFirst()
//                if (inputIndex >= 0) {
//                    val inputBuffer = mMediaCodec!!.getInputBuffer(inputIndex)
//                    inputBuffer!!.clear()
//                    inputBuffer.put(data, 0, data.size)
//                    mMediaCodec!!.queueInputBuffer(
//                        inputIndex,
//                        0,
//                        data.size,
//                        System.currentTimeMillis(),
//                        0
//                    )
//                    Sync()
//                }
//            }
        }
    }

    private fun organize_buffer(data: ByteArray, data_queue: ArrayDeque<ByteArray>){
        if(data.size < 5) return
        if(data_queue.size > 30) {
            data_queue.clear()
            reStart()
//            var type = eBufferType.BUFFER_NULL
//            if (m_parseCodec != null) {
//                type = m_parseCodec!!.getBufferType(data)
//            }
//            if (type == eBufferType.BUFFER_FLAG_CODEC_CONFIG || type == eBufferType.BUFFER_FLAG_KEY_FRAME) {
//                data_queue.clear()
//                data_queue.add(data)
//            } else {
//                data_queue.add(data)
//            }
        }
        else{
            data_queue.add(data)
        }
    }

    private fun startThread(thread: HandlerThread) : Handler{
        if(!thread.isAlive) {
            thread.start()
        }
        return Handler(thread.getLooper())
    }

    private fun reStart() {
        m_decodeDataLock.withLock {
            if(m_isCallStop){
                stopDecode()
                return
            }
            Log.d(m_tag, "reStart")
            when(m_codecState){
                eCodecState.CONFIGURED->mMediaCodec?.reset()
                eCodecState.STARTED-> {
                    mMediaCodec?.stop()
                    mMediaCodec?.release()
                }
                eCodecState.STOPPED->mMediaCodec?.release()
                eCodecState.ERROR->mMediaCodec?.release()
                eCodecState.RELEASED -> TODO()
            }
            m_codecState = eCodecState.RELEASED
            m_isGetVPS = false
            m_inputIndexDeque.clear()
            m_dataDeque.clear()
            mMediaCodec = null
            startDecode()
        }
    }

    fun stopDecode() {
        m_isCallStop = true
        if (mMediaCodec != null) {
            mMediaCodec!!.stop()
            mMediaCodec!!.release()
            mMediaCodec = null
        }
        m_codecState = eCodecState.RELEASED
        if (m_webSocketClient != null) {
            Log.d(m_tag, "stopDecodee")
            m_webSocketClient!!.close(0)
            m_webSocketClient = null
        }
        m_codecThread.quitSafely()
        m_Surface?.release()
        m_Surface = null
        m_SurfaceTexture.release()
    }

    override fun injectMotionEvent(cmotionEvent: cMotionEvent) {
        m_MotionLock.withLock {
            if (m_webSocketClient != null) {
                if (m_webSocketClient!!.getHostAddress() != null) {
                    val view_name = cmotionEvent.name
                    cmotionEvent.name = view_name + "->${m_webSocketClient!!.getHostAddress()}"
                    cmotionEvent.decoder_width = m_size.width
                    cmotionEvent.decoder_height = m_size.height
                    val motionEventBytes = Json.encodeToString(cmotionEvent).encodeToByteArray()

                    m_webSocketClient?.sendData(motionEventBytes)
                }
            }
        }
    }
    @Synchronized
    private fun onTriggerRenderCallback(surfaceTexture: cSurfaceTexture) {
        m_renderTriggered.forEach { it(surfaceTexture) }
    }

    override fun getEGLContext(): EGLContext?
    {
        return m_eglcontext
    }

    override fun getSurfaceTexture(): cSurfaceTexture{
        return m_SurfaceTexture
    }

    @Synchronized
    override fun triggerRenderSubscribe(handler: (cSurfaceTexture?) -> Unit){
        m_renderTriggered.add(handler)
    }

    @Synchronized
    override fun triggerRenderUnsubscribe(handler: (cSurfaceTexture?) -> Unit){
        m_renderTriggered.remove(handler)
    }

}