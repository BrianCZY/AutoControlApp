package com.example.autocontrol.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.autocontrol.AutoControlApplication
import com.example.autocontrol.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 前台服务：持定时调度，接收 AlarmManager 广播并执行动作。
 * 通过 [Scheduler] 的 action 区分要执行的动作。
 */
class AutoControlService : Service() {

    private companion object {
        /** 所有定时动作 action 的公共前缀，用于识别「本次启动来自闹钟」 */
        const val ACTION_PREFIX = "com.example.autocontrol.action."
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(this)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            Scheduler.ACTION_SHUTDOWN -> handleShutdown()
            Scheduler.ACTION_GRAYSCALE_ON -> handleGrayscale(true)
            Scheduler.ACTION_GRAYSCALE_OFF -> handleGrayscale(false)
            Scheduler.ACTION_NETWORK_OFF -> handleNetwork(false)
            Scheduler.ACTION_NETWORK_ON -> handleNetwork(true)
            else -> {
                // 普通启动（手动启动服务）：异步读取当前状态后重排调度，避免主线程阻塞
                rescheduleAsync()
            }
        }
        // 精确闹钟是一次性的，触发后必须重新排程，否则只有第一天生效。
        // 普通启动分支已自行重排，这里只处理「闹钟触发」的情况。
        if (action != null && action.startsWith(ACTION_PREFIX)) {
            rescheduleAsync()
        }
        return START_STICKY
    }

    private fun rescheduleAsync() {
        val app = application as? AutoControlApplication ?: return
        scope.launch {
            runCatching { app.settingsRepository.state.first() }
                .getOrNull()
                ?.let { state ->
                    Scheduler.rescheduleAll(this@AutoControlService, state)
                    // 开机/服务重启后按当前时段校正灰罩：
                    // 若此刻正处于灰度时段（例如设备关机期间跨过了开始时间），应立刻补上灰罩。
                    GrayscaleSchedule.apply(this@AutoControlService, state)
                }
        }
    }

    private fun handleShutdown() {
        val result = ActionExecutor.performShutdown(this)
        notifyResult(
            title = getString(R.string.notif_shutdown_title),
            result = result
        )
    }

    private fun handleGrayscale(on: Boolean) {
        if (on) {
            if (GrayscaleAccessibilityService.isEnabled(this)) {
                GrayscaleAccessibilityService.show(this)
                notifyResult(
                    title = getString(R.string.notif_grayscale_on_title),
                    result = ActionExecutor.ActionResult(true, getString(R.string.action_grayscale_on_success))
                )
            } else {
                notifyResult(
                    title = getString(R.string.notif_grayscale_on_title),
                    result = ActionExecutor.ActionResult(false, getString(R.string.accessibility_not_granted))
                )
            }
        } else {
            GrayscaleAccessibilityService.hide(this)
            notifyResult(
                title = getString(R.string.notif_grayscale_off_title),
                result = ActionExecutor.ActionResult(true, getString(R.string.action_grayscale_off_success))
            )
        }
    }

    private fun handleNetwork(on: Boolean) {
        val result = ActionExecutor.performDisconnect(this, enable = on)
        notifyResult(
            title = getString(if (on) R.string.notif_network_on_title else R.string.notif_network_off_title),
            result = result
        )
    }

    private fun notifyResult(title: String, result: ActionExecutor.ActionResult) {
        NotificationHelper.notifyReminder(this, title, result.message)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[Job]?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
