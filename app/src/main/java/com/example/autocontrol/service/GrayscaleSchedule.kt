package com.example.autocontrol.service

import android.content.Context
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
 */
object GrayscaleSchedule {

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
     * 按当前时间与配置，把灰罩校正到正确状态（显示/隐藏）。
     *
     * @return true 表示当前应处于灰罩显示状态
     */
    fun apply(context: Context, state: ControlState): Boolean {
        if (!state.grayscaleEnabled) {
            GrayscaleAccessibilityService.hide(context)
            return false
        }
        // 无障碍未开启时灰罩不可能绘制，统一收敛为隐藏，避免状态残留
        if (!GrayscaleAccessibilityService.isEnabled(context)) {
            GrayscaleAccessibilityService.hide(context)
            return false
        }
        return if (shouldShow(state)) {
            GrayscaleAccessibilityService.show(context)
            true
        } else {
            GrayscaleAccessibilityService.hide(context)
            false
        }
    }

    /** 供提示文案使用：把时段格式化为 "22:00 ~ 07:00" */
    fun describe(state: ControlState): String =
        "%02d:%02d ~ %02d:%02d".format(
            state.grayscaleStart.hour, state.grayscaleStart.minute,
            state.grayscaleEnd.hour, state.grayscaleEnd.minute,
        )
}
