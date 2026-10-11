package com.example.autocontrol.ui.home

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
import com.example.autocontrol.service.GrayscaleAccessibilityService
import com.example.autocontrol.service.Scheduler
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class HomeViewModel(
    private val application: Application,
    private val repository: SettingsRepository,
    private val permissionManager: PermissionManager,
) : ViewModel() {

    val state: StateFlow<ControlState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ControlState()
    )

    /**
     * 一次性 UI 提示（Toast/Snackbar）。用带缓冲的 SharedFlow，
     * 让“立即应用灰度”的成败（尤其权限缺失原因）能直接反馈给用户，
     * 而不是像之前那样被直接丢弃、用户只能干等定时触发却不知为何无效。
     */
    private val _toast = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    /**
     * 两个权限/能力的实时状态，供首页卡片展示并随前台刷新。
     * - accessibilityGranted：无障碍服务是否已开启，灰度遮罩必需（可信浮窗才能在其他应用上层
     *   绘制并穿透点击，规避 Android12+ 的 BLOCK_UNTRUSTED_TOUCHES），需在系统设置中开启；
     * - exactAlarmGranted：精确闹钟，定时灰度到点触发必需。
     * 两者都是用户在 App 外（系统设置）授予的，因此需要“回到 App”时重新读取。
     */
    private val _accessibilityGranted = MutableStateFlow(GrayscaleAccessibilityService.isEnabled(application))
    val accessibilityGranted: StateFlow<Boolean> = _accessibilityGranted.asStateFlow()

    private val _exactAlarmGranted = MutableStateFlow(permissionManager.isExactAlarmGranted())
    val exactAlarmGranted: StateFlow<Boolean> = _exactAlarmGranted.asStateFlow()

    /** 从 App 外返回（如系统设置授权）后调用，重新读取权限状态 */
    fun refreshPermissions() {
        _accessibilityGranted.value = GrayscaleAccessibilityService.isEnabled(application)
        _exactAlarmGranted.value = permissionManager.isExactAlarmGranted()
    }

    fun toggleShutdown(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShutdownEnabled(enabled)
            reschedule()
        }
    }

    fun toggleGrayscale(enabled: Boolean) {
        viewModelScope.launch {
            repository.setGrayscaleEnabled(enabled)
            // 开关切换应有即时反馈：立即启停遮罩，再按排程维护时段窗口
            if (enabled) {
                if (GrayscaleAccessibilityService.isEnabled(application)) {
                    GrayscaleAccessibilityService.show(application)
                    _toast.tryEmit("已开启屏幕灰度遮罩")
                    if (!isExactAlarmGranted()) {
                        _toast.tryEmit("定时灰度需先在设置中授予“精确闹钟”权限，否则到点不会自动切换")
                    }
                } else {
                    _toast.tryEmit("请先在系统设置开启本应用的“无障碍”服务（用于绘制灰度层），已为你打开设置页")
                    GrayscaleAccessibilityService.openSettings(application)
                }
            } else {
                GrayscaleAccessibilityService.hide(application)
                _toast.tryEmit("已关闭屏幕灰度遮罩")
            }
            reschedule()
        }
    }

    fun toggleNetwork(enabled: Boolean) {
        viewModelScope.launch {
            repository.setNetworkEnabled(enabled)
            reschedule()
        }
    }

    fun setShutdownTime(time: LocalTime) {
        viewModelScope.launch {
            repository.setShutdownTime(time)
            reschedule()
        }
    }

    fun setGrayscaleStart(time: LocalTime) {
        viewModelScope.launch {
            repository.setGrayscaleStart(time)
            reschedule()
        }
    }

    fun setGrayscaleEnd(time: LocalTime) {
        viewModelScope.launch {
            repository.setGrayscaleEnd(time)
            reschedule()
        }
    }

    fun setNetworkStart(time: LocalTime) {
        viewModelScope.launch {
            repository.setNetworkStart(time)
            reschedule()
        }
    }

    fun setNetworkEnd(time: LocalTime) {
        viewModelScope.launch {
            repository.setNetworkEnd(time)
            reschedule()
        }
    }

    // ---- 权限查询（供 UI 展示真实状态） ----

    fun isNotificationGranted(): Boolean = permissionManager.isNotificationGranted()
    fun isExactAlarmGranted(): Boolean = permissionManager.isExactAlarmGranted()

    /** 灰度遮罩必需的无障碍服务是否已开启 */
    fun isAccessibilityGranted(): Boolean = GrayscaleAccessibilityService.isEnabled(application)

    fun openAccessibilitySettings() = permissionManager.openAccessibilitySettings()

    /** 跳转“精确闹钟”授权设置页 */
    fun openExactAlarmSettings() = permissionManager.openExactAlarmSettings()

    private suspend fun reschedule() {
        val current = repository.state.first()
        Scheduler.rescheduleAll(application, current)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = application as AutoControlApplication
                HomeViewModel(application, app.settingsRepository, app.permissionManager)
            }
        }
    }
}
