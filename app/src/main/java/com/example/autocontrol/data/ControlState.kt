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
    val networkEnabled: Boolean = false,
    val networkStart: LocalTime = LocalTime.of(23, 0),
    val networkEnd: LocalTime = LocalTime.of(7, 0),
)
