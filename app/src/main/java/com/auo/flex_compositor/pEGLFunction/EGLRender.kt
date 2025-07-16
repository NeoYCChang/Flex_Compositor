package com.auo.flex_compositor.pEGLFunction


import android.content.Context
import android.content.res.Resources
import android.opengl.GLES11Ext
import android.opengl.GLES32
import android.util.Log
import com.auo.flex_compositor.pInterface.deWarp_Parameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class EGLRender {

    interface AUORenderCallback {
        fun onSurfaceCreatedCallback()
    }

    data class Texture_Size(var width: Int, var height: Int, var offsetX: Int, var offsetY: Int,
                            var cropWidth: Int, var cropHeight: Int)

    data class Render_Parameters(val vertices : ArrayList<Float>, val textcoods : ArrayList<Float>,
                                 var column : Int, var row : Int, var countOfTriangles: Int, var isDeWarp: Boolean,
                                var isUpdate: Boolean)

    private var index: Int = 0

    private var m_AUORenderCallback: AUORenderCallback? = null
    private var mProgram: Int = -1
    private var m_vertexShader: Int = -1
    private var m_fragmentShader: Int = -1
    private var m_context: Context? = null
    private var m_positionHandle: Int = 0
    private var m_texCoordHandle: Int = 0
    private var mVBO: Int  = -1
    private var mEBO: Int  = -1
    private val mTextureSize : Texture_Size = Texture_Size(
        960, 540, 0, 0,
        960,540
    )
    private var m_dewarpParameters: deWarp_Parameters? = null


    private val m_render_parameters : Render_Parameters = Render_Parameters(
        ArrayList<Float>(), ArrayList<Float>(), 2, 2,2, false,
        false
    )


    // Set up shaders and program (vertex shader and fragment shader)
    constructor(context : Context, dewarpParameters: deWarp_Parameters?) : super() {
        m_context = context
        m_dewarpParameters = dewarpParameters
    }

    fun onSurfaceCreated() {
        //GLES20.glEnable(GLES20.GL_TEXTURE_2D);
        // Initialize OpenGL settings (e.g., set the background color)
        GLES32.glClearColor(0.0f, 0.0f, 0.0f, 1.0f) // Set background color to black
        GLES32.glEnable(GLES32.GL_DEPTH_TEST) // Enable depth testing
        m_AUORenderCallback?.onSurfaceCreatedCallback()
        mProgram = createProgram(m_context!!.resources, "shader/vertex.glsl", "shader/fragment.glsl")
        val handles = activeHandle(mProgram, "vPosition","vCoordinate")
        m_positionHandle = handles[0]
        m_texCoordHandle = handles[1]

        if(m_dewarpParameters != null){
            // It is possible that the incorrect XML format may cause it to return false.
            // In this case, the system will switch to normal mode.
            if(!setDeWarpMode(m_render_parameters, mTextureSize, m_dewarpParameters!!)){
                setNormalMode(m_render_parameters, mTextureSize)
            }
        }
        else{
            setNormalMode(m_render_parameters, mTextureSize)
        }

        mVBO = createVBO(m_positionHandle, m_texCoordHandle, m_render_parameters)
        mEBO = createEBO(m_render_parameters)
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        // Adjust the viewport based on the new surface size
        GLES32.glViewport(0, 0, width, height)
    }

    // Implement the onDrawFrame() method
    fun onDrawFrame(textureID: Int) {
        // 1. Check if OpenGL state is properly initialized
        if (mProgram == 0 || mVBO == 0 || mEBO == 0) {
            Log.e(m_tag, "Invalid GL state: mProgram=$mProgram, mVBO=$mVBO, mEBO=$mEBO")
            return
        }

        // 2. If update flag is set, update the texture coordinates in VBO
        if (m_render_parameters.isUpdate) {
            Log.d(m_tag, "Updating texture VBO")
            updateTextureVBO(m_render_parameters, mTextureSize, mVBO)
            m_render_parameters.isUpdate = false
            Log.d(m_tag, "Texture VBO updated")
        }

        // 3. Use the shader program
        GLES32.glUseProgram(mProgram)

        // 4. Bind VBO and EBO buffers
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, mVBO)

        GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, mEBO)

        // 5. Clear screen
        GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT or GLES32.GL_DEPTH_BUFFER_BIT)
        GLES32.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        if(textureID != 0) {
            // 6. Bind texture
            GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID)
            // 7. Draw elements
            val indexCount = m_render_parameters.countOfTriangles
            if (indexCount > 0) {
                GLES32.glDrawElements(
                    GLES32.GL_TRIANGLES,
                    indexCount,
                    GLES32.GL_UNSIGNED_INT,
                    0
                )
            } else {
                Log.e(m_tag, "Invalid index count: $indexCount")
            }

            // 8. Unbind
            GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
        GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, 0)
        // 9. Finish
        GLES32.glFinish()

    }

    fun setCustomRenderCallback(callback: AUORenderCallback) {
        m_AUORenderCallback = callback
    }

    private fun createProgram(res: Resources, vertexResPath: String, fragmentResPath: String) : Int {
        return createProgram(loadSrcFromAssetFile(res, vertexResPath), loadSrcFromAssetFile(res, fragmentResPath))
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

    private fun createProgram(vertexSrcCode: String?, fragSrcCode: String?) : Int {
        //通常做法
//            String vertexSource = AssetsUtils.read(CameraGlSurfaceShowActivity.this, "vertex_texture.glsl");
//            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
//            String fragmentSource = AssetsUtils.read(CameraGlSurfaceShowActivity.this, "fragment_texture.glsl");
//            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

        m_vertexShader = loadShader(GLES32.GL_VERTEX_SHADER, vertexSrcCode!!)
        m_fragmentShader = loadShader(GLES32.GL_FRAGMENT_SHADER, fragSrcCode!!)

        // 创建空的OpenGL ES程序
        var program = GLES32.glCreateProgram()


        // 添加顶点着色器到程序中
        GLES32.glAttachShader(program, m_vertexShader)

        // 添加片段着色器到程序中
        GLES32.glAttachShader(program, m_fragmentShader)

        // 创建OpenGL ES程序可执行文件
        GLES32.glLinkProgram(program)

        return program
    }

//    private fun loadShader(type: Int, shaderCode: String): Int {
//        val shader = GLES20.glCreateShader(type)
//        // 添加上面编写的着色器代码并编译它
//        GLES20.glShaderSource(shader, shaderCode)
//        GLES20.glCompileShader(shader)
//        return shader
//    }

    private fun loadShader(type: Int, srcCode: String?): Int {
        // 创建shader
        var shader = GLES32.glCreateShader(type)
        // 加载源代码
        GLES32.glShaderSource(shader, srcCode)
        // 编译shader
        GLES32.glCompileShader(shader)
        val compiled = IntArray(1)
        // 查看编译状态
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


    private fun activeHandle(program: Int, glVarPosition: String, glVarCoordinate: String) :  IntArray {
        // Set up attributes and uniforms (code omitted for brevity)
        val handles = IntArray(2)
        handles[0] = GLES32.glGetAttribLocation(program, glVarPosition)
        handles[1] = GLES32.glGetAttribLocation(program, glVarCoordinate)

        return handles
    }

    fun setTextureSize(textureSize : Texture_Size, update: Boolean = false){
        mTextureSize.width = textureSize.width
        mTextureSize.height = textureSize.height
        mTextureSize.offsetX = textureSize.offsetX
        mTextureSize.offsetY = textureSize.offsetY
        mTextureSize.cropWidth = textureSize.cropWidth
        mTextureSize.cropHeight = textureSize.cropHeight
        m_render_parameters.isUpdate = update
    }

    fun setTextureSize(width: Int, height: Int){
        mTextureSize.width = width
        mTextureSize.height = height
    }

    fun setTextureCrop(offsetX: Int, offsetY: Int, width: Int, height: Int){
        mTextureSize.offsetX = offsetX
        mTextureSize.offsetY = offsetY
        mTextureSize.cropWidth  = width
        mTextureSize.cropHeight = height
    }

    private fun setNormalMode(renderParameters: Render_Parameters, textureSize : Texture_Size){
        renderParameters.isDeWarp = false
        renderParameters.column = 2
        renderParameters.row = 2
        renderParameters.countOfTriangles = (renderParameters.column - 1) * (renderParameters.row - 1) * 2 * 3

        val left = textureSize.offsetX.toFloat() / textureSize.width.toFloat()
        val top = textureSize.offsetY.toFloat() / textureSize.height.toFloat()
        val right = (textureSize.offsetX + textureSize.cropWidth).toFloat() / textureSize.width.toFloat()
        val bottom = (textureSize.offsetY + textureSize.cropHeight).toFloat() / textureSize.height.toFloat()

        val vertices = renderParameters.vertices
        val textcoods = renderParameters.textcoods
        vertices.clear()
        textcoods.clear()

        textcoods.add(left);textcoods.add(top) //x;y
        textcoods.add(right);textcoods.add(top)
        textcoods.add(left);textcoods.add(bottom)
        textcoods.add(right);textcoods.add(bottom)

        vertices.add(-1.0f);vertices.add(1.0f);vertices.add(0.0f) //x;y;z
        vertices.add(1.0f);vertices.add(1.0f);vertices.add(0.0f)
        vertices.add(-1.0f);vertices.add(-1.0f);vertices.add(0.0f)
        vertices.add(1.0f);vertices.add(-1.0f);vertices.add(0.0f)

    }

    private fun setDeWarpMode(renderParameters: Render_Parameters, textureSize : Texture_Size,
                                dewarpParameters: deWarp_Parameters): Boolean{
        val left = textureSize.offsetX.toFloat() / textureSize.width.toFloat()
        val top = textureSize.offsetY.toFloat() / textureSize.height.toFloat()
        val right = (textureSize.offsetX + textureSize.cropWidth).toFloat() / textureSize.width.toFloat()
        val bottom = (textureSize.offsetY + textureSize.cropHeight).toFloat() / textureSize.height.toFloat()
        if( dewarpParameters.column >= 2 && dewarpParameters.row >= 2) {
            renderParameters.column = dewarpParameters.column
            renderParameters.row = dewarpParameters.row
            renderParameters.countOfTriangles = (renderParameters.column - 1) * (renderParameters.row - 1) * 2 * 3
            val vertices = renderParameters.vertices
            val textcoods = renderParameters.textcoods
            vertices.clear()
            textcoods.clear()
            var vertex_offset = 0
            for (i in 0 until (renderParameters.column * renderParameters.row)) {
                if (vertex_offset < dewarpParameters.vertices.size) {
                    var x = dewarpParameters.vertices[vertex_offset];vertex_offset++
                    x = x * 2.0f - 1.0f
                    var y = dewarpParameters.vertices[vertex_offset];vertex_offset++
                    y = 1.0f - y * 2.0f
                    vertices.add(x)
                    vertices.add(y)
                    vertices.add(0.0f);
                } else {
                    vertices.add(0.0f);vertices.add(0.0f);vertices.add(0.0f);
                }
                val texture_x =
                    1.0f * (i % renderParameters.column).toFloat() / (renderParameters.column-1).toFloat() * (right - left) + left
                val texture_y =
                    1.0f * (i / renderParameters.column).toFloat() / (renderParameters.row-1).toFloat() * (bottom - top) + top

                textcoods.add(texture_x);textcoods.add(texture_y) // x;y
            }
        }else return false
        renderParameters.isDeWarp = true
        return true
    }

    private fun updateTextureVBO(renderParameters: Render_Parameters, textureSize : Texture_Size, vbo: Int){
        val left = textureSize.offsetX.toFloat() / textureSize.width.toFloat()
        val top = textureSize.offsetY.toFloat() / textureSize.height.toFloat()
        val right = (textureSize.offsetX + textureSize.cropWidth).toFloat() / textureSize.width.toFloat()
        val bottom = (textureSize.offsetY + textureSize.cropHeight).toFloat() / textureSize.height.toFloat()
        val textcoods = renderParameters.textcoods
        if(renderParameters.isDeWarp){
            if(textcoods.size == (renderParameters.column * renderParameters.row) * 2) {
                for (i in 0 until (renderParameters.column * renderParameters.row)) {
                    val texture_x =
                        1.0f * (i % renderParameters.column).toFloat() / (renderParameters.column - 1).toFloat() * (right - left) + left
                    val texture_y =
                        1.0f * (i / renderParameters.column).toFloat() / (renderParameters.row - 1).toFloat() * (bottom - top) + top

                    textcoods[i*2] = (texture_x);textcoods[i*2+1] = (texture_y) // x;y
                }
            }
            else{
                return
            }
        }
        else
        {
            if(textcoods.size == 8) {
                textcoods[0] = (left);textcoods[1] = (top) //x;y
                textcoods[2] = (right);textcoods[3] = (top)
                textcoods[4] = (left);textcoods[5] = (bottom)
                textcoods[6] = (right);textcoods[7] = (bottom)
            }
            else{
                return
            }
        }

        val textcoodBuffer = ByteBuffer.allocateDirect(textcoods.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        textcoodBuffer.put(textcoods.toFloatArray())
        textcoodBuffer.position(0)

        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo)
        var offset = renderParameters.vertices.size  * 4
        GLES32.glBufferSubData(GLES32.GL_ARRAY_BUFFER, offset, textcoods.size * 4, textcoodBuffer)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)
    }

    private fun setDeWarpMode(resources: Resources, dewarp_file: String, renderParameters: Render_Parameters,
                              textureSize : Texture_Size): Boolean{
        val xml_file = loadSrcFromAssetFile(resources, dewarp_file)
        if(xml_file != null){
            val Document = parseXml(xml_file)
            return extractDeWarp(Document, renderParameters, textureSize)
        }
        return false
    }

    fun parseXml(xmlContent: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val inputSource = InputSource(StringReader(xmlContent))
        return builder.parse(inputSource)
    }

    fun extractDeWarp(document: Document, renderParameters: Render_Parameters,
                      textureSize : Texture_Size) : Boolean {

        val left = textureSize.offsetX.toFloat() / textureSize.width.toFloat()
        val top = textureSize.offsetY.toFloat() / textureSize.height.toFloat()
        val right = (textureSize.offsetX + textureSize.cropWidth).toFloat() / textureSize.width.toFloat()
        val bottom = (textureSize.offsetY + textureSize.cropHeight).toFloat() / textureSize.height.toFloat()

        val nodeList = document.getElementsByTagName("output")

        if(nodeList.length > 0){
            val outputNode = nodeList.item(0) as org.w3c.dom.Element
            val column = outputNode.attributes.getNamedItem("column").nodeValue.toIntOrNull()
            val row = outputNode.attributes.getNamedItem("row").nodeValue.toIntOrNull()
            val vertexNodeList = outputNode.getElementsByTagName("vertex")
            if(column != null && row != null){
                if( column >= 2 && row >= 2) {
                    renderParameters.column = column
                    renderParameters.row = row
                    renderParameters.countOfTriangles = (renderParameters.column - 1) * (renderParameters.row - 1) * 2 * 3
                    val vertices = renderParameters.vertices
                    val textcoods = renderParameters.textcoods
                    vertices.clear()
                    textcoods.clear()
                    for (i in 0 until (column * row)) {
                        if (i < vertexNodeList.length) {
                            val vertexNode = vertexNodeList.item(i)
                            var x =
                                vertexNode.attributes.getNamedItem("x").nodeValue.toFloatOrNull()
                            var y =
                                vertexNode.attributes.getNamedItem("y").nodeValue.toFloatOrNull()
                            if (x != null && y != null) {
                                x = x * 2.0f - 1.0f
                                y = 1.0f - y * 2.0f
                                vertices.add(x);vertices.add(y);vertices.add(0.0f); //x;y;z
                            } else {
                                vertices.add(0.0f);vertices.add(0.0f);vertices.add(0.0f);
                            }
                        } else {
                            vertices.add(0.0f);vertices.add(0.0f);vertices.add(0.0f);
                        }

                        val texture_x =
                            1.0f * (i % column).toFloat() / (column-1).toFloat() * (right - left) + left
                        val texture_y =
                            1.0f * (i / column).toFloat() / (row-1).toFloat() * (bottom - top) + top

                        textcoods.add(texture_x);textcoods.add(texture_y) // x;y
                    }
                }else return false
            }else return false
        }
        else return false
        renderParameters.isDeWarp = true
        return true

//        for (i in 0 until nodeList.length) {
//            val bookNode = nodeList.item(i)
//
//            if (bookNode.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
//                val bookElement = bookNode as org.w3c.dom.Element
//                val title = bookElement.getElementsByTagName("title").item(0).textContent
//                val author = bookElement.getElementsByTagName("author").item(0).textContent
//                val year = bookElement.getElementsByTagName("year").item(0).textContent
//
//                println("Title: $title, Author: $author, Year: $year")
//            }
//        }
    }

    companion object {
        private val m_tag = "AUORender"
        fun createOESTextureObject(): Int {
            val tex = IntArray(1)

            //Generate a texture
            GLES32.glGenTextures(1, tex, 0)

            //Bind this texture to the external texture.
            GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[0])

            //Set texture filtering parameters.
            GLES32.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES32.GL_TEXTURE_MIN_FILTER, GLES32.GL_NEAREST.toFloat()
            )
            GLES32.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES32.GL_TEXTURE_MAG_FILTER, GLES32.GL_LINEAR.toFloat()
            )
            GLES32.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES32.GL_TEXTURE_WRAP_S, GLES32.GL_CLAMP_TO_EDGE.toFloat()
            )
            GLES32.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES32.GL_TEXTURE_WRAP_T, GLES32.GL_CLAMP_TO_EDGE.toFloat()
            )
            GLES32.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            Log.d(m_tag, "createOESTextureObject")
            return tex[0]
        }
    }

    private fun createVBO(positionHandle: Int, texCoordHandle: Int, renderParameters : Render_Parameters) : Int {
        val vbo = IntArray(1)
        val vertices = renderParameters.vertices
        val textcoods = renderParameters.textcoods
        // Create direct ByteBuffers for vertices and texture coordinates
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices.toFloatArray())
        vertexBuffer.position(0)

        val textcoodBuffer = ByteBuffer.allocateDirect(textcoods.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        textcoodBuffer.put(textcoods.toFloatArray())
        textcoodBuffer.position(0)

        GLES32.glGenBuffers(1, vbo, 0)
        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, vbo[0])
        GLES32.glBufferData(GLES32.GL_ARRAY_BUFFER, (vertices.size + textcoods.size) * 4,
            null, GLES32.GL_STATIC_DRAW)
        var offset = 0
        GLES32.glBufferSubData(GLES32.GL_ARRAY_BUFFER, offset, vertices.size * 4, vertexBuffer)
        offset += vertices.size  * 4
        GLES32.glBufferSubData(GLES32.GL_ARRAY_BUFFER, offset, textcoods.size * 4, textcoodBuffer)

        GLES32.glEnableVertexAttribArray(positionHandle)
        GLES32.glEnableVertexAttribArray(texCoordHandle)
        GLES32.glVertexAttribPointer(positionHandle, 3, GLES32.GL_FLOAT, false, 12, 0)
        GLES32.glVertexAttribPointer(texCoordHandle, 2, GLES32.GL_FLOAT, false, 8, offset)

        GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, 0)

        return vbo[0]
    }

    private fun createEBO(renderParameters : Render_Parameters) : Int {
        val ebo = IntArray(1)

        val indeices = ArrayList<Int>()
        val column = renderParameters.column
        val row = renderParameters.row


        for(i in 0 until (row-1)){
            for(j in 0 until (column-1)){
                val index1 = j + i * column
                val index2 = j + (i+1) * column
                val index3 = (j+1) + i * column
                val index4 = (j+1) + (i+1) * column
                indeices.add(index1);indeices.add(index2);indeices.add(index3) //first triangle
                indeices.add(index2);indeices.add(index4);indeices.add(index3) //second triangle
            }
        }

        val indexBuffer = ByteBuffer.allocateDirect(indeices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        indexBuffer.put(indeices.toIntArray())
        indexBuffer.position(0)

        GLES32.glGenBuffers(1, ebo, 0)
        GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, ebo[0])

        GLES32.glBufferData(GLES32.GL_ELEMENT_ARRAY_BUFFER, indeices.size * 4,
            indexBuffer, GLES32.GL_STATIC_DRAW)

        GLES32.glBindBuffer(GLES32.GL_ELEMENT_ARRAY_BUFFER, 0)

        return ebo[0]
    }

    fun release(){
        Log.d(m_tag, "release")
        var array = intArrayOf(mVBO)
        GLES32.glDeleteBuffers(1, array, 0)
        array = intArrayOf(mEBO)
        GLES32.glDeleteBuffers(1, array, 0)
        GLES32.glDeleteProgram(mProgram)
        GLES32.glDeleteShader(m_vertexShader)
        GLES32.glDeleteShader(m_fragmentShader)
    }
}