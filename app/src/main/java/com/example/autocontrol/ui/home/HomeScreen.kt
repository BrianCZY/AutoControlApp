package com.example.autocontrol.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autocontrol.R
import com.example.autocontrol.data.ControlState
import com.example.autocontrol.ui.components.ControlCard
import com.example.autocontrol.ui.components.ControlStatusRow
import com.example.autocontrol.ui.components.TealSwitch
import com.example.autocontrol.ui.components.TimeRangePickerField
import com.example.autocontrol.ui.theme.BgCard
import com.example.autocontrol.ui.theme.BgRoot
import com.example.autocontrol.ui.theme.Divider
import com.example.autocontrol.ui.theme.TextPrimary
import com.example.autocontrol.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(LocalAppContext.current))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val overlay by viewModel.overlayGranted.collectAsStateWithLifecycle()
    val exactAlarm by viewModel.exactAlarmGranted.collectAsStateWithLifecycle()
    var pickerTarget by rememberSaveable(stateSaver = TimePickerTargetSaver) {
        mutableStateOf<TimePickerTarget?>(null)
    }

    // 从 App 外返回（执行 adb / 系统设置授权后）重新读取权限状态，自动消除告警
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 把灰度操作的成败（含权限缺失原因）以 Toast 形式即时反馈给用户
    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgRoot)
            .padding(contentPadding)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DateHeader() }
            item { Spacer(Modifier.height(2.dp)) }
            item { TitleBlock() }
            item { Spacer(Modifier.height(6.dp)) }

            item {
                ShutdownCard(
                    state = state,
                    onToggle = viewModel::toggleShutdown,
                    onPickTime = {
                        pickerTarget = TimePickerTarget.Shutdown(state.shutdownTime)
                    },
                )
            }
            item {
                GrayscaleCard(
                    state = state,
                    hasOverlay = overlay,
                    hasExactAlarm = exactAlarm,
                    onToggle = viewModel::toggleGrayscale,
                    onPickStart = {
                        pickerTarget = TimePickerTarget.Start(state.grayscaleStart)
                    },
                    onPickEnd = {
                        pickerTarget = TimePickerTarget.End(state.grayscaleEnd)
                    },
                    onGrantOverlay = viewModel::openOverlaySettings,
                    onGrantExactAlarm = viewModel::openExactAlarmSettings,
                )
            }
            item {
                NetworkCard(
                    state = state,
                    onToggle = viewModel::toggleNetwork,
                    onPickStart = {
                        pickerTarget = TimePickerTarget.NetworkStart(state.networkStart)
                    },
                    onPickEnd = {
                        pickerTarget = TimePickerTarget.NetworkEnd(state.networkEnd)
                    },
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    pickerTarget?.let { target ->
        TimePickerDialog(
            initial = target.initial,
            onConfirm = { picked ->
                when (target) {
                    is TimePickerTarget.Shutdown -> viewModel.setShutdownTime(picked)
                    is TimePickerTarget.Start -> viewModel.setGrayscaleStart(picked)
                    is TimePickerTarget.End -> viewModel.setGrayscaleEnd(picked)
                    is TimePickerTarget.NetworkStart -> viewModel.setNetworkStart(picked)
                    is TimePickerTarget.NetworkEnd -> viewModel.setNetworkEnd(picked)
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null },
        )
    }
}

@Composable
private fun DateHeader() {
    val today = rememberSaveable { LocalDate.now() }
    Text(
        text = today.format(DateFormatter),
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun TitleBlock() {
    Text(
        text = stringResource(R.string.home_title),
        style = MaterialTheme.typography.displaySmall,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ShutdownCard(
    state: ControlState,
    onToggle: (Boolean) -> Unit,
    onPickTime: () -> Unit,
) {
    ControlCard(
        icon = Icons.Filled.PowerSettingsNew,
        title = stringResource(R.string.shutdown_title),
        subtitle = stringResource(R.string.shutdown_subtitle),
    ) {
        ControlStatusRow(
            label = stringResource(R.string.shutdown_label),
            status = if (state.shutdownEnabled) stringResource(R.string.status_enabled) else stringResource(R.string.shutdown_status_disabled),
            trailing = { TealSwitch(checked = state.shutdownEnabled, onCheckedChange = onToggle) },
        )
        if (state.shutdownEnabled) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider)
            )
            Spacer(Modifier.height(12.dp))
            TimeField(
                label = stringResource(R.string.shutdown_time_label),
                time = state.shutdownTime,
                onClick = onPickTime,
            )
        }
    }
}

@Composable
private fun GrayscaleCard(
    state: ControlState,
    hasOverlay: Boolean,
    hasExactAlarm: Boolean,
    onToggle: (Boolean) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onGrantOverlay: () -> Unit,
    onGrantExactAlarm: () -> Unit,
) {
    ControlCard(
        icon = Icons.Filled.Brightness4,
        title = stringResource(R.string.grayscale_title),
        subtitle = stringResource(R.string.grayscale_subtitle),
    ) {
        ControlStatusRow(
            label = stringResource(R.string.grayscale_label),
            status = stringResource(
                R.string.grayscale_time_summary,
                state.grayscaleStart.format(TimeFormatter),
                state.grayscaleEnd.format(TimeFormatter),
            ),
            trailing = { TealSwitch(checked = state.grayscaleEnabled, onCheckedChange = onToggle) },
        )
        // 缺少悬浮窗权限时，无论是否启用都先提示授权——否则遮罩无法绘制
        if (!hasOverlay) {
            Spacer(Modifier.height(12.dp))
            TipRow(
                text = stringResource(R.string.grayscale_permission_tip),
                warning = true,
                onClick = onGrantOverlay,
                actionText = stringResource(R.string.grayscale_grant_overlay),
            )
        }
        // 定时灰度依赖精确闹钟权限：未授予时 alarm 不会触发，到点不会自动切换
        if (state.grayscaleEnabled && !hasExactAlarm) {
            Spacer(Modifier.height(12.dp))
            TipRow(
                text = stringResource(R.string.grayscale_exact_alarm_tip),
                warning = true,
                onClick = onGrantExactAlarm,
                actionText = stringResource(R.string.grayscale_grant_exact_alarm),
            )
        }
        if (state.grayscaleEnabled) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider)
            )
            Spacer(Modifier.height(14.dp))
            TimeRangePickerField(
                start = state.grayscaleStart,
                end = state.grayscaleEnd,
                onPickStart = onPickStart,
                onPickEnd = onPickEnd,
            )
            Spacer(Modifier.height(10.dp))
            TipRow(text = stringResource(R.string.grayscale_tip))
        }
    }
}

