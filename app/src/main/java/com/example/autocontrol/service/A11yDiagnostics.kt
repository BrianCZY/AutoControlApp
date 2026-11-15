package com.example.autocontrol.service

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 无障碍服务的生命周期诊断记录。
 *
 * 存在的意义：无障碍服务可能被系统/ROM 强制关闭（强制停止应用、覆盖安装、省电清理等），
 * 但 App 侧看到的只是「开关变关了」，无法区分是「系统解绑」还是「自己判断错了」。
 * 这里把服务的连接/断开/销毁事件按时间落盘，就能在设置页直接看出真相：
 * - 有 everConnected 但当前未开启 → 曾经成功连上过，现在被系统解绑了；
 * - 从来没有 everConnected → 服务压根没启动过（可能被 ROM 拦截）。
 */
internal object A11yDiagnostics {

    private const val PREFS = "a11y_diag"
    private const val KEY_EVER_CONNECTED = "ever_connected"
    private const val KEY_LAST_CONNECTED_AT = "last_connected_at"
    private const val KEY_LAST_DISCONNECTED_AT = "last_disconnected_at"
    private const val KEY_LAST_EVENT = "last_event"

    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun now(): String = formatter.format(Date())

    fun onConnected(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_EVER_CONNECTED, true)
            .putString(KEY_LAST_CONNECTED_AT, now())
            .putString(KEY_LAST_EVENT, "已连接")
            .apply()
    }

    /** onUnbind：系统主动解绑（用户关闭开关、或 ROM 回收） */
    fun onUnbind(context: Context) {
        prefs(context).edit()
            .putString(KEY_LAST_DISCONNECTED_AT, now())
            .putString(KEY_LAST_EVENT, "被系统解绑")
            .apply()
    }

    fun onDestroy(context: Context) {
        prefs(context).edit()
            .putString(KEY_LAST_DISCONNECTED_AT, now())
            .putString(KEY_LAST_EVENT, "服务销毁")
            .apply()
    }

    /** 是否曾经成功连接过（用于区分"从未开启"与"被系统关闭"） */
    fun everConnected(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EVER_CONNECTED, false)

    /**
     * 生成一句给用户看的状态说明。没有历史记录时返回 null。
     */
    fun summary(context: Context): String? {
        val p = prefs(context)
        val lastEvent = p.getString(KEY_LAST_EVENT, null) ?: return null
        val connectedAt = p.getString(KEY_LAST_CONNECTED_AT, null)
        val disconnectedAt = p.getString(KEY_LAST_DISCONNECTED_AT, null)
        return buildString {
            append("最近：")
            append(lastEvent)
            when {
                disconnectedAt != null -> append("（$disconnectedAt）")
                connectedAt != null -> append("（$connectedAt）")
            }
            if (disconnectedAt != null && connectedAt != null) {
                append("，上次连接 ").append(connectedAt)
            }
        }
    }
}
