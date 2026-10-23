package com.example.autocontrol.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.autocontrol.AutoControlApplication
import com.example.autocontrol.data.ControlState
import com.example.autocontrol.data.PermissionManager
import com.example.autocontrol.data.SettingsRepository
import com.example.autocontrol.service.A11yDiagnostics
import com.example.autocontrol.service.AutoControlService
import com.example.autocontrol.service.GrayscaleAccessibilityService
import com.example.autocontrol.service.PermissionGuide
import com.example.autocontrol.service.PermissionTarget
import com.example.autocontrol.service.ServiceKeeper
import com.example.autocontrol.ui.components.PermissionStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 设置页 UI 状态，权限状态全部来自真实的系统查询。
 */
data class SettingsUiState(
    val batteryStatus: PermissionStatus = PermissionStatus.NotGranted,
    val foregroundServiceStatus: PermissionStatus = PermissionStatus.Granted,
    val accessibilityStatus: PermissionStatus = PermissionStatus.NotGranted,
    val notificationStatus: PermissionStatus = PermissionStatus.NotGranted,
    val exactAlarmStatus: PermissionStatus = PermissionStatus.NotGranted,
    val overlayStatus: PermissionStatus = PermissionStatus.NotGranted,
    val serviceRunning: Boolean = false,
    /** 服务在当前进程里是否真的活着 */
    val serviceAlive: Boolean = false,
    /** 服务已运行分钟数；未运行为 null */
    val serviceUptimeMinutes: Int? = null,
    /** 无障碍曾经开启过、但现在被系统关闭了（与「从未开启」区分开） */
    val accessibilityWasRevoked: Boolean = false,
    /** 无障碍服务最近一次生命周期事件，便于排查被关闭的原因 */
    val accessibilityHint: String? = null,
) {
    val coreGrantedCount: Int
        get() = listOf(batteryStatus, foregroundServiceStatus).count { it == PermissionStatus.Granted }

    /** 缺少任一必需权限都提示：无障碍决定灰罩能否绘制，精确闹钟决定定时能否准点触发 */
    val showAlert: Boolean
        get() = coreGrantedCount < 2 ||
            accessibilityStatus == PermissionStatus.NotGranted ||
            exactAlarmStatus == PermissionStatus.NotGranted
}

class SettingsViewModel(
    private val application: Application,
    private val repository: SettingsRepository,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    /** 灰度显示配置（颜色/浓度），供设置页实时展示 */
    val settings: StateFlow<ControlState> = repository.state

    /** 一次性提示（例如浮窗指引不可用时的说明） */
    private val _toast = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    init {
        refresh()
    }

    /** 从系统重新读取真实权限状态 */
    fun refresh() {
        val accessibilityOn = GrayscaleAccessibilityService.isEnabled(application)
        _ui.value = SettingsUiState(
            batteryStatus = if (permissionManager.isIgnoringBatteryOptimizations()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            foregroundServiceStatus = PermissionStatus.Granted,
            accessibilityStatus = if (accessibilityOn) PermissionStatus.Granted else PermissionStatus.NotGranted,
            notificationStatus = if (permissionManager.isNotificationGranted()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            exactAlarmStatus = if (permissionManager.isExactAlarmGranted()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            overlayStatus = if (permissionManager.canDrawOverlays()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            // 用户意图（是否启用保活）
            serviceRunning = ServiceKeeper.shouldRun(application),
            // 服务进程内的真实存活状态：比 shouldRun 更能反映"到底还在不在跑"
            serviceAlive = AutoControlService.alive,
            serviceUptimeMinutes = if (AutoControlService.alive && AutoControlService.startedAt > 0) {
                ((System.currentTimeMillis() - AutoControlService.startedAt) / 60_000L).toInt()
            } else {
                null
            },
            // 区分两种「未开启」：从未启用过 vs 曾经启用但被系统解绑（后者需要专门提示）
            accessibilityWasRevoked = !accessibilityOn && A11yDiagnostics.everConnected(application),
            accessibilityHint = A11yDiagnostics.summary(application),
        )
    }

    /** 权限的标题与步骤，供应用内“浮窗式指引卡”展示 */
    fun guideTitle(target: PermissionTarget): String = PermissionGuide.title(application, target)

    fun guideSteps(target: PermissionTarget): String = PermissionGuide.steps(application, target)

    /**
     * 统一的授权入口：先把指引浮窗挂到系统设置页上方（无障碍已开启时），再跳到对应系统页面。
     * 所有权限设置都从设置页发起，首页不再保留任何授权按钮。
     */
    fun onRequest(target: PermissionTarget) {
        val floatingShown = PermissionGuide.showFloating(application, target)
        if (!floatingShown && target != PermissionTarget.Overlay) {
            _toast.tryEmit("系统页上的浮窗指引需要先授予「悬浮窗」权限，可在本页对应项开启")
        }
        when (target) {
            PermissionTarget.Overlay -> permissionManager.openOverlaySettings()
            PermissionTarget.Battery -> permissionManager.openBatteryOptimizationSettings()
            PermissionTarget.ForegroundService -> permissionManager.openAppSettings()
            PermissionTarget.Accessibility -> permissionManager.openAccessibilitySettings()
            PermissionTarget.Notification -> permissionManager.openNotificationSettings()
            PermissionTarget.ExactAlarm -> permissionManager.openExactAlarmSettings()
        }
    }

    /** 回到本应用后收起系统级指引浮窗 */
    fun hideFloatingGuide() = PermissionGuide.hideFloating(application)

    // ---- 灰度颜色值 / 浓度 ----

    /** 修改灰罩颜色（0xRRGGBB），改动立即作用于正在显示的灰罩 */
    fun setGrayscaleColor(rgb: Int) {
        viewModelScope.launch {
            repository.setGrayscaleColor(rgb)
            GrayscaleAccessibilityService.refreshStyle(
                application,
                rgb and 0xFFFFFF,
                repository.state.value.grayscaleAlpha,
            )
        }
    }

    /** 修改灰罩浓度（0..255），改动立即作用于正在显示的灰罩 */
    fun setGrayscaleAlpha(alpha: Int) {
        viewModelScope.launch {
            repository.setGrayscaleAlpha(alpha)
            GrayscaleAccessibilityService.refreshStyle(
                application,
                repository.state.value.grayscaleColor,
                alpha,
            )
        }
    }

    /**
     * 启停后台服务。
     *
     * 这里的"停止"会同时取消保活心跳并记下用户的意图，
     * 否则 [ServiceKeeper] 会在 15 分钟后把服务重新拉起来，用户会觉得"关不掉"。
     */
    fun onToggleService() {
        val enable = !ServiceKeeper.shouldRun(application)
        if (enable) {
            ServiceKeeper.setUserStopped(application, false)
            ServiceKeeper.ensureRunning(application)
            _toast.tryEmit("后台服务已启动，关闭应用后仍会继续运行")
        } else {
            ServiceKeeper.setUserStopped(application, true)
            ServiceKeeper.stopService(application)
            _toast.tryEmit("已停止后台服务，定时任务将不再执行")
        }
        _ui.value = _ui.value.copy(serviceRunning = enable)

        // 服务启停是异步的（系统回调有延迟），稍后再刷新一次真实存活状态
        viewModelScope.launch {
            delay(700)
            refresh()
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as AutoControlApplication
                SettingsViewModel(application, app.settingsRepository, app.permissionManager)
            }
        }
    }
}
