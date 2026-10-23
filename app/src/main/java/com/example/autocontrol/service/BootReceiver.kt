package com.example.autocontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.autocontrol.AutoControlApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 开机/应用更新后重建所有定时任务，并重新拉起前台服务与保活心跳。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            reschedule(context)
            // 开机后系统不会自动恢复前台服务，必须主动拉起；顺带续上心跳。
            ServiceKeeper.ensureRunning(context)
        }
    }

    private fun reschedule(context: Context) {
        val app = context.applicationContext as? AutoControlApplication ?: return
        val state = runCatching {
            runBlocking { app.settingsRepository.state.first() }
        }.getOrNull() ?: return
        Scheduler.rescheduleAll(context, state)
    }
}
