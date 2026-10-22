package com.example.autocontrol.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
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
 *
 * 注：Play 策略检查会提示 AccessibilityService 的使用需符合政策（需在 Play 控制台完成无障碍声明）。
 * 本应用用作"屏幕灰度滤镜/护眼"，不读取任何窗口内容、不绕过隐私控制，故按 id 抑制该提示。
 */
@Suppress("AccessibilityPolicy")
class GrayscaleAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    /** 权限指引浮窗（小卡片，位于系统设置页之上，可点击关闭，不遮挡全屏操作） */
    private var guideView: View? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_SHOW -> showOverlay()
                ACTION_HIDE -> removeOverlay()
                ACTION_GUIDE_SHOW -> showGuideOverlay(
                    title = intent.getStringExtra(EXTRA_GUIDE_TITLE).orEmpty(),
                    steps = intent.getStringExtra(EXTRA_GUIDE_STEPS).orEmpty(),
                    dismissText = intent.getStringExtra(EXTRA_GUIDE_DISMISS).orEmpty(),
                )
                ACTION_GUIDE_HIDE -> removeGuideOverlay()
            }
        }
    }

    companion object {
        private const val CHANNEL = "com.example.autocontrol.GRAYSCALE"
        const val ACTION_SHOW = "$CHANNEL.SHOW"
        const val ACTION_HIDE = "$CHANNEL.HIDE"
        const val ACTION_GUIDE_SHOW = "$CHANNEL.GUIDE_SHOW"
        const val ACTION_GUIDE_HIDE = "$CHANNEL.GUIDE_HIDE"

        const val EXTRA_GUIDE_TITLE = "guide_title"
        const val EXTRA_GUIDE_STEPS = "guide_steps"
        const val EXTRA_GUIDE_DISMISS = "guide_dismiss"

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

        /**
         * 在系统设置页之上显示“权限指引”浮窗。
         * 指引卡片尺寸较小、可点击关闭，只占屏幕底部一条区域，不影响用户操作其余界面。
         * 仅在无障碍服务已开启时有效（服务未开启时无窗口可依附）。
         */
        fun showGuide(context: Context, title: String, steps: String, dismissText: String) {
            val intent = Intent(ACTION_GUIDE_SHOW).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_GUIDE_TITLE, title)
                putExtra(EXTRA_GUIDE_STEPS, steps)
                putExtra(EXTRA_GUIDE_DISMISS, dismissText)
            }
            context.sendBroadcast(intent)
        }

        /** 移除权限指引浮窗（例如用户已回到本应用） */
        fun hideGuide(context: Context) {
            context.sendBroadcast(Intent(ACTION_GUIDE_HIDE).setPackage(context.packageName))
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter().apply {
            addAction(ACTION_SHOW)
            addAction(ACTION_HIDE)
            addAction(ACTION_GUIDE_SHOW)
            addAction(ACTION_GUIDE_HIDE)
        }
        // 广播仅应用内部收发，显式声明 NOT_EXPORTED；Android 13 以下无该参数，走旧重载。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
        // 服务被启用/重连时按「开关 + 当前时段」决定是否显示：
        // 只有在时段内才出灰罩，避免用户一开开关就整屏变灰。
        val app = application as? AutoControlApplication
        val state = app?.settingsRepository?.state?.value
        if (state != null && GrayscaleSchedule.shouldShow(state)) {
            showOverlay()
        } else {
            removeOverlay()
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
        } catch (_: Exception) {
            // 理论上无障碍浮窗不会失败；若失败仅移除，不影响其他功能
            removeOverlay()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    /**
     * 绘制“权限指引”小卡片浮窗。
     *
     * 与灰罩不同，指引卡片需要可点击（“知道了”按钮），因此不带 FLAG_NOT_TOUCHABLE；
     * 但窗口尺寸只占屏幕底部一条（宽度=屏宽-32dp、高度包裹内容），
     * 其余区域的触摸仍会正常落到系统设置页上，不会影响用户操作。
     */
    private fun showGuideOverlay(title: String, steps: String, dismissText: String) {
        val wm = windowManager ?: return
        removeGuideOverlay()
        if (title.isBlank()) return

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val card = GuideCard.create(
            context = this,
            title = title,
            steps = steps,
            dismissText = dismissText,
            onDismiss = { removeGuideOverlay() },
        )

        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            (screenWidth - dp(32)).coerceAtLeast(dp(220)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 可点击但不抢焦点：不弹输入法、不影响系统页面的正常交互
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // 抬离底部手势区，避免遮挡系统导航
            y = dp(88)
        }
        try {
            wm.addView(card, params)
            guideView = card
        } catch (_: Exception) {
            removeGuideOverlay()
        }
    }

    private fun removeGuideOverlay() {
        guideView?.let { view -> runCatching { windowManager?.removeView(view) } }
        guideView = null
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
        removeGuideOverlay()
        super.onDestroy()
    }
}
