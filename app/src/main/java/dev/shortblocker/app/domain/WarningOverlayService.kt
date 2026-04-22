package dev.shortblocker.app.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.graphics.drawable.Animatable
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.repeatCount
import dev.shortblocker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WarningOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var sirenJob: Job? = null
    private var gifWatchJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var imageLoader: ImageLoader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAsForegroundService()
        showOverlay()
        startSiren()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        gifWatchJob?.cancel()
        stopSiren()
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val gifSizePx = (BASE_GIF_SIZE_DP * GIF_SCALE * density).toInt()
        val sirenSizePx = (88 * density).toInt()
        val marginPx = (4 * density).toInt()
        val bottomMarginPx = (OVERLAY_BOTTOM_MARGIN_DP * density).toInt()
        val sirenOffsetPx = (SIREN_OFFSET_DP * density).toInt()

        val root = FrameLayout(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        val siren = ImageView(this).apply {
            setImageResource(R.drawable.ic_siren)
            imageTintList = null
            scaleType = ImageView.ScaleType.FIT_CENTER
            translationY = sirenOffsetPx.toFloat()
        }
        val hand = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val loader = ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
        imageLoader = loader
        loader.enqueue(
            ImageRequest.Builder(this)
                .data("file:///android_asset/$GIF_ASSET_FILE_NAME")
                .repeatCount(GIF_REPEAT_COUNT)
                .target(hand)
                .listener(
                    onSuccess = { _, result ->
                        configureGifPlayback(result)
                    },
                )
                .crossfade(false)
                .build(),
        )

        val pulse = AlphaAnimation(0.35f, 1f).apply {
            duration = 280
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        siren.startAnimation(pulse)

        content.addView(
            siren,
            LinearLayout.LayoutParams(sirenSizePx, sirenSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = marginPx
            },
        )
        content.addView(
            hand,
            LinearLayout.LayoutParams(gifSizePx, gifSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )

        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = bottomMarginPx
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager?.addView(root, params)
        overlayView = root
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        runCatching {
            windowManager?.removeView(view)
        }
        overlayView = null
        windowManager = null
        imageLoader?.shutdown()
        imageLoader = null
    }

    private fun configureGifPlayback(result: SuccessResult) {
        val drawable = result.drawable
        val animatable = drawable as? Animatable ?: run {
            stopSelf()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) {
            drawable.registerAnimationCallback(
                object : Animatable2.AnimationCallback() {
                    override fun onAnimationEnd(drawable: Drawable?) {
                        stopSelf()
                    }
                },
            )
        }
        animatable.start()
        watchGifAnimation(animatable)
    }

    private fun watchGifAnimation(animatable: Animatable) {
        gifWatchJob?.cancel()
        gifWatchJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            var hasStarted = false
            while (true) {
                val running = animatable.isRunning
                if (running) {
                    hasStarted = true
                } else if (hasStarted) {
                    stopSelf()
                    break
                }
                delay(GIF_WATCH_INTERVAL_MS)
            }
        }
    }

    private fun startSiren() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        runCatching {
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < maxMusic / 2) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic / 2, 0)
            }
        }
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
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

    private fun startAsForegroundService() {
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortblocker)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "shortblocker_overlay_service"
        private const val NOTIFICATION_ID = 1101
        private const val GIF_ASSET_FILE_NAME = "jump.gif"
        private const val GIF_REPEAT_COUNT = 1
        private const val GIF_WATCH_INTERVAL_MS = 50L
        private const val BASE_GIF_SIZE_DP = 320
        private const val GIF_SCALE = 2
        private const val OVERLAY_BOTTOM_MARGIN_DP = -32
        private const val SIREN_OFFSET_DP = 20

        fun canDrawOverlays(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        }

        fun start(context: Context) {
            val intent = Intent(context, WarningOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
