package com.example.autocontrol.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.autocontrol.AutoControlApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 前台服务保活调度。
 *
 * 目标：用户把 App 从最近任务划掉、甚至长时间不打开，后台的定时调度也要继续跑。
 *
 * 做法（三层，逐级兜底）：
 * 1. 前台服务 + `START_STICKY`：常规情况下系统会保活，进程被杀后自动重启；
 * 2. `onTaskRemoved` 里排一个短延迟闹钟重新拉起服务（见 [AutoControlService]）——
 *    不能直接 startForegroundService，Android 12+ 在后台直启前台服务会被拦；
 *    而闹钟触发的启动享有约 10 秒白名单，是官方允许的路径；
 * 3. 本类的**心跳闹钟**：每 [WATCHDOG_INTERVAL_MS] 唤醒一次服务。
 *    服务每次被唤醒都会重新排下一次心跳，形成闭环——
 *    即使进程被 ROM 彻底杀掉、`START_STICKY` 失效，下一次心跳也能把服务拉回来。
 *
 * 注意：心跳用 `setAndAllowWhileIdle`（不要求精确），
 * 既能在 Doze 下穿透，又不会因为 `setExactAndAllowWhileIdle` 的配额限制而被丢弃。
 */
object ServiceKeeper {

    private const val TAG = "ServiceKeeper"

    /**
     * 心跳间隔：5 分钟。
     *
     * 原本是 15 分钟，但那样被 ROM 杀掉后最长有 15 分钟真空期，
     * 用户划掉 App 几分钟内看不到常驻通知，就会认为"App 退出了"。
     * 5 分钟在耗电与恢复速度之间比较平衡；Doze 下系统会自行放宽（allowWhileIdle 有配额），
     * 不会真的做到严格 5 分钟，但已足够把"用户感知到的退出"压到最低。
     */
    private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L

    /** 划掉任务后重新拉起服务的延迟：给系统留出收尾时间，又不至于太久被冻结 */
    private const val RELAUNCH_DELAY_MS = 1_000L

    private const val REQUEST_WATCHDOG = 3001
    private const val REQUEST_RELAUNCH = 3002

    /** 排下一次心跳 */
    fun scheduleWatchdog(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = watchdogPendingIntent(context)
        val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: Exception) {
            Log.e(TAG, "心跳闹钟排程失败", e)
        }
    }

    fun cancelWatchdog(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(watchdogPendingIntent(context)) }
    }

    /**
     * 用户划掉最近任务后，延迟重新拉起服务。
     *
     * 这里**不能**用 setExactAndAllowWhileIdle：
     * Android 14（targetSdk 34）起，新装应用的 SCHEDULE_EXACT_ALARM 默认不授予，
     * 调用会抛 SecurityException —— 之前正是因为这一点，兜底重启从未真正生效。
     * 改用 setAndAllowWhileIdle（不需要任何权限），1 秒延迟的精度完全够用。
     */
    fun scheduleRelaunch(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = relaunchPendingIntent(context)
        val triggerAt = System.currentTimeMillis() + RELAUNCH_DELAY_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: Exception) {
            Log.e(TAG, "重启闹钟排程失败", e)
        }
    }

    /**
     * 启动/唤醒前台服务。
     *
     * @return true 表示启动指令已发出（不代表服务一定活着，ROM 仍可能拦截）
     */
    fun startService(context: Context): Boolean {
        val intent = Intent(context, AutoControlService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            // Android 12+ 在后台直启前台服务会抛异常，调用方应改用闹钟路径
            Log.w(TAG, "启动前台服务失败（可能处于后台限制）", e)
            false
        }
    }

    /** 停止服务与保活（用户主动关闭时调用，否则服务会反复被拉起） */
    fun stopService(context: Context) {
        cancelWatchdog(context)
        runCatching { context.stopService(Intent(context, AutoControlService::class.java)) }
    }

    /** 从偏好里同步读取配置，供广播接收器（无协程作用域）使用 */
    fun readState(context: Context) = runCatching {
        val app = context.applicationContext as? AutoControlApplication ?: return@runCatching null
        runBlocking { app.settingsRepository.state.first() }
    }.getOrNull()
    private fun watchdogPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ServiceWatchdogReceiver::class.java).apply {
            action = ServiceWatchdogReceiver.ACTION_WATCHDOG
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_WATCHDOG,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun relaunchPendingIntent(context: Context): PendingIntent {
        // 指向广播接收器而非 Service：闹钟触发接收器属于系统豁免的后台启动路径，
        // 在接收器里再 startForegroundService 成功率明显高于直连 Service。
        val intent = Intent(context, ServiceWatchdogReceiver::class.java).apply {
            action = ServiceWatchdogReceiver.ACTION_RELAUNCH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_RELAUNCH,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 供设置页展示：服务是否被用户主动关闭（关闭后不再自动拉起） */
    fun isUserStopped(context: Context): Boolean = prefs(context).getBoolean(KEY_USER_STOPPED, false)

    fun setUserStopped(context: Context, stopped: Boolean) {
        prefs(context).edit().putBoolean(KEY_USER_STOPPED, stopped).apply()
    }

    private const val KEY_USER_STOPPED = "user_stopped"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("service_keeper", Context.MODE_PRIVATE)

    /** 服务是否应当运行（用户没有主动关闭） */
    fun shouldRun(context: Context): Boolean = !isUserStopped(context)

    /**
     * 应用启动时的统一入口：把「心跳 + 服务」拉起来。
     * 用户主动停用过就跳过，避免"关了又自己起来"。
     */
    fun ensureRunning(context: Context) {
        if (!shouldRun(context)) return
        startService(context)
        scheduleWatchdog(context)
    }
}
