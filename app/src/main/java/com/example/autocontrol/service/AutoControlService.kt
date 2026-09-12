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
        return START_STICKY
    }

    private fun rescheduleAsync() {
        val app = application as? AutoControlApplication ?: return
        scope.launch {
            runCatching { app.settingsRepository.state.first() }
                .getOrNull()
                ?.let { state -> Scheduler.rescheduleAll(this@AutoControlService, state) }
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
        val result = ActionExecutor.setGrayscale(this, on)
        notifyResult(
            title = getString(if (on) R.string.notif_grayscale_on_title else R.string.notif_grayscale_off_title),
            result = result
        )
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
