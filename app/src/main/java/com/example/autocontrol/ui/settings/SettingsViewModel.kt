package com.example.autocontrol.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.autocontrol.AutoControlApplication
import com.example.autocontrol.data.PermissionManager
import com.example.autocontrol.data.SettingsRepository
import com.example.autocontrol.service.GrayscaleAccessibilityService
import com.example.autocontrol.service.PermissionGuide
import com.example.autocontrol.service.PermissionTarget
import com.example.autocontrol.ui.components.PermissionStatus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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
    @Suppress("unused") private val repository: SettingsRepository,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

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
        _ui.value = SettingsUiState(
            batteryStatus = if (permissionManager.isIgnoringBatteryOptimizations()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            foregroundServiceStatus = PermissionStatus.Granted,
            accessibilityStatus = if (GrayscaleAccessibilityService.isEnabled(application)) PermissionStatus.Granted else PermissionStatus.NotGranted,
            notificationStatus = if (permissionManager.isNotificationGranted()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            exactAlarmStatus = if (permissionManager.isExactAlarmGranted()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            overlayStatus = if (permissionManager.canDrawOverlays()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            serviceRunning = _ui.value.serviceRunning,
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

    fun onToggleService() {
        _ui.value = _ui.value.copy(serviceRunning = !_ui.value.serviceRunning)
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
