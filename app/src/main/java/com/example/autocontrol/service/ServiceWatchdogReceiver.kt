package com.example.autocontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.autocontrol.AutoControlApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 服务心跳 / 重启接收器。
 *
 * 为什么要经过一个广播接收器，而不是直接把 PendingIntent 指向 Service：
 * - 拉起服务前需要判断「用户是否主动停用」；
 * - 广播接收器由系统在闹钟触发时交付，此时调用 startForegroundService
 *   属于「由 AlarmManager 触发的后台启动」，处在系统豁免窗口内，比在
 *   onTaskRemoved 里直启更可靠（后者在 Android 12+ 常抛
 *   ForegroundServiceStartNotAllowedException）。
 *
 * 两种 action：
 * - [ACTION_WATCHDOG]：周期心跳，长期保活的核心；
 * - [ACTION_RELAUNCH]：用户划掉最近任务后的一次性快速重启。
 *
 * **重要：心跳不只是"把服务叫醒"，它自己也会顺手校正一次灰罩。**
 * 因为主服务的轮询用协程 delay，设备深睡时不会推进；而心跳用的是
 * `setAndAllowWhileIdle`，能穿透 Doze 唤醒 CPU。把校正放在这里，
 * 就多了一条不依赖主服务存活的可靠通道（通常 5 分钟内一定纠正）。
 */
class ServiceWatchdogReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WATCHDOG = "com.example.autocontrol.action.WATCHDOG"
        const val ACTION_RELAUNCH = "com.example.autocontrol.action.RELAUNCH"
        private const val TAG = "ServiceWatchdog"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_WATCHDOG && action != ACTION_RELAUNCH) return
        if (!ServiceKeeper.shouldRun(context)) return

        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // 先校正灰罩：这一步不依赖服务能否被拉起，失败也能独立生效
                runCatching {
                    val state = (app as? AutoControlApplication)
                        ?.settingsRepository?.state?.value
                    if (state != null) {
                        val shown = GrayscaleSchedule.apply(app, state)
                        Log.d(
                            TAG,
                            "心跳校正：${if (shown) "显示" else "隐藏"}" +
                                "（时段=${GrayscaleSchedule.describe(state)}）"
                        )
                    }
                }.onFailure { Log.e(TAG, "心跳校正灰罩失败", it) }

                // 再把服务叫起来（常驻通知 + 监测循环）
                ServiceKeeper.startService(app)
                ServiceKeeper.scheduleWatchdog(app)
                Log.d(TAG, "$action：已校正灰罩并请求启动服务")
            } catch (e: Exception) {
                Log.e(TAG, "$action 处理失败", e)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
