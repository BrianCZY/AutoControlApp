package com.example.autocontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.autocontrol.AutoControlApplication
import com.example.autocontrol.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 定时动作的统一入口（由 AlarmManager 触发）。
 *
 * ## 为什么必须走广播，而不是 PendingIntent.getService
 *
 * 之前闹钟直接指向 [AutoControlService]，带来一个致命脆弱点：
 * 主服务一旦被系统/ROM 杀掉，闹钟触发时需要在**后台**重新拉起前台服务，
 * 而 Android 12+ 对此有严格限制（`ForegroundServiceStartNotAllowedException`）。
 * 一旦这一步失败，到点就什么都不发生 —— 这正是"有时候生效、有时候不生效"的主因。
 *
 * 改为广播接收器后：
 * - 广播是**静态注册**在 manifest 里的，进程不在也能被系统拉起，这条路必然可达；
 * - 接收器直接用 `goAsync()` 干活，**完全不依赖主服务是否活着**
 *   （灰度只需给无障碍服务发一条指令，无障碍服务由系统托管，比主服务稳定得多）。
 *
 * 处理完动作后仍会尝试拉起主服务，但那只是「恢复常驻通知」的锦上添花，不再是前提。
 *
 * ## 为什么灰度不用区分 ON / OFF
 * 闹钟可能延迟数分钟甚至被系统攒批，此时"开始时刻的闹钟"执行时可能已经走出时段。
 * 因此一律按「此刻的实际时间」重新判定，而不是盲信 action 语义。
 */
class AlarmActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // goAsync 让接收器在返回后仍能继续异步执行（最多约 10s），
        // 避免在 onReceive 主线程里做 DataStore 读取而卡死。
        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(app, action)
            } catch (e: Exception) {
                Log.e(TAG, "处理定时动作失败：$action", e)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    private suspend fun handle(context: Context, action: String) {
        val state = (context as? AutoControlApplication)
            ?.settingsRepository?.state?.value
        if (state == null) {
            Log.w(TAG, "读取配置失败，跳过 $action")
            return
        }

        when (action) {
            Scheduler.ACTION_GRAYSCALE_ON, Scheduler.ACTION_GRAYSCALE_OFF -> {
                // 按当前时间重算，而不是照 action 字面意思执行
                val shown = GrayscaleSchedule.apply(context, state)
                Log.d(
                    TAG,
                    "灰度闹钟：校正为${if (shown) "显示" else "隐藏"}" +
                        "（时段=${GrayscaleSchedule.describe(state)}）"
                )
            }

            Scheduler.ACTION_SHUTDOWN -> {
                val result = ActionExecutor.performShutdown(context)
                NotificationHelper.notifyReminder(
                    context,
                    context.getString(R.string.notif_shutdown_title),
                    result.message,
                )
            }

            Scheduler.ACTION_NETWORK_OFF, Scheduler.ACTION_NETWORK_ON -> {
                val enable = action == Scheduler.ACTION_NETWORK_ON
                val result = ActionExecutor.performDisconnect(context, enable = enable)
                NotificationHelper.notifyReminder(
                    context,
                    context.getString(
                        if (enable) R.string.notif_network_on_title
                        else R.string.notif_network_off_title
                    ),
                    result.message,
                )
            }

            else -> Log.w(TAG, "未知动作：$action")
        }

        // 一次性精确闹钟触发后必须重排，否则只有第一天生效
        Scheduler.rescheduleAll(context, state)

        // 顺带把主服务拉起来（常驻通知、监测循环）。失败不影响上面已完成的动作。
        if (ServiceKeeper.shouldRun(context)) {
            ServiceKeeper.startService(context)
            ServiceKeeper.scheduleWatchdog(context)
        }
    }
}
