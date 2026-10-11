package com.example.autocontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.example.autocontrol.MainActivity
import com.example.autocontrol.R

/**
 * 屏幕灰度遮罩服务。
 *
 * 通过 WindowManager 在屏幕最上层绘制一块全屏半透明灰罩（TYPE_APPLICATION_OVERLAY），
 * 让“指定时段屏幕变灰”在**不依赖任何电脑授权**的情况下生效——所需权限是
 * SYSTEM_ALERT_WINDOW（显示在其他应用上层），可在系统设置页内直接点开授予。
 *
 * 与之前的 Daltonizer 方案相比：
 * - 不再需要 WRITE_SECURE_SETTINGS（该权限普通安装拿不到、只能 adb 授权）；
 * - 不再依赖辅助功能服务；
 * - 代价：这是“灰罩/灰滤镜”而非真正像素级去色，锁屏等少数界面可能覆盖不全，且略耗电。
 *
 * 服务以“前台服务”形式保活，保证整个灰度时段内遮罩不被系统回收。
 */
class GrayscaleOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null

    companion object {
        private const val CHANNEL_OVERLAY = "auto_control_overlay"
        const val NOTIFICATION_ID = 1003
        const val ACTION_STOP = "com.example.autocontrol.action.OVERLAY_STOP"

        /** 是否已获得悬浮窗权限（调用 start 前应先检查） */
        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

        /** 启动灰度遮罩（需已获得 SYSTEM_ALERT_WINDOW 权限） */
        fun start(context: Context) {
            val intent = Intent(context, GrayscaleOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                @Suppress("DEPRECATION")
                context.startService(intent)
            }
        }

        /** 停止灰度遮罩 */
        fun stop(context: Context) {
            context.stopService(Intent(context, GrayscaleOverlayService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        // 半透明灰罩：alpha≈0.55，覆盖在内容上使画面偏灰、变暗
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(140, 128, 128, 128))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            wm.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            // 没有悬浮窗权限时 addView 会抛异常：自身停止，由调用方提示用户授权
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        windowManager = null
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_OVERLAY,
                getString(R.string.notif_channel_overlay_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_overlay_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, GrayscaleOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_overlay_title))
            .setContentText(getString(R.string.notif_overlay_text))
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_overlay_stop), stopPi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
