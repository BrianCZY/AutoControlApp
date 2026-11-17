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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autocontrol.R
import com.example.autocontrol.data.ControlState
import com.example.autocontrol.service.GrayscaleSchedule
import com.example.autocontrol.ui.components.ControlCard
import com.example.autocontrol.ui.components.ControlStatusRow
import com.example.autocontrol.ui.components.TealSwitch
import com.example.autocontrol.ui.components.TimeRangePickerField
import com.example.autocontrol.ui.theme.AutoControlTheme
import com.example.autocontrol.ui.theme.BgCard
import com.example.autocontrol.ui.theme.BgRoot
import com.example.autocontrol.ui.theme.Divider
import com.example.autocontrol.ui.theme.TextPrimary
import com.example.autocontrol.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onGoToSettings: () -> Unit = {},
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(LocalAppContext.current))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickerTarget by rememberSaveable(stateSaver = TimePickerTargetSaver) {
        mutableStateOf<TimePickerTarget?>(null)
    }

    // 把灰度操作的成败（含权限缺失原因）以 Toast 形式即时反馈给用户
    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // 权限不满足时，授权入口统一在「设置」页，这里只负责把用户带过去
    LaunchedEffect(Unit) {
        viewModel.openPermissionSettings.collect { onGoToSettings() }
    }

    // 每次回到首页都按当前时间校正一次灰罩：
    // 兜住「系统延迟/错过闹钟」「锁屏期间跨过开始时间」等情形。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumeSync()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 每 30 秒刷新一次“当前时间”，用于在卡片上标注是否已进入灰度时段。
    // 不依赖它来驱动灰罩（灰罩由闹钟+各入口校正），仅作展示，故刷新频率不必更高。
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(30_000)
        }
    }

    HomeContent(
        state = state,
        now = now,
        contentPadding = contentPadding,
        onToggleShutdown = viewModel::toggleShutdown,
        onToggleGrayscale = viewModel::toggleGrayscale,
        onToggleNetwork = viewModel::toggleNetwork,
        onPickShutdownTime = { pickerTarget = TimePickerTarget.Shutdown(state.shutdownTime) },
        onPickGrayscaleStart = { pickerTarget = TimePickerTarget.Start(state.grayscaleStart) },
        onPickGrayscaleEnd = { pickerTarget = TimePickerTarget.End(state.grayscaleEnd) },
        onPickNetworkStart = { pickerTarget = TimePickerTarget.NetworkStart(state.networkStart) },
        onPickNetworkEnd = { pickerTarget = TimePickerTarget.NetworkEnd(state.networkEnd) },
    )

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

/**
 * 首页的**无状态**内容层。
 *
 * 从 [HomeScreen] 中拆分出来，目的有两个：
 * 1. 让 `@Preview` 能直接渲染整屏（预览环境里没有 ViewModel / DataStore）；
 * 2. 布局与状态解耦，便于测试不同的 [ControlState] 组合。
 *
 * 所有交互都通过回调向外抛，自身不持有任何可变状态。
 */
