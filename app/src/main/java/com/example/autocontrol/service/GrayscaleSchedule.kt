package com.example.autocontrol.service

import android.content.Context
import android.util.Log
import com.example.autocontrol.data.ControlState
import java.time.LocalTime

/**
 * 灰度「时段」判定与统一应用入口。
 *
 * 灰度开关的语义是「启用该时段策略」，不是「立刻变灰」：
 * - 当前时间落在 [start, end) 内 → 显示灰罩；
 * - 落在时段外 → 保持透明（等开始时刻的闹钟再来显示）。
 *
 * 支持跨午夜（如 22:00 ~ 07:00），这是默认配置，也是实际最常用的形态。
 * 只有所有入口（手动开关、改时间、无障碍服务重连、开机/服务重启、回到前台）
 * 都走 [apply]，才不会出现「一开开关就变灰」「时段改了不生效」这类不一致。
 *
 * ## 双通道策略
 * 1. **悬浮窗通道（首选）**：[OverlayManager] + `SYSTEM_ALERT_WINDOW`。
 *    授权一次长期有效，强制停止/覆盖安装都不会被撤销。
 * 2. **无障碍通道（降级）**：用户没给悬浮窗权限、但开着无障碍服务时使用。
 *
 * 之所以优先悬浮窗：无障碍服务是系统级特权，被强停或覆盖安装后系统会**自动移除授权**，
 * 每次都要用户手动重开，对"加一层灰色滤镜"这种需求来说代价过高。
 */
object GrayscaleSchedule {

    private const val TAG = "GrayscaleSchedule"

    /**
     * 判断 [now] 是否落在灰度时段内。
     *
     * - [start] < [end]：同日区间，`start <= now < end`
     * - [start] > [end]：跨午夜区间，`now >= start || now < end`
     * - [start] == [end]：视作覆盖全天
     */
    fun isInWindow(now: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
        start == end -> true
        start.isBefore(end) -> !now.isBefore(start) && now.isBefore(end)
        else -> !now.isBefore(start) || now.isBefore(end)
    }

    /** 当前是否应当显示灰罩（开关已启用 + 处于时段内） */
    fun shouldShow(state: ControlState, now: LocalTime = LocalTime.now()): Boolean =
        state.grayscaleEnabled && isInWindow(now, state.grayscaleStart, state.grayscaleEnd)

    /**
     * 当前遮罩是否真的挂在屏幕上（两条通道任一为真即可）。
     * 供监测循环做自愈比对。
     */
    val isShowing: Boolean
        get() = OverlayManager.showing || GrayscaleAccessibilityService.overlayShowing

    /** 是否有能力绘制遮罩（悬浮窗或无障碍任一可用） */
    fun canRender(context: Context): Boolean =
        OverlayManager.canDraw(context) || GrayscaleAccessibilityService.isEnabled(context)

    /**
     * 按当前时间与配置，把灰罩校正到正确状态（显示/隐藏）。
     *
     * @return true 表示当前应处于灰罩显示状态
     */
    fun apply(context: Context, state: ControlState): Boolean {
        val desired = shouldShow(state)

        if (!desired) {
            hideAll(context)
            return false
        }

        // 1) 首选悬浮窗通道：无需无障碍，授权一次长期有效
        if (OverlayManager.canDraw(context)) {
            val ok = OverlayManager.show(
                context,
                state.grayscaleColor and 0xFFFFFF,
                state.grayscaleAlpha / 255f,
            )
            if (ok) {
                // 两条通道同时存在时会产生两层叠加、颜色偏深，这里把无障碍那层收掉
                if (GrayscaleAccessibilityService.overlayShowing) {
                    GrayscaleAccessibilityService.hide(context)
                }
                return true
            }
            Log.w(TAG, "悬浮窗通道绘制失败，尝试降级到无障碍通道")
        }

        // 2) 降级：无障碍服务（用户已开启时才能工作）
        if (GrayscaleAccessibilityService.isEnabled(context)) {
            GrayscaleAccessibilityService.show(context)
            return true
        }

        // 3) 两条通道都不可用
        Log.w(TAG, "无法绘制灰罩：缺少悬浮窗权限且无障碍服务未开启")
        return false
    }

    /** 颜色/浓度变化时原地重绘，避免重建窗口造成闪烁 */
    fun refreshStyle(context: Context, state: ControlState) {
        if (OverlayManager.showing) {
            OverlayManager.applyStyle(
                context,
                state.grayscaleColor and 0xFFFFFF,
                state.grayscaleAlpha / 255f,
            )
        }
        // 无障碍通道由它自己读配置重绘
        GrayscaleAccessibilityService.refreshStyle(
            context,
            state.grayscaleColor,
            state.grayscaleAlpha,
        )
    }

    private fun hideAll(context: Context) {
        OverlayManager.hide(context)
        GrayscaleAccessibilityService.hide(context)
    }

    /** 供提示文案使用：把时段格式化为 "22:00 ~ 07:00" */
    fun describe(state: ControlState): String =
        "%02d:%02d ~ %02d:%02d".format(
            state.grayscaleStart.hour, state.grayscaleStart.minute,
            state.grayscaleEnd.hour, state.grayscaleEnd.minute,
        )
}
