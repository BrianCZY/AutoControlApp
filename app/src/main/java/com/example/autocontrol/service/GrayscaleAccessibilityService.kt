package com.example.autocontrol.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.autocontrol.AutoControlApplication

/**
 * 灰度遮罩的无障碍服务。
 *
 * 为什么用无障碍浮窗（TYPE_ACCESSIBILITY_OVERLAY）而不是普通悬浮窗：
 * HarmonyOS 4.2 / Android 12+ 引入 BLOCK_UNTRUSTED_TOUCHES，普通 TYPE_APPLICATION_OVERLAY
 * 浮窗的触摸会被系统拦截，无法穿透到其他应用（表现为“本应用能点、其他应用点不动”）。
 * 而 TYPE_ACCESSIBILITY_OVERLAY 属于“可信窗口”，触摸穿透不受该限制——这正是屏幕滤镜 /
 * 护眼类应用在 Android 12+ 的标准做法。代价：需用户在系统设置里开启本应用的无障碍服务
 * （App 内跳转，无需连接电脑）。
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SHOW -> showOverlay()
                ACTION_HIDE -> removeOverlay()
            }
        }
    }

    companion object {
        private const val CHANNEL = "com.example.autocontrol.GRAYSCALE"
        const val ACTION_SHOW = "$CHANNEL.SHOW"
        const val ACTION_HIDE = "$CHANNEL.HIDE"

        private fun componentName(context: Context): String =
            "${context.packageName}/com.example.autocontrol.service.GrayscaleAccessibilityService"

        /** 无障碍服务是否已开启（灰度遮罩依赖此才能在其他应用上层绘制并穿透点击） */
        fun isEnabled(context: Context): Boolean {
            val expected = componentName(context)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
        }

        /** 打开无障碍设置页，引导用户开启本应用的无障碍服务（App 内跳转，无需电脑） */
        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /** 请求显示灰罩（仅当无障碍服务已启用时生效） */
        fun show(context: Context) {
            context.sendBroadcast(Intent(ACTION_SHOW).setPackage(context.packageName))
        }

        /** 请求移除灰罩 */
        fun hide(context: Context) {
            context.sendBroadcast(Intent(ACTION_HIDE).setPackage(context.packageName))
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW)
            addAction(ACTION_HIDE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
        // 服务被启用时，若灰度已开启则立即显示遮罩
        val app = application as? AutoControlApplication
        if (app?.settingsRepository?.state?.value?.grayscaleEnabled == true) {
            showOverlay()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 系统启动无障碍服务时也会回调，无需处理 action
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val wm = windowManager ?: return
        val view = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(140, 128, 128, 128))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            wm.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            // 理论上无障碍浮窗不会失败；若失败仅移除，不影响其他功能
            removeOverlay()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // 本服务仅用于绘制灰罩，不处理无障碍事件
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(commandReceiver) }
        removeOverlay()
        super.onDestroy()
    }
}
