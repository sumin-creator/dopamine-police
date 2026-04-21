package dev.shortblocker.app

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.shortblocker.app.data.WarningLevel
import dev.shortblocker.app.ui.theme.ShortblockerTheme
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberModelInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class Intervention3dActivity : ComponentActivity() {
    private var autoDismissJob: Job? = null
    private var sirenJob: Job? = null
    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        startSiren()
        autoDismissJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            delay(AUTO_DISMISS_MS)
            finish()
        }
        setContent {
            ShortblockerTheme {
                Intervention3dScreen(
                    appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "Shortblocker" },
                    dialogue = intent.getStringExtra(EXTRA_DIALOGUE).orEmpty().ifBlank { "そろそろ休もう。" },
                    score = intent.getIntExtra(EXTRA_SCORE, 0),
                    warningLevel = WarningLevel.fromName(intent.getStringExtra(EXTRA_WARNING_LEVEL)),
                    hasGlb = hasAsset(assets, DEFAULT_GLB_ASSET_PATH),
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        autoDismissJob?.cancel()
        stopSiren()
        super.onDestroy()
    }

    private fun startSiren() {
        toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        sirenJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 320)
                delay(350)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_HIGH_PBX_L, 320)
                delay(350)
            }
        }
    }

    private fun stopSiren() {
        sirenJob?.cancel()
        sirenJob = null
        toneGenerator?.release()
        toneGenerator = null
    }

    companion object {
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_DIALOGUE = "extra_dialogue"
        private const val EXTRA_SCORE = "extra_score"
        private const val EXTRA_WARNING_LEVEL = "extra_warning_level"
        private const val AUTO_DISMISS_MS = 7_000L
        private const val DEFAULT_GLB_ASSET_PATH = "models/intervention_character.glb"

        fun intent(
            context: Context,
            appName: String,
            dialogue: String,
            score: Int,
            warningLevel: String,
        ): Intent = Intent(context, Intervention3dActivity::class.java).apply {
            putExtra(EXTRA_APP_NAME, appName)
            putExtra(EXTRA_DIALOGUE, dialogue)
            putExtra(EXTRA_SCORE, score)
            putExtra(EXTRA_WARNING_LEVEL, warningLevel)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
    }
}

private fun hasAsset(assetManager: AssetManager, path: String): Boolean {
    return runCatching {
        assetManager.open(path).use { true }
    }.getOrElse { false }
}

@Composable
private fun Intervention3dScreen(
    appName: String,
    dialogue: String,
    score: Int,
    warningLevel: WarningLevel,
    hasGlb: Boolean,
    onClose: () -> Unit,
) {
    val style = WarningStyle.fromWarningLevel(warningLevel)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = style.backgroundGradient,
                ),
            ),
    ) {
        SirenOverlay(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )
        AndroidMascot3dView(
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp),
            style = style,
            useGlb = false,
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "$appName / ${warningLevel.label} / score $score",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = dialogue,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE2E8FF),
            )
            Button(onClick = onClose) {
                Text("とじる")
            }
        }
    }
}

@Composable
private fun SirenOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "siren")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 280),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sirenAlpha",
    )
    Icon(
        painter = painterResource(id = R.drawable.ic_siren),
        contentDescription = "Siren",
        tint = Color.Unspecified,
        modifier = modifier
            .size(88.dp)
            .graphicsLayer(alpha = alpha),
    )
}

