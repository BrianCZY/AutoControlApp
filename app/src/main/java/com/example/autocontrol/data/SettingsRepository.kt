package com.example.autocontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_control_prefs")

object PrefKeys {
    val ShutdownEnabled = booleanPreferencesKey("shutdown_enabled")
    val ShutdownTime = stringPreferencesKey("shutdown_time") // "HH:mm"
    val GrayscaleEnabled = booleanPreferencesKey("grayscale_enabled")
    val GrayscaleStart = stringPreferencesKey("grayscale_start") // "HH:mm"
    val GrayscaleEnd = stringPreferencesKey("grayscale_end")
    val GrayscaleColor = intPreferencesKey("grayscale_color") // 0xRRGGBB
    val GrayscaleAlpha = intPreferencesKey("grayscale_alpha") // 0..255
    val NetworkEnabled = booleanPreferencesKey("network_enabled")
    val NetworkStart = stringPreferencesKey("network_start")
    val NetworkEnd = stringPreferencesKey("network_end")
}

/**
 * DataStore 仓库，封装"控制设置"的持久化与读取。
 * 对外暴露 [state] (StateFlow) 和若干 suspend 写入方法。
 * 使用 StateFlow（而非冷 Flow）是为了让非协程场景（如无障碍服务 onServiceConnected）
 * 能直接读取最新的灰度开关状态。
 */
class SettingsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val state: StateFlow<ControlState> = context.dataStore.data.map { prefs ->
        ControlState(
            shutdownEnabled = prefs[PrefKeys.ShutdownEnabled] ?: false,
            shutdownTime = prefs[PrefKeys.ShutdownTime]?.toLocalTimeOrNull() ?: LocalTime.of(23, 0),
            grayscaleEnabled = prefs[PrefKeys.GrayscaleEnabled] ?: true,
            grayscaleStart = prefs[PrefKeys.GrayscaleStart]?.toLocalTimeOrNull() ?: LocalTime.of(22, 0),
            grayscaleEnd = prefs[PrefKeys.GrayscaleEnd]?.toLocalTimeOrNull() ?: LocalTime.of(7, 0),
            grayscaleColor = prefs[PrefKeys.GrayscaleColor] ?: ControlState.DEFAULT_GRAYSCALE_COLOR,
            grayscaleAlpha = prefs[PrefKeys.GrayscaleAlpha] ?: ControlState.DEFAULT_GRAYSCALE_ALPHA,
            networkEnabled = prefs[PrefKeys.NetworkEnabled] ?: false,
            networkStart = prefs[PrefKeys.NetworkStart]?.toLocalTimeOrNull() ?: LocalTime.of(23, 0),
            networkEnd = prefs[PrefKeys.NetworkEnd]?.toLocalTimeOrNull() ?: LocalTime.of(7, 0),
        )
    }.stateIn(scope, SharingStarted.Eagerly, ControlState())

    suspend fun setShutdownEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.ShutdownEnabled] = enabled }
    }

    suspend fun setShutdownTime(time: LocalTime) {
        context.dataStore.edit { it[PrefKeys.ShutdownTime] = time.toHhmm() }
    }

    suspend fun setGrayscaleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.GrayscaleEnabled] = enabled }
    }

    suspend fun setGrayscaleStart(time: LocalTime) {
        context.dataStore.edit { it[PrefKeys.GrayscaleStart] = time.toHhmm() }
    }

    suspend fun setGrayscaleEnd(time: LocalTime) {
        context.dataStore.edit { it[PrefKeys.GrayscaleEnd] = time.toHhmm() }
    }

    /** 灰罩颜色（0xRRGGBB） */
    suspend fun setGrayscaleColor(rgb: Int) {
        context.dataStore.edit { it[PrefKeys.GrayscaleColor] = rgb and 0xFFFFFF }
    }

    /** 灰罩浓度（0..255） */
    suspend fun setGrayscaleAlpha(alpha: Int) {
        context.dataStore.edit {
            it[PrefKeys.GrayscaleAlpha] = alpha.coerceIn(
                ControlState.MIN_GRAYSCALE_ALPHA,
                ControlState.MAX_GRAYSCALE_ALPHA,
            )
        }
    }

    suspend fun setNetworkEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PrefKeys.NetworkEnabled] = enabled }
    }

    suspend fun setNetworkStart(time: LocalTime) {
        context.dataStore.edit { it[PrefKeys.NetworkStart] = time.toHhmm() }
    }

    suspend fun setNetworkEnd(time: LocalTime) {
        context.dataStore.edit { it[PrefKeys.NetworkEnd] = time.toHhmm() }
    }

    private fun LocalTime.toHhmm(): String =
        "%02d:%02d".format(hour, minute)

    private fun String.toLocalTimeOrNull(): LocalTime? = runCatching {
        val (h, m) = split(":").map { it.toInt() }
        LocalTime.of(h, m)
    }.getOrNull()
}