@Composable
private fun NetworkCard(
    state: ControlState,
    onToggle: (Boolean) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    ControlCard(
        icon = Icons.Filled.SignalCellularOff,
        title = stringResource(R.string.network_title),
        subtitle = stringResource(R.string.network_subtitle),
    ) {
        ControlStatusRow(
            label = stringResource(R.string.network_label),
            status = if (state.networkEnabled) stringResource(R.string.status_enabled) else stringResource(R.string.network_status_disabled),
            trailing = { TealSwitch(checked = state.networkEnabled, onCheckedChange = onToggle) },
        )
        if (state.networkEnabled) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider)
            )
            Spacer(Modifier.height(14.dp))
            TimeRangePickerField(
                start = state.networkStart,
                end = state.networkEnd,
                onPickStart = onPickStart,
                onPickEnd = onPickEnd,
                label = stringResource(R.string.network_time_range_label),
                summaryHint = stringResource(R.string.network_time_summary_hint),
            )
        }
    }
}

@Composable
private fun TimeField(
    label: String,
    time: LocalTime,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BgCard)
                .border(1.dp, Divider, RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = time.format(TimeFormatter),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TipRow(
    text: String,
    warning: Boolean = false,
    onClick: (() -> Unit)? = null,
    actionText: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (warning) "⚠️" else "💡",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        if (onClick != null && actionText != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) androidx.compose.ui.graphics.Color(0xFFE0A44A) else TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = actionText,
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color(0xFF2BB6A6),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onClick() },
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) androidx.compose.ui.graphics.Color(0xFFE0A44A) else TextSecondary,
            )
        }
    }
}

// ------- Time picker dialog -------

private sealed class TimePickerTarget {
    abstract val initial: LocalTime
    data class Shutdown(override val initial: LocalTime) : TimePickerTarget()
    data class Start(override val initial: LocalTime) : TimePickerTarget()
    data class End(override val initial: LocalTime) : TimePickerTarget()
    data class NetworkStart(override val initial: LocalTime) : TimePickerTarget()
    data class NetworkEnd(override val initial: LocalTime) : TimePickerTarget()
}

private val TimePickerTargetSaver: Saver<TimePickerTarget?, String> = Saver(
    save = { target ->
        when (target) {
            null -> "null"
            is TimePickerTarget.Shutdown -> "shutdown:${target.initial.hour}:${target.initial.minute}"
            is TimePickerTarget.Start -> "start:${target.initial.hour}:${target.initial.minute}"
            is TimePickerTarget.End -> "end:${target.initial.hour}:${target.initial.minute}"
            is TimePickerTarget.NetworkStart -> "nstart:${target.initial.hour}:${target.initial.minute}"
            is TimePickerTarget.NetworkEnd -> "nend:${target.initial.hour}:${target.initial.minute}"
        }
    },
    restore = { saved ->
        when (saved) {
            "null" -> null
            else -> {
                val parts = saved.split(":")
                val tag = parts[0]
                val h = parts[1].toInt()
                val m = parts[2].toInt()
                when (tag) {
                    "shutdown" -> TimePickerTarget.Shutdown(LocalTime.of(h, m))
                    "start" -> TimePickerTarget.Start(LocalTime.of(h, m))
                    "end" -> TimePickerTarget.End(LocalTime.of(h, m))
                    "nstart" -> TimePickerTarget.NetworkStart(LocalTime.of(h, m))
                    "nend" -> TimePickerTarget.NetworkEnd(LocalTime.of(h, m))
                    else -> null
                }
            }
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.time_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.time_picker_cancel)) }
        },
        title = { Text(stringResource(R.string.time_picker_title)) },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
    )
}

/**
 * 灰度遮罩所需的悬浮窗权限(SYSTEM_ALERT_WINDOW)无法在 App 内直接申请，
 * 但可在系统设置页内点开授予——首页卡片与设置页均提供“前往授予”入口。
 */