@Composable
private fun AndroidMascot3dView(
    modifier: Modifier,
    style: WarningStyle,
    useGlb: Boolean,
) {
    Image(
        painter = painterResource(id = R.drawable.hand),
        contentDescription = "Warning hand image",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

@Composable
private fun GlbMascot3dView(
    modifier: Modifier,
    style: WarningStyle,
) {
    val modelPath = remember { "models/intervention_character.glb" }
    SceneView(modifier = modifier) {
        rememberModelInstance(modelLoader, modelPath)?.let { instance ->
            ModelNode(
                modelInstance = instance,
                scaleToUnits = style.glbScaleToUnits,
                autoAnimate = true,
            )
        }
    }
}

private class MascotGlSurfaceView(context: Context, style: WarningStyle) : GLSurfaceView(context) {
    private val renderer = MascotRenderer(style)

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}

private class MascotRenderer(private val style: WarningStyle) : GLSurfaceView.Renderer {
    private lateinit var cube: CubeMesh
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var angle = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(style.clearColorRed, style.clearColorGreen, style.clearColorBlue, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        cube = CubeMesh(style)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projection, 0, 55f, ratio, 0.1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 0.5f, 4f, 0f, 0f, 0f, 0f, 1f, 0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setIdentityM(model, 0)
        angle += style.rotationSpeed
        val bob = sin(Math.toRadians((angle * style.bounceSpeedMultiplier).toDouble())).toFloat() * style.bounceAmplitude
        Matrix.translateM(model, 0, 0f, bob, 0f)
        Matrix.rotateM(model, 0, angle * style.rotationYawFactor, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, cos(Math.toRadians(angle.toDouble())).toFloat() * style.rotationPitchFactor, 1f, 0f, 0f)
        Matrix.scaleM(model, 0, style.scale, style.scale, style.scale)

        val vp = FloatArray(16)
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        cube.draw(mvp)
    }
}

private class CubeMesh(style: WarningStyle) {
    private val vertexShaderCode = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        attribute vec3 aColor;
        varying vec3 vColor;
        void main() {
          vColor = aColor;
          gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec3 vColor;
        void main() {
          gl_FragColor = vec4(vColor, 1.0);
        }
    """.trimIndent()

    private val vertices = floatArrayOf(
        -1f, 1f, 1f, style.primaryColorRed, style.primaryColorGreen, style.primaryColorBlue,
        -1f, -1f, 1f, style.primaryColorRed, style.primaryColorGreen, style.primaryColorBlue,
        1f, -1f, 1f, style.primaryColorRed, style.primaryColorGreen, style.primaryColorBlue,
        1f, 1f, 1f, style.primaryColorRed, style.primaryColorGreen, style.primaryColorBlue,
        -1f, 1f, -1f, style.secondaryColorRed, style.secondaryColorGreen, style.secondaryColorBlue,
        -1f, -1f, -1f, style.secondaryColorRed, style.secondaryColorGreen, style.secondaryColorBlue,
        1f, -1f, -1f, style.secondaryColorRed, style.secondaryColorGreen, style.secondaryColorBlue,
        1f, 1f, -1f, style.secondaryColorRed, style.secondaryColorGreen, style.secondaryColorBlue,
    )

    private val index = shortArrayOf(
        0, 1, 2, 0, 2, 3,
        7, 6, 5, 7, 5, 4,
        4, 5, 1, 4, 1, 0,
        3, 2, 6, 3, 6, 7,
        4, 0, 3, 4, 3, 7,
        1, 5, 6, 1, 6, 2,
    )

    private val vertexBuffer = java.nio.ByteBuffer
        .allocateDirect(vertices.size * 4)
        .order(java.nio.ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(vertices)
            position(0)
        }
    private val indexBuffer = java.nio.ByteBuffer
        .allocateDirect(index.size * 2)
        .order(java.nio.ByteOrder.nativeOrder())
        .asShortBuffer()
        .apply {
            put(index)
            position(0)
        }

    private val program: Int
    private val mvpHandle: Int
    private val positionHandle: Int
    private val colorHandle: Int

    init {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also { programId ->
            GLES20.glAttachShader(programId, vertexShader)
            GLES20.glAttachShader(programId, fragmentShader)
            GLES20.glLinkProgram(programId)
        }
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvp")
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
    }

    fun draw(mvp: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 24, vertexBuffer)

        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 24, vertexBuffer)

        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            index.size,
            GLES20.GL_UNSIGNED_SHORT,
            indexBuffer,
        )

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
    }
}

private data class WarningStyle(
    val backgroundGradient: List<Color>,
    val clearColorRed: Float,
    val clearColorGreen: Float,
    val clearColorBlue: Float,
    val primaryColorRed: Float,
    val primaryColorGreen: Float,
    val primaryColorBlue: Float,
    val secondaryColorRed: Float,
    val secondaryColorGreen: Float,
    val secondaryColorBlue: Float,
    val rotationSpeed: Float,
    val bounceAmplitude: Float,
    val bounceSpeedMultiplier: Float,
    val rotationYawFactor: Float,
    val rotationPitchFactor: Float,
    val scale: Float,
    val glbScaleToUnits: Float,
) {
    companion object {
        fun fromWarningLevel(level: WarningLevel): WarningStyle = when (level) {
            WarningLevel.LIGHT -> WarningStyle(
                backgroundGradient = listOf(Color(0xFF0D1A26), Color(0xFF13344D), Color(0xFF1F5270)),
                clearColorRed = 0.06f,
                clearColorGreen = 0.13f,
                clearColorBlue = 0.20f,
                primaryColorRed = 0.52f,
                primaryColorGreen = 0.88f,
                primaryColorBlue = 1.00f,
                secondaryColorRed = 0.32f,
                secondaryColorGreen = 0.68f,
                secondaryColorBlue = 0.92f,
                rotationSpeed = 0.9f,
                bounceAmplitude = 0.10f,
                bounceSpeedMultiplier = 0.9f,
                rotationYawFactor = 0.7f,
                rotationPitchFactor = 6f,
                scale = 1.05f,
                glbScaleToUnits = 0.9f,
            )

            WarningLevel.MEDIUM -> WarningStyle(
                backgroundGradient = listOf(Color(0xFF1A1028), Color(0xFF3A1F59), Color(0xFF5A2F82)),
                clearColorRed = 0.11f,
                clearColorGreen = 0.06f,
                clearColorBlue = 0.18f,
                primaryColorRed = 0.95f,
                primaryColorGreen = 0.62f,
                primaryColorBlue = 1.00f,
                secondaryColorRed = 0.72f,
                secondaryColorGreen = 0.40f,
                secondaryColorBlue = 0.95f,
                rotationSpeed = 1.3f,
                bounceAmplitude = 0.15f,
                bounceSpeedMultiplier = 1.2f,
                rotationYawFactor = 0.95f,
                rotationPitchFactor = 9f,
                scale = 1.2f,
                glbScaleToUnits = 1.0f,
            )

            WarningLevel.STRONG -> WarningStyle(
                backgroundGradient = listOf(Color(0xFF2A0F10), Color(0xFF5B1A1D), Color(0xFF8F2328)),
                clearColorRed = 0.16f,
                clearColorGreen = 0.05f,
                clearColorBlue = 0.07f,
                primaryColorRed = 1.00f,
                primaryColorGreen = 0.45f,
                primaryColorBlue = 0.48f,
                secondaryColorRed = 0.92f,
                secondaryColorGreen = 0.25f,
                secondaryColorBlue = 0.35f,
                rotationSpeed = 1.9f,
                bounceAmplitude = 0.20f,
                bounceSpeedMultiplier = 1.6f,
                rotationYawFactor = 1.2f,
                rotationPitchFactor = 13f,
                scale = 1.35f,
                glbScaleToUnits = 1.1f,
            )

            WarningLevel.WATCH -> WarningStyle(
                backgroundGradient = listOf(Color(0xFF10121B), Color(0xFF161D2E), Color(0xFF242E4A)),
                clearColorRed = 0.08f,
                clearColorGreen = 0.10f,
                clearColorBlue = 0.16f,
                primaryColorRed = 1.00f,
                primaryColorGreen = 0.68f,
                primaryColorBlue = 0.82f,
                secondaryColorRed = 0.65f,
                secondaryColorGreen = 0.78f,
                secondaryColorBlue = 1.00f,
                rotationSpeed = 1.1f,
                bounceAmplitude = 0.12f,
                bounceSpeedMultiplier = 1.0f,
                rotationYawFactor = 0.9f,
                rotationPitchFactor = 8f,
                scale = 1.1f,
                glbScaleToUnits = 0.95f,
            )
        }
    }
}
