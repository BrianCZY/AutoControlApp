package com.example.autocontrol.ui.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.autocontrol.AutoControlApplication
import com.example.autocontrol.data.PermissionManager
import com.example.autocontrol.data.SettingsRepository
import com.example.autocontrol.ui.components.PermissionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 设置页 UI 状态，权限状态全部来自真实的系统查询。
 */
data class SettingsUiState(
    val batteryStatus: PermissionStatus = PermissionStatus.NotGranted,
    val foregroundServiceStatus: PermissionStatus = PermissionStatus.Granted,
    val accessibilityStatus: PermissionStatus = PermissionStatus.NotGranted,
    val notificationStatus: PermissionStatus = PermissionStatus.NotGranted,
    val serviceRunning: Boolean = false,
) {
    val coreGrantedCount: Int
        get() = listOf(batteryStatus, foregroundServiceStatus).count { it == PermissionStatus.Granted }

    val showAlert: Boolean
        get() = coreGrantedCount < 2 || accessibilityStatus == PermissionStatus.NotGranted
}

class SettingsViewModel(
    private val application: Application,
    @Suppress("unused") private val repository: SettingsRepository,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    /** 从系统重新读取真实权限状态 */
    fun refresh() {
        _ui.value = SettingsUiState(
            batteryStatus = if (permissionManager.isIgnoringBatteryOptimizations()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            foregroundServiceStatus = PermissionStatus.Granted,
            accessibilityStatus = if (permissionManager.isAccessibilityEnabled()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            notificationStatus = if (permissionManager.isNotificationGranted()) PermissionStatus.Granted else PermissionStatus.NotGranted,
            serviceRunning = _ui.value.serviceRunning,
        )
    }

    fun onRequestBattery() = permissionManager.openBatteryOptimizationSettings()

    fun onRequestForegroundService() = permissionManager.openAppSettings()

    fun onRequestAccessibility() = permissionManager.openAccessibilitySettings()

    fun onRequestNotification() = permissionManager.openNotificationSettings()

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
