package dev.shortblocker.app.domain

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import dev.shortblocker.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WarningOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var autoDismissJob: Job? = null
    private var sirenJob: Job? = null
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
        scheduleAutoDismiss()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        autoDismissJob?.cancel()
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        val siren = ImageView(this).apply {
            setImageResource(R.drawable.ic_siren)
            imageTintList = null
            scaleType = ImageView.ScaleType.FIT_CENTER
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
                .data("file:///android_asset/jump.gif")
                .target(hand)
                .crossfade(false)
                .build(),
        )

        val pulse = AlphaAnimation(0.35f, 1f).apply {
            duration = 280
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        siren.startAnimation(pulse)

        root.addView(
            siren,
            LinearLayout.LayoutParams(sirenSizePx, sirenSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = marginPx
            },
        )
        root.addView(
            hand,
            LinearLayout.LayoutParams(gifSizePx, gifSizePx).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (500 * density).toInt()
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

    private fun scheduleAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            delay(AUTO_DISMISS_MS)
            stopSelf()
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
        private const val AUTO_DISMISS_MS = 7_000L
        private const val CHANNEL_ID = "shortblocker_overlay_service"
        private const val NOTIFICATION_ID = 1101
        private const val BASE_GIF_SIZE_DP = 320
        private const val GIF_SCALE = 2

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
