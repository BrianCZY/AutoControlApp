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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
     * 权限缺失时的跳转事件：通知 UI 切到「设置」页。
     * 按照产品约定，所有权限的查询与授权入口都收敛到设置页，首页只做提示 + 引导。
     */
    private val _openPermissionSettings = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val openPermissionSettings: SharedFlow<Unit> = _openPermissionSettings.asSharedFlow()

    fun toggleShutdown(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShutdownEnabled(enabled)
            reschedule()
        }
    }

    fun toggleGrayscale(enabled: Boolean) {
        viewModelScope.launch {
            // 缺少无障碍服务时不开灰度：置回关闭并引导用户去设置页完成授权
            if (enabled && !GrayscaleAccessibilityService.isEnabled(application)) {
                repository.setGrayscaleEnabled(false)
                _toast.tryEmit("请先在「设置」页开启本应用的「无障碍」服务，再开启灰度")
                _openPermissionSettings.tryEmit(Unit)
                reschedule()
                return@launch
            }
            repository.setGrayscaleEnabled(enabled)
            // 开关切换应有即时反馈：立即启停遮罩，再按排程维护时段窗口
            if (enabled) {
                GrayscaleAccessibilityService.show(application)
                _toast.tryEmit("已开启屏幕灰度遮罩")
                if (!permissionManager.isExactAlarmGranted()) {
                    _toast.tryEmit("定时灰度需先在「设置」页授予“精确闹钟”权限，否则到点不会自动切换")
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
