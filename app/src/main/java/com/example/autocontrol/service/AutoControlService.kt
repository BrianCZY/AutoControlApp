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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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
         * 灰度监测轮询周期：30 秒。
         *
         * 30s 精度对「到点变灰」足够（用户几乎察觉不到延迟），
         * 同时开销极小：只读内存里的 StateFlow 值 + 一次 Settings.Secure 查询 + 一次应用内广播。
         * 再短没有意义（状态不会在 30s 内变化两次），再长则会明显感到"到点没变灰"。
         */
        private const val MONITOR_INTERVAL_MS = 30_000L

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

    /** 灰度监测循环任务 */
    private var monitorJob: Job? = null

    /** 上一轮监测得出的「应显示」状态，仅用于减少无意义的重复广播 */
    @Volatile
    private var lastGrayscaleDesired: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        alive = true
        startedAt = System.currentTimeMillis()
        NotificationHelper.ensureChannels(this)
        startForegroundCompat()
        // 监测循环随服务一起起来：App 在前台还是后台都不影响它
        startMonitor()
        Log.d(TAG, "服务已创建")
    }

    /**
     * 启动灰度时段监测循环。
     *
     * 为什么必须有这一步：
     * 只依赖 AlarmManager 的一次性精确闹钟是不够的 ——
     * 闹钟可能因为 Doze、精确闹钟权限、ROM 限制、进程被冻结等原因**不送达或延迟送达**，
     * 一旦漏掉，灰罩就永远不会在该时段出现（下一轮闹钟要等一整天）。
     *
     * 因此把「时段判定」变成一个常驻的轻量轮询：
     * 每 [MONITOR_INTERVAL_MS] 读一次配置，算出当前该不该变灰，再校正灰罩。
     * 这样即便闹钟没来，最多晚一个轮询周期就会自动纠正，且具备自愈能力
     * （无障碍服务重连、灰罩被意外移除，下一轮都会恢复）。
     *
     * 闹钟与轮询是互补关系：
     * - 闹钟负责「准点」触发（秒级精度）；
     * - 轮询负责「兜底 + 自愈」（容忍延迟，但绝不漏）。
     */
    private fun startMonitor() {
        if (monitorJob?.isActive == true) return
        monitorJob = scope.launch {
            // 配置一变（改时段、改开关、改颜色）就立刻校正，不必等下一个轮询周期
            launch {
                val app = application as? AutoControlApplication ?: return@launch
                app.settingsRepository.state.collect {
                    runCatching { applyGrayscaleNow() }
                        .onFailure { e -> Log.e(TAG, "配置变更后校正失败", e) }
                }
            }
            // 时间推进的兜底轮询：既负责准点外的校正，也负责灰罩丢失后的自愈
            while (isActive) {
                runCatching { applyGrayscaleNow() }
                    .onFailure { Log.e(TAG, "灰度监测失败", it) }
                delay(MONITOR_INTERVAL_MS)
            }
        }
        Log.d(TAG, "灰度监测已启动，周期 ${MONITOR_INTERVAL_MS / 1000}s")
    }

    /**
     * 按「当前时间 + 当前配置」把灰罩校正到正确状态。
     *
     * 幂等，且带自愈能力：不仅比对"期望值是否变化"，还比对"屏幕上是否真的有遮罩"。
     * 只要两者不一致就重新下发一次，因此悬浮窗被系统清掉、无障碍重连等情况
     * 都能在下一个轮询周期内自动恢复，不需要用户手动干预。
     */
    private fun applyGrayscaleNow() {
        val app = application as? AutoControlApplication ?: return
        // state 是 StateFlow(Eagerly)，value 即最新配置，无需挂起
        val state = app.settingsRepository.state.value
        val desired = GrayscaleSchedule.shouldShow(state)
        val actual = GrayscaleSchedule.isShowing

        if (desired == actual && lastGrayscaleDesired == desired) return

        GrayscaleSchedule.apply(this, state)
        lastGrayscaleDesired = desired
        Log.d(
            TAG,
            "灰度校正：期望=${if (desired) "显示" else "隐藏"}，" +
                "实际=${if (actual) "显示" else "隐藏"}" +
                "（启用=${state.grayscaleEnabled}, 时段=${GrayscaleSchedule.describe(state)}," +
                "悬浮窗=${OverlayManager.canDraw(this)}）"
        )
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
            // 灰度不用 action 区分 on/off：闹钟可能迟到、也可能被跳过，
            // 到点后按「此刻实际时间」重新判定才正确（例如设备休眠跨过了整个时段）。
            Scheduler.ACTION_GRAYSCALE_ON, Scheduler.ACTION_GRAYSCALE_OFF -> handleGrayscaleAlarm()
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

    /**
     * 灰度闹钟到点：按当前时间重新判定该显示还是隐藏。
     *
     * 不再直接信任 action 的 on/off 语义 —— 闹钟有可能延迟数分钟甚至被系统攒批延迟，
     * 此时"开始时刻的闹钟"到点执行时可能已经走出时段，按时间重算才是正确的。
     */
    private fun handleGrayscaleAlarm() {
        val app = application as? AutoControlApplication ?: return
        val state = app.settingsRepository.state.value

        // 两条通道都没有才报错：悬浮窗（首选）或无障碍（降级）
        if (!GrayscaleSchedule.canRender(this)) {
            notifyResult(
                title = getString(R.string.notif_grayscale_on_title),
                result = ActionExecutor.ActionResult(false, getString(R.string.overlay_not_granted))
            )
            return
        }

        val shown = GrayscaleSchedule.apply(this, state)
        lastGrayscaleDesired = shown
        notifyResult(
            title = getString(
                if (shown) R.string.notif_grayscale_on_title else R.string.notif_grayscale_off_title
            ),
            result = ActionExecutor.ActionResult(
                true,
                getString(
                    if (shown) R.string.action_grayscale_on_success
                    else R.string.action_grayscale_off_success
                )
            )
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
        alive = false
        startedAt = 0L
        monitorJob?.cancel()
        monitorJob = null
        Log.d(TAG, "服务已销毁")
        super.onDestroy()
        scope.coroutineContext[Job]?.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
