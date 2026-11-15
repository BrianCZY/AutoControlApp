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
 * 屏幕事件接收器：在设备解锁完成时立即校正灰罩。
 *
 * ## 为什么需要它
 *
 * 主服务的兜底轮询用的是 `delay(30_000)`，而协程的 delay 基于 uptime 计时，
 * **设备深度休眠时 CPU 停转、计时暂停**。于是熄屏一段时间后：
 * - 轮询实际上没有在推进；
 * - 到了灰度开始时间也不会有任何动作；
 * - 用户点亮屏幕后还要等下一个周期才会变灰，观感就是"到点没生效"。
 *
 * 而 `ACTION_USER_PRESENT` 是**由系统在解锁完成时投递的**，属于 manifest 可静态注册的广播，
 * 无论进程在不在都会被拉起。因此它是"点亮屏幕即正确"最可靠的一环。
 *
 * 这里只做一件事：读配置 → 算出现在该不该灰 → 校正。
 * 不启动服务、不发通知，力求轻量（接收器有 10 秒超时，必须快速返回）。
 */
class ScreenEventReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenEvent"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val pending = goAsync()
        val app = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = (app as? AutoControlApplication)
                    ?.settingsRepository?.state?.value
                if (state != null) {
                    val shown = GrayscaleSchedule.apply(app, state)
                    Log.d(
                        TAG,
                        "解锁校正：${if (shown) "显示" else "隐藏"}" +
                            "（时段=${GrayscaleSchedule.describe(state)}）"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "解锁校正失败", e)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }
}
