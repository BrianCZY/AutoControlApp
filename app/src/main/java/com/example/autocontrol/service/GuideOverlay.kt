package com.example.autocontrol.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 「权限指引」普通悬浮窗通道。
 *
 * 为什么需要它：无障碍浮窗（[GrayscaleAccessibilityService]）只有在无障碍服务已开启时才能绘制，
 * 而用户第一次要开启的往往正是“无障碍”本身——那一刻服务还没跑起来，指引浮窗无处可依附。
 * 因此这里补一条基于 SYSTEM_ALERT_WINDOW 的普通悬浮窗通道，让指引在系统设置页上也能悬浮。
 *
 * 与灰罩不同，指引卡片本身需要接收点击（“知道了”），因此不带 FLAG_NOT_TOUCHABLE；
 * 但它只占屏幕底部一条，不会影响用户在系统设置页上的其余操作。
 */
internal object GuideOverlay {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var guideView: View? = null

    /** 是否已具备悬浮窗权限（可在系统设置页内直接授予） */
    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * 显示指引浮窗。
     *
     * @return true 表示已成功挂上窗口；false 表示缺少悬浮窗权限或添加失败。
     */
    fun show(context: Context, title: String, steps: String, dismissText: String): Boolean {
        if (title.isBlank()) return false
        val app = context.applicationContext
        if (!canShow(app)) return false

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return showInternal(app, title, steps, dismissText)
        }
        mainHandler.post { showInternal(app, title, steps, dismissText) }
        return true
    }

    /** 移除指引浮窗（回到本应用、或用户点“知道了”时调用） */
    fun hide(context: Context) {
        val app = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideInternal()
        } else {
            mainHandler.post { hideInternal() }
        }
    }

    private fun showInternal(context: Context, title: String, steps: String, dismissText: String): Boolean {
        val wm = windowManager
            ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.also { windowManager = it }
            ?: return false

        hideInternal()

        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val card = GuideCard.create(
            context = context,
            title = title,
            steps = steps,
            dismissText = dismissText,
            onDismiss = { hideInternal() },
        )

        val screenWidth = context.resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            (screenWidth - dp(32)).coerceAtLeast(dp(220)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 可点击但不抢焦点：不弹输入法、不影响系统页面的正常交互
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // 抬离底部手势区，避免遮挡系统导航
            y = dp(88)
        }

        return try {
            wm.addView(card, params)
            guideView = card
            true
        } catch (_: Exception) {
            // 权限刚被撤销 / 窗口令牌失效等情况：静默降级，不影响跳转本身
            hideInternal()
            false
        }
    }

    private fun hideInternal() {
        guideView?.let { view -> runCatching { windowManager?.removeView(view) } }
        guideView = null
    }
}
