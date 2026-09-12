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
import com.example.autocontrol.service.Scheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun toggleShutdown(enabled: Boolean) {
        viewModelScope.launch {
            repository.setShutdownEnabled(enabled)
            reschedule()
        }
    }

    fun toggleGrayscale(enabled: Boolean) {
        viewModelScope.launch {
            repository.setGrayscaleEnabled(enabled)
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

    fun isAccessibilityGranted(): Boolean = permissionManager.isAccessibilityEnabled()
    fun isNotificationGranted(): Boolean = permissionManager.isNotificationGranted()
    fun isExactAlarmGranted(): Boolean = permissionManager.isExactAlarmGranted()

    fun openAccessibilitySettings() = permissionManager.openAccessibilitySettings()

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
