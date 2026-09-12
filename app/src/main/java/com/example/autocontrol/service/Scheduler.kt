package com.example.autocontrol.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.autocontrol.data.ControlState
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * AlarmManager 调度封装。负责为三类定时任务注册/取消精确闹钟。
 * 闹钟触发后通过显式 Intent 发给 [AutoControlService]（带 action 区分）。
 */
object Scheduler {

    // 广播 action（发给 AutoControlService）
    const val ACTION_SHUTDOWN = "com.example.autocontrol.action.SHUTDOWN"
    const val ACTION_GRAYSCALE_ON = "com.example.autocontrol.action.GRAYSCALE_ON"
    const val ACTION_GRAYSCALE_OFF = "com.example.autocontrol.action.GRAYSCALE_OFF"
    const val ACTION_NETWORK_ON = "com.example.autocontrol.action.NETWORK_ON"
    const val ACTION_NETWORK_OFF = "com.example.autocontrol.action.NETWORK_OFF"

    private const val REQUEST_SHUTDOWN = 2001
    private const val REQUEST_GRAYSCALE_ON = 2002
    private const val REQUEST_GRAYSCALE_OFF = 2003
    private const val REQUEST_NETWORK_ON = 2004
    private const val REQUEST_NETWORK_OFF = 2005

    fun rescheduleAll(context: Context, state: ControlState) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 关机
        if (state.shutdownEnabled) {
            schedule(context, am, ACTION_SHUTDOWN, state.shutdownTime, REQUEST_SHUTDOWN)
        } else {
            cancel(context, am, ACTION_SHUTDOWN, REQUEST_SHUTDOWN)
        }

        // 灰度
        if (state.grayscaleEnabled) {
            schedule(context, am, ACTION_GRAYSCALE_ON, state.grayscaleStart, REQUEST_GRAYSCALE_ON)
            schedule(context, am, ACTION_GRAYSCALE_OFF, state.grayscaleEnd, REQUEST_GRAYSCALE_OFF)
        } else {
            cancel(context, am, ACTION_GRAYSCALE_ON, REQUEST_GRAYSCALE_ON)
            cancel(context, am, ACTION_GRAYSCALE_OFF, REQUEST_GRAYSCALE_OFF)
        }

        // 断网
        if (state.networkEnabled) {
            schedule(context, am, ACTION_NETWORK_OFF, state.networkStart, REQUEST_NETWORK_OFF)
            schedule(context, am, ACTION_NETWORK_ON, state.networkEnd, REQUEST_NETWORK_ON)
        } else {
            cancel(context, am, ACTION_NETWORK_OFF, REQUEST_NETWORK_OFF)
            cancel(context, am, ACTION_NETWORK_ON, REQUEST_NETWORK_ON)
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancel(context, am, ACTION_SHUTDOWN, REQUEST_SHUTDOWN)
        cancel(context, am, ACTION_GRAYSCALE_ON, REQUEST_GRAYSCALE_ON)
        cancel(context, am, ACTION_GRAYSCALE_OFF, REQUEST_GRAYSCALE_OFF)
        cancel(context, am, ACTION_NETWORK_ON, REQUEST_NETWORK_ON)
        cancel(context, am, ACTION_NETWORK_OFF, REQUEST_NETWORK_OFF)
    }

    private fun schedule(
        context: Context,
        am: AlarmManager,
        action: String,
        time: LocalTime,
        requestCode: Int,
    ) {
        val triggerAt = nextTriggerMillis(time)
        val pi = pendingIntent(context, action, requestCode)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // 未授予精确闹钟权限时静默降级：不崩溃，定时任务由前台服务兜底或等待用户授权后重排
            // 也可选择在此提示用户，但 Scheduler 无 UI 上下文，交由调用方决定
        }
    }

    private fun cancel(
        context: Context,
        am: AlarmManager,
        action: String,
        requestCode: Int,
    ) {
        val pi = pendingIntent(context, action, requestCode)
        am.cancel(pi)
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AutoControlService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 计算下一次到达 [time] 的毫秒时间戳（如果今天已过，则明天）。 */
    private fun nextTriggerMillis(time: LocalTime): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }
        return target.toInstant().toEpochMilli()
    }
}