@Composable
fun HomeContent(
    state: ControlState,
    now: LocalTime,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onToggleShutdown: (Boolean) -> Unit = {},
    onToggleGrayscale: (Boolean) -> Unit = {},
    onToggleNetwork: (Boolean) -> Unit = {},
    onPickShutdownTime: () -> Unit = {},
    onPickGrayscaleStart: () -> Unit = {},
    onPickGrayscaleEnd: () -> Unit = {},
    onPickNetworkStart: () -> Unit = {},
    onPickNetworkEnd: () -> Unit = {},
) {
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

           /* item {
                ShutdownCard(
                    state = state,
                    onToggle = onToggleShutdown,
                    onPickTime = onPickShutdownTime,
                )
            }*/
            item {
                GrayscaleCard(
                    state = state,
                    now = now,
                    onToggle = onToggleGrayscale,
                    onPickStart = onPickGrayscaleStart,
                    onPickEnd = onPickGrayscaleEnd,
                )
            }
        /*    item {
                NetworkCard(
                    state = state,
                    onToggle = onToggleNetwork,
                    onPickStart = onPickNetworkStart,
                    onPickEnd = onPickNetworkEnd,
                )
            }*/
            item { Spacer(Modifier.height(20.dp)) }
        }
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
    now: LocalTime,
    onToggle: (Boolean) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    // 当前是否正处于灰度时段内：用于状态摘要，让用户一眼看出“为什么没变灰”
    val inWindow = GrayscaleSchedule.isInWindow(now, state.grayscaleStart, state.grayscaleEnd)
    ControlCard(
        icon = Icons.Filled.Brightness4,
        title = stringResource(R.string.grayscale_title),
        subtitle = stringResource(R.string.grayscale_subtitle),
    ) {
        ControlStatusRow(
            label = stringResource(R.string.grayscale_label),
            status = when {
                !state.grayscaleEnabled -> stringResource(
                    R.string.grayscale_summary_disabled,
                    state.grayscaleStart.format(TimeFormatter),
                    state.grayscaleEnd.format(TimeFormatter),
                )
                inWindow -> stringResource(
                    R.string.grayscale_summary_active,
                    state.grayscaleStart.format(TimeFormatter),
                    state.grayscaleEnd.format(TimeFormatter),
                )
                else -> stringResource(
                    R.string.grayscale_summary_waiting,
                    state.grayscaleStart.format(TimeFormatter),
                    state.grayscaleEnd.format(TimeFormatter),
                )
            },
            trailing = { TealSwitch(checked = state.grayscaleEnabled, onCheckedChange = onToggle) },
        )
        // 时段编辑始终可用：开关只控制「是否执行该策略」，不影响「怎么设置策略」。
        // 之前的做法是关闭时隐藏选择器，导致用户想先设好时段再开启时无处可点。
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
 * 灰度所需的权限（无障碍服务 / 精确闹钟）统一在「设置」页授权与展示，
 * 首页只保留开关与时段配置；缺权限时开关会置回关闭并把用户带到设置页。
 */

// ======================= Compose 预览 =======================
//
// 预览直接渲染无状态的 [HomeContent]，因此不需要 ViewModel / DataStore / 系统权限。
// 在 Android Studio 中打开本文件，切到 Split / Design 即可看到 4 种状态。

/** 夜间灰度时段（默认配置形态） */
private val PreviewNightState = ControlState()

/** 全部功能开启：布局最长，用于检查提示行换行与整体高度 */
private fun previewAllEnabledState() = ControlState(
    shutdownEnabled = true,
    shutdownTime = LocalTime.of(23, 30),
    grayscaleEnabled = true,
    grayscaleStart = LocalTime.of(22, 0),
    grayscaleEnd = LocalTime.of(7, 0),
    networkEnabled = true,
    networkStart = LocalTime.of(23, 0),
    networkEnd = LocalTime.of(7, 0),
)

/** 全部关闭：三张卡片折叠为「未启用」，检查空状态视觉重量 */
private fun previewAllDisabledState() = ControlState(
    shutdownEnabled = false,
    grayscaleEnabled = false,
    networkEnabled = false,
)

/** 最常见的实际使用状态：只开灰度 */
@Preview(
    name = "首页 · 仅灰度开启",
    group = "首页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=851dp,dpi=440",
)
@Composable
private fun HomePreview_GrayscaleOnly() {
    AutoControlTheme {
        HomeContent(state = PreviewNightState, now = LocalTime.of(23, 0))
    }
}

/** 灰度已启用但当前不在时段内（白天）：摘要应显示「未到时段」 */
@Preview(
    name = "首页 · 灰度未到时段",
    group = "首页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=851dp,dpi=440",
)
@Composable
private fun HomePreview_GrayscaleWaiting() {
    AutoControlTheme {
        HomeContent(state = PreviewNightState, now = LocalTime.of(14, 0))
    }
}

/** 全部开启：卡片全部展开，高度放大以便一屏看全 */
@Preview(
    name = "首页 · 全部开启",
    group = "首页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=1200dp,dpi=440",
)
@Composable
private fun HomePreview_AllEnabled() {
    AutoControlTheme {
        HomeContent(state = previewAllEnabledState(), now = LocalTime.of(23, 10))
    }
}

/** 全部关闭：灰度卡仍展开（时段可编辑），关机与断网折叠，检查空状态视觉重量 */
@Preview(
    name = "首页 · 全部关闭",
    group = "首页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=950dp,dpi=440",
)
@Composable
private fun HomePreview_AllDisabled() {
    AutoControlTheme {
        HomeContent(state = previewAllDisabledState(), now = LocalTime.of(14, 0))
    }
}
