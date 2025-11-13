package com.auo.flex_compositor.pEGLFunction

import android.content.Context
import android.content.res.Resources
import android.opengl.GLES32
import android.util.Log
import com.auo.flex_compositor.pEGLFunction.EGLRender.Companion
import com.auo.flex_compositor.pEGLFunction.EGLRender.Render_Parameters
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EGLRenderPGB {

    private val m_tag = "EGLRenderPGB"
    private var mProgram: Int = -1
    private var m_vertexShader: Int = -1
    private var m_fragmentShader: Int = -1
    private var m_context: Context? = null
    private var m_positionHandle: Int = 0
    private var m_uCenterLocation : Int = 0
    private var m_uAngleLocation : Int = 0
    private var m_angle : Float = 0.0f
    private var mVBO: Int  = -1

    constructor(context : Context) {
        init_shader(context)
    }

    private fun init_shader(context : Context){
        m_context = context
        mProgram = createProgram(m_context!!.resources, "shader/vertex_pgb.glsl", "shader/fragment_pgb.glsl")
        m_positionHandle = GLES32.glGetAttribLocation(mProgram, "vPosition")
        m_uCenterLocation = GLES32.glGetUniformLocation(mProgram, "uCenter")
        m_uAngleLocation = GLES32.glGetUniformLocation(mProgram, "uAngle")
        mVBO = createVBO(m_positionHandle)
    }

    fun sizeChange(width: Float, height: Float){
        if (mProgram == -1  || m_uCenterLocation == -1) {
            Log.e(m_tag, "Invalid GL state: mProgram=$mProgram, m_uCenterLocation=$m_uCenterLocation")
            return
        }
        GLES32.glUseProgram(mProgram)
        GLES32.glUniform2f(m_uCenterLocation, width/2, height/2)
        GLES32.glFinish()
    }

    fun draw(){
        if (mProgram == -1 || mVBO == -1 || m_uCenterLocation == -1 || m_uAngleLocation == -1) {
            Log.e(m_tag, "Invalid GL state: mProgram=$mProgram, mVBO=$mVBO, m_uCenterLocation=$m_uCenterLocation, m_uAngleLocation=$m_uAngleLocation")
            return
        }
        GLES32.glEnable(GLES32.GL_BLEND)
        GLES32.glBlendFunc(GLES32.GL_SRC_ALPHA, GLES32.GL_ONE_MINUS_SRC_ALPHA)
        GLES32.glUseProgram(mProgram)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, mVBO)
        incrementAngle()
        GLES32.glDrawArrays(
            GLES32.GL_TRIANGLE_STRIP, 0, 4
        )

        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)

        GLES32.glFinish()
    }

    /** circle angle
            ℼ/2
           *****
        ***     ***
     ℼ **         ** 0
        ***     ***
           *****
            -ℼ/2
     */
    private fun incrementAngle(){
        m_angle+=0.05f
        if(m_angle > 3.14){
            m_angle-=6.24f
        }
        GLES32.glUniform1f(m_uAngleLocation, m_angle)
    }

    private fun createProgram(res: Resources, vertexResPath: String, fragmentResPath: String) : Int {
        return createProgram(loadSrcFromAssetFile(res, vertexResPath), loadSrcFromAssetFile(res, fragmentResPath))
    }

    private fun createProgram(vertexSrcCode: String?, fragSrcCode: String?) : Int {

        m_vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexSrcCode!!)
        m_fragmentShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragSrcCode!!)

        var program = GLES32.glCreateProgram()
        GLES32.glAttachShader(program, m_vertexShader)
        GLES32.glAttachShader(program, m_fragmentShader)
        GLES32.glLinkProgram(program)

        return program
    }

    private fun loadSrcFromAssetFile(resources: Resources, shaderNamePath: String): String? {
        val result = java.lang.StringBuilder()
        try {
            val stream = resources.assets.open(shaderNamePath)
            var ch: Int
            val buffer = ByteArray(1024)
            while (-1 != (stream.read(buffer).also { ch = it })) {
                result.append(String(buffer, 0, ch))
            }
        } catch (e: java.lang.Exception) {
            return null
        }
        //        return result.toString().replaceAll("\\r\\n","\n");
        return result.toString().replace("\\r\\n".toRegex(), "\n")
    }

    private fun loadShader(type: Int, srcCode: String?): Int {
        var shader = GLES32.glCreateShader(type)
        GLES32.glShaderSource(shader, srcCode)
        GLES32.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES32.glGetShaderiv(shader, GLES32.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(
                m_tag, ("Could not compile shader:" + shader
                        + " type = " + (if (type == GLES32.GL_VERTEX_SHADER) "GL_VERTEX_SHADER" else "GL_FRAGMENT_SHADER"))
            )
            Log.e(m_tag, "GLES20 Error:" + GLES32.glGetShaderInfoLog(shader))
            GLES32.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }


    private fun createVBO(positionHandle: Int) : Int {
        val vbo = IntArray(1)
        val vertices : ArrayList<Float> = ArrayList<Float>()

        vertices.add(-1.0f);vertices.add(1.0f);vertices.add(0.0f) //x;y;z
        vertices.add(1.0f);vertices.add(1.0f);vertices.add(0.0f)
        vertices.add(-1.0f);vertices.add(-1.0f);vertices.add(0.0f)
        vertices.add(1.0f);vertices.add(-1.0f);vertices.add(0.0f)

        // Create direct ByteBuffers for vertices and texture coordinates
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices.toFloatArray())
        vertexBuffer.position(0)

        GLES32.glGenBuffers(1, vbo, 0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo[0])
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, (vertices.size) * 4,
            vertexBuffer, GLES32.GL_STATIC_DRAW)

        GLES32.glEnableVertexAttribArray(positionHandle)
        GLES32.glVertexAttribPointer(positionHandle, 3, GLES32.GL_FLOAT, false, 12, 0)

        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)

        return vbo[0]
    }
}