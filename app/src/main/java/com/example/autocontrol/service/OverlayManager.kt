package com.example.autocontrol.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.autocontrol.AutoControlApplication
import com.example.autocontrol.data.ControlState

/**
 * 灰度遮罩（悬浮窗通道，不依赖无障碍服务）。
 *
 * ## 为什么改成悬浮窗
 *
 * 无障碍服务是为「读取/操控他人界面」设计的特权，系统会把它当成高风险能力：
 * **应用被强制停止或覆盖安装后，system_server 会直接把该服务从已启用列表移除**，
 * 用户必须重新手动开启。对一个只是"给屏幕加一层灰色滤镜"的功能来说，这个代价太重。
 *
 * 而 `TYPE_APPLICATION_OVERLAY` 只需要 `SYSTEM_ALERT_WINDOW`（"显示在其他应用上层"）：
 * - 授权一次长期有效，**强制停止/覆盖安装都不会撤销**；
 * - 属于普通悬浮窗，不是特权服务，用户心理负担小。
 *
 * ## 层级是否够高（关键）
 * 窗口层级数值：`TYPE_APPLICATION_OVERLAY`=2038 > `TYPE_NAVIGATION_BAR`=2019
 * > `TYPE_STATUS_BAR`=2000。因此配合 `FLAG_LAYOUT_NO_LIMITS` 可以盖住状态栏与导航栏。
 *
 * ## 触摸穿透（Android 12+ 的隐患）
 * Android 12 引入 `BLOCK_UNTRUSTED_TOUCHES`，目的是防止恶意悬浮窗劫持点击。
 * 但该机制只针对**可触摸**的不受信任窗口（`TouchOcclusionMode.BLOCK_UNTRUSTED`）；
 * 声明了 `FLAG_NOT_TOUCHABLE` 的窗口会被标记为 `PASS_THROUGH`，
 * 触摸事件直接下发给下层窗口，不受拦截。本类全程带该 flag，故不影响其它应用操作。
 *
 * ## 已知限制（相对无障碍浮窗）
 * - 无法覆盖系统锁屏界面（keyguard）；
 * - 部分 ROM 在状态栏下拉后的通知面板上可能盖住遮罩；
 * - 屏幕录制/投屏时遮罩默认不会出现在录制画面里（对护眼场景反而是优点）。
 */
internal object OverlayManager {

    private const val TAG = "OverlayManager"

    /**
     * 窗口 alpha 的安全上限。
     *
     * AOSP `TouchOcclusionMode.USE_OPACITY` 的判定阈值为 0.8：
     * 超过它，本窗口会被当成遮挡层，下层应用的所有触摸都会被系统丢弃。
     * 取 0.78 留出浮点与 ROM 差异的余量。
     */
    const val SAFE_MAX_WINDOW_ALPHA = 0.78f

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 用 Application Context 取 WindowManager，窗口生命周期才能与 Activity 解耦 */
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    /**
     * 遮罩当前是否真的挂在屏幕上。
     * 供监测循环做「期望 vs 实际」比对，实现自愈。
     */
    @Volatile
    var showing: Boolean = false
        private set

    /** 是否具备悬浮窗权限 */
    fun canDraw(context: Context): Boolean = runCatching {
        Settings.canDrawOverlays(context)
    }.getOrDefault(false)

