package com.example.autocontrol.data

import java.time.LocalTime

/**
 * 全局控制状态，首页直接消费。
 */
data class ControlState(
    val shutdownEnabled: Boolean = false,
    val shutdownTime: LocalTime = LocalTime.of(23, 0),
    val grayscaleEnabled: Boolean = true,
    val grayscaleStart: LocalTime = LocalTime.of(22, 0),
    val grayscaleEnd: LocalTime = LocalTime.of(7, 0),
    /** 灰罩颜色（RGB，不含 alpha）。默认中性灰 #808080 */
    val grayscaleColor: Int = DEFAULT_GRAYSCALE_COLOR,
    /** 灰罩浓度（0..255，越大越不透明、去色越强）。默认 140 */
    val grayscaleAlpha: Int = DEFAULT_GRAYSCALE_ALPHA,
    val networkEnabled: Boolean = false,
    val networkStart: LocalTime = LocalTime.of(23, 0),
    val networkEnd: LocalTime = LocalTime.of(7, 0),
    /**
     * 是否从系统「最近任务」列表中隐藏本应用。
     *
     * 默认 true：本应用是后台常驻型工具，留在最近任务里既无意义又容易被误划掉。
     * 隐藏后只能从桌面图标进入。
     */
    val hideFromRecents: Boolean = true,
) {
    companion object {
        const val DEFAULT_GRAYSCALE_COLOR = 0x808080
        const val DEFAULT_GRAYSCALE_ALPHA = 140

        /** 浓度范围为「几乎透明 .. 较实」，两头都不给到 0/255，避免用户调到看不见或全屏挡死 */
        const val MIN_GRAYSCALE_ALPHA = 20

        /**
         * 浓度上限 199（≈0.78）。
         *
         * 不再取 230 的原因：悬浮窗通道用 **窗口 alpha** 表达浓度，
         * 而 Android 12 的 `TouchOcclusionMode.USE_OPACITY` 在窗口 alpha > 0.8 时
         * 会把该窗口判定为"遮挡层"，**拦截其下方应用的所有触摸**
         * （防点击劫持机制，见 `OverlayManager.buildParams` 注释）。
         *
         * 上限压到 0.78 可保证整个滑块范围内触摸都能穿透。
         * 此浓度在视觉上已接近全灰，不影响实际使用。
         */
        const val MAX_GRAYSCALE_ALPHA = 199
    }
}
