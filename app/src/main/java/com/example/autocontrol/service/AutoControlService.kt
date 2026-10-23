package com.example.autocontrol.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
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
 *
 * 长期后台运行的三道保障：
 * 1. 前台常驻通知 + `START_STICKY`：常规保活，进程被杀后系统会自动重建；
 * 2. [onTaskRemoved]：用户从最近任务划掉 App 时，排一个 1 秒后的闹钟把服务拉回来
 *    （不能直接 startForegroundService，Android 12+ 后台直启会被拦，闹钟路径有白名单）；
 * 3. [ServiceKeeper] 心跳：每 15 分钟唤醒一次，形成闭环，即使 ROM 彻底杀进程也能复活。
 */
class AutoControlService : Service() {

    companion object {
        private const val TAG = "AutoControlService"

        /** 所有定时动作 action 的公共前缀，用于识别「本次启动来自闹钟」 */
        private const val ACTION_PREFIX = "com.example.autocontrol.action."

        /**
         * 服务是否真的活着（进程内标志）。
         *
         * 为什么不用 SharedPreferences：进程被杀后偏好里的值仍然存在，会误报"运行中"。
         * 而静态变量随进程消失而清零，恰好能如实反映"这个进程里服务还在不在"。
         * 本应用单进程，设置页读到的值就是服务进程的真值。
         */
        @Volatile
        var alive: Boolean = false
            private set

        /** 本次服务启动的时间戳，用于界面显示"已运行多久" */
        @Volatile
        var startedAt: Long = 0L
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        alive = true
        startedAt = System.currentTimeMillis()
        NotificationHelper.ensureChannels(this)
        startForegroundCompat()
        Log.d(TAG, "服务已创建")
    }

    /**
     * 显式声明前台服务类型。
     *
     * targetSdk 34 起，manifest 里声明了 foregroundServiceType 的服务
     * 必须在前台化时给出对应类型，否则系统可能抛异常或直接不认这个前台服务
     * ——那就沦为普通后台服务，很容易被回收。
     */
    private fun startForegroundCompat() {
        val notification = NotificationHelper.buildServiceNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "前台化失败，服务可能被系统回收", e)
        }
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
                // 普通启动 / 心跳唤醒 / 划掉任务后重启：读取当前状态并重排调度
                rescheduleAsync()
            }
        }
        // 精确闹钟是一次性的，触发后必须重新排程，否则只有第一天生效。
        // 普通启动分支已自行重排，这里只处理「闹钟触发」的情况。
        if (action != null && action.startsWith(ACTION_PREFIX)) {
            rescheduleAsync()
        }

        // 每次被唤醒都续一次心跳，保证「服务活着 → 心跳继续 → 服务持续活着」这个闭环不断。
        ServiceKeeper.scheduleWatchdog(this)

        return START_STICKY
    }

    /**
     * 用户把 App 从最近任务列表划掉时回调。
     *
     * 这是「关闭 App 后后台还要继续跑」的关键点。采用双路径，缺一不可：
     * 1. **先直接尝试拉起**：此刻本进程还活着（回调正在执行），通常仍在系统豁免窗口内，
     *    成功率较高，可让服务"无感续命"，通知栏不出现空档；
     * 2. **再排一个 1 秒后的闹钟兜底**：若上面被 ROM 拦下，闹钟能在进程被杀后重新创建进程。
     *    注意必须用 setAndAllowWhileIdle —— 精确闹钟在 Android 14 默认无权限，会静默失败。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!ServiceKeeper.shouldRun(this)) return

        Log.d(TAG, "任务被移除：尝试立即续命 + 安排兜底重启")
        val relaunched = ServiceKeeper.startService(this)
        Log.d(TAG, "立即拉起结果：$relaunched")
        // 无论立即拉起是否成功都排闹钟：成功时它只是一次幂等唤醒，失败时它就是救命稻草。
        ServiceKeeper.scheduleRelaunch(this)
        ServiceKeeper.scheduleWatchdog(this)
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
        alive = false
        startedAt = 0L
        Log.d(TAG, "服务已销毁")
        super.onDestroy()
        scope.coroutineContext[Job]?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