    /**
     * 显示遮罩。
     * @return true 表示已挂上（或已排队挂上）
     */
    fun show(context: Context, rgb: Int, alpha: Float): Boolean {
        val app = context.applicationContext
        if (!canDraw(app)) {
            Log.w(TAG, "缺少悬浮窗权限，无法绘制遮罩")
            return false
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return showInternal(app, rgb, alpha)
        }
        mainHandler.post { showInternal(app, rgb, alpha) }
        return true
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hideInternal()
        } else {
            mainHandler.post { hideInternal() }
        }
    }

    /** 颜色/浓度变化时原地重绘，不重建窗口（避免闪烁） */
    fun applyStyle(context: Context, rgb: Int, alpha: Float) {
        val app = context.applicationContext
        mainHandler.post {
            val view = overlayView as? FrameLayout ?: return@post
            view.setBackgroundColor(0xFF000000.toInt() or (rgb and 0xFFFFFF))
            runCatching {
                windowManager?.updateViewLayout(view, buildParams(alpha))
            }
        }
    }

    /**
     * 按当前配置算出遮罩的**不透明**基色（RGB）。
     *
     * 浓度不放在颜色里，而是放到窗口 alpha 上——原因见 [buildParams] 的说明。
     */
    fun baseColorOf(context: Context): Int {
        val state = (context.applicationContext as? AutoControlApplication)
            ?.settingsRepository?.state?.value
            ?: return 0x808080
        return state.grayscaleColor and 0xFFFFFF
    }

    /** 浓度换算为窗口 alpha（0f..1f） */
    fun alphaOf(context: Context): Float {
        val state = (context.applicationContext as? AutoControlApplication)
            ?.settingsRepository?.state?.value
            ?: return ControlState.DEFAULT_GRAYSCALE_ALPHA / 255f
        return state.grayscaleAlpha / 255f
    }

    fun argbOf(rgb: Int, alpha: Int): Int {
        val c = rgb and 0xFFFFFF
        return Color.argb(
            alpha.coerceIn(ControlState.MIN_GRAYSCALE_ALPHA, ControlState.MAX_GRAYSCALE_ALPHA),
            (c shr 16) and 0xFF,
            (c shr 8) and 0xFF,
            c and 0xFF,
        )
    }

    private fun showInternal(context: Context, rgb: Int, alpha: Float): Boolean {
        val wm = windowManager
            ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.also { windowManager = it }
            ?: return false

        // 已存在则只更新颜色与透明度，避免重复 addView 抛异常
        (overlayView as? FrameLayout)?.let {
            it.setBackgroundColor(0xFF000000.toInt() or (rgb and 0xFFFFFF))
            wm.updateViewLayout(it, buildParams(alpha))
            showing = true
            return true
        }

        val view = FrameLayout(context).apply {
            // 背景必须是**不透明**色：浓度由窗口 alpha 承担，
            // 否则 alpha=1.0 的窗口会被系统判为遮挡，触摸会被拦截。
            setBackgroundColor(0xFF000000.toInt() or (rgb and 0xFFFFFF))
        }
        return try {
            wm.addView(view, buildParams(alpha))
            overlayView = view
            showing = true
            Log.d(TAG, "遮罩已显示（悬浮窗通道，alpha=$alpha）")
            true
        } catch (e: Exception) {
            Log.e(TAG, "遮罩添加失败", e)
            hideInternal()
            false
        }
    }

    private fun hideInternal() {
        overlayView?.let { v -> runCatching { windowManager?.removeView(v) } }
        overlayView = null
        showing = false
    }

    /**
     * 铺满整块屏幕（含状态栏 / 导航栏 / 刘海）的窗口参数。
     *
     * ### 尺寸：四个条件缺一不可
     * 1. `FLAG_LAYOUT_IN_SCREEN`：按整屏布局，而非"可用区域"；
     * 2. `FLAG_LAYOUT_NO_LIMITS`：允许超出边界，这是能盖住状态栏的关键；
     * 3. `layoutInDisplayCutoutMode`：延伸进刘海/挖孔，否则顶部留缺口；
     * 4. `setFitInsetsTypes(0)`：不避让任何系统栏 insets（不设的话前三条会被裁掉）。
     *
     * ### 触摸穿透：为什么浓度必须放在 window alpha 上
     *
     * 实测 `dumpsys input` 显示本窗口 `touchOcclusionMode=USE_OPACITY`：
     * 因为带了 `FLAG_NOT_TOUCHABLE`，系统不直接用 `BLOCK_UNTRUSTED` 拦截，
     * 而是**按窗口不透明度决定是否遮挡**。AOSP 判定为：
     *
     * ```
     * case USE_OPACITY: return windowHandle.getAlpha() > 0.8f;
     * ```
     *
     * 也就是说：窗口 alpha > 0.8 会被当作"遮挡层"，其下方应用的触摸会被系统拦截
     * （Android 12 的 `BLOCK_UNTRUSTED_TOUCHES` 机制，防点击劫持）。
     *
     * 最初把浓度做在背景色 `Color.argb(140,...)` 上、窗口 alpha 保持默认 1.0，
     * 必然被判为遮挡 → 其它应用点不动。这就是先前被迫改用无障碍浮窗的原因。
     *
     * 现在把浓度搬到 [WindowManager.LayoutParams.alpha]（背景色用不透明 RGB）：
     * 视觉完全等价，但窗口 alpha 变成 0.08~0.78，系统认为"透明"，
     * 触摸事件正常下发给下层窗口，无需无障碍服务也能穿透。
     *
     * 因此 `MAX_GRAYSCALE_ALPHA` 被限制在 199（≈0.78），留出安全余量。
     */
    private fun buildParams(alpha: Float): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START

            // 关键：浓度以窗口 alpha 表达，且必须低于 0.8，否则触摸会被系统拦截
            this.alpha = alpha.coerceIn(0.05f, SAFE_MAX_WINDOW_ALPHA)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }
    }
}
