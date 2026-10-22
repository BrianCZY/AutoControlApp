package com.example.autocontrol.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autocontrol.R
import com.example.autocontrol.data.ControlState
import com.example.autocontrol.service.PermissionTarget
import com.example.autocontrol.ui.components.InfoBanner
import com.example.autocontrol.ui.components.InfoBannerTone
import com.example.autocontrol.ui.components.PermissionRow
import com.example.autocontrol.ui.components.StatusCard
import com.example.autocontrol.ui.components.TagChip
import com.example.autocontrol.ui.home.LocalAppContext
import com.example.autocontrol.ui.theme.BgCard
import com.example.autocontrol.ui.theme.BgButtonDisabled
import com.example.autocontrol.ui.theme.BgCardElevated
import com.example.autocontrol.ui.theme.BgRoot
import com.example.autocontrol.ui.theme.BrandTeal
import com.example.autocontrol.ui.theme.Divider
import com.example.autocontrol.ui.theme.TextOnBrand
import com.example.autocontrol.ui.theme.TextPrimary
import com.example.autocontrol.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(LocalAppContext.current))
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 待展示指引的权限项（非空时弹出“浮窗式”指引卡）
    var guideTarget by remember { mutableStateOf<PermissionTarget?>(null) }

    // 是否正在挑选灰罩颜色
    var showColorPicker by remember { mutableStateOf(false) }

    // 浮窗指引不可用（既没开无障碍、也没给悬浮窗权限）时给出说明
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // 每次页面回到前台时收起系统级指引浮窗，并刷新真实权限状态（用户可能刚在系统设置里完成授权）
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.hideFloatingGuide()
            viewModel.refresh()
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
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_eyebrow),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (state.showAlert) {
                item {
                    InfoBanner(
                        icon = Icons.Outlined.WarningAmber,
                        title = stringResource(R.string.settings_alert_title),
                        subtitle = stringResource(R.string.settings_alert_subtitle),
                        tone = InfoBannerTone.Danger,
                    )
                }
            }
            item {
                GrayscaleDisplayGroup(
                    color = settings.grayscaleColor,
                    alpha = settings.grayscaleAlpha,
                    onPickColor = { showColorPicker = true },
                    onAlphaChange = viewModel::setGrayscaleAlpha,
                )
            }
            item {
                PermissionGroup(
                    label = stringResource(R.string.settings_core_label),
                ) {
                    PermissionRow(
                        icon = Icons.Outlined.BatteryFull,
                        title = stringResource(R.string.perm_battery_title),
                        subtitle = stringResource(R.string.perm_battery_subtitle),
                        status = state.batteryStatus,
                        onClick = { guideTarget = PermissionTarget.Battery },
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.SettingsRemote,
                        title = stringResource(R.string.perm_foreground_title),
                        subtitle = stringResource(R.string.perm_foreground_subtitle),
                        status = state.foregroundServiceStatus,
                        onClick = { guideTarget = PermissionTarget.ForegroundService },
                    )
                }
            }
            item {
                PermissionGroup(
                    label = stringResource(R.string.settings_extra_label),
                ) {
                    PermissionRow(
                        icon = Icons.Outlined.Layers,
                        title = stringResource(R.string.perm_overlay_title),
                        subtitle = stringResource(R.string.perm_overlay_subtitle),
                        status = state.overlayStatus,
                        onClick = { guideTarget = PermissionTarget.Overlay },
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.Visibility,
                        title = stringResource(R.string.perm_accessibility_title),
                        subtitle = stringResource(R.string.perm_accessibility_subtitle),
                        status = state.accessibilityStatus,
                        onClick = { guideTarget = PermissionTarget.Accessibility },
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.Alarm,
                        title = stringResource(R.string.perm_exact_alarm_title),
                        subtitle = stringResource(R.string.perm_exact_alarm_subtitle),
                        status = state.exactAlarmStatus,
                        onClick = { guideTarget = PermissionTarget.ExactAlarm },
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.perm_notification_title),
                        subtitle = stringResource(R.string.perm_notification_subtitle),
                        status = state.notificationStatus,
                        onClick = { guideTarget = PermissionTarget.Notification },
                    )
                }
            }
            item {
                StatusCard(
                    title = stringResource(R.string.service_card_title),
                    status = if (state.serviceRunning) stringResource(R.string.service_status_running) else stringResource(R.string.service_status_inactive),
                    hint = if (state.serviceRunning) stringResource(R.string.service_status_running_hint) else stringResource(R.string.service_status_inactive_hint),
                    actionLabel = if (state.serviceRunning) stringResource(R.string.service_action_stop) else stringResource(R.string.service_action_start),
                    onAction = viewModel::onToggleService,
                    meta = listOf(
                        stringResource(R.string.service_meta_name_label) to stringResource(R.string.service_meta_name_value),
                        stringResource(R.string.service_meta_api_label) to stringResource(R.string.service_meta_api_value),
                        stringResource(R.string.service_meta_strategy_label) to stringResource(R.string.service_meta_strategy_value),
                    ),
                )
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    // 灰罩颜色选择
    if (showColorPicker) {
        GrayscaleColorDialog(
            current = settings.grayscaleColor,
            onConfirm = { rgb ->
                viewModel.setGrayscaleColor(rgb)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    // 权限指引浮窗：点击授权时先说明“到系统设置里点什么”，确认后再跳转
    guideTarget?.let { target ->
        PermissionGuideDialog(
            title = viewModel.guideTitle(target),
            steps = viewModel.guideSteps(target),
            onConfirm = {
                viewModel.onRequest(target)
                guideTarget = null
            },
            onDismiss = { guideTarget = null },
        )
    }
}

/**
 * “浮窗式”指引卡：半透明蒙层 + 悬浮卡片，说明系统设置内的操作步骤。
 * 与系统级浮窗指引（无障碍服务绘制）配合使用——确认后跳到系统设置页，
 * 若无障碍已开启，卡片会继续浮在系统页面上直到用户关闭。
 */
@Composable
private fun PermissionGuideDialog(
    title: String,
    steps: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(BgCardElevated)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💡", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = steps,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 21.sp,
                )
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuideActionButton(
                        text = stringResource(R.string.perm_guide_cancel),
                        primary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    GuideActionButton(
                        text = stringResource(R.string.perm_guide_go),
                        primary = true,
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideActionButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) BrandTeal else BgCard)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) TextOnBrand else TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PermissionGroup(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TagChip(text = label)
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            content()
        }
    }
}

// ------- 灰度显示（颜色值 / 浓度） -------

/**
 * 灰罩的「颜色值 + 浓度」设置。
 *
 * 灰罩的实现是：在全屏最上层叠一层半透明纯色。
 * - 颜色决定偏色（默认中性灰 #808080，不会偏暖/偏冷）；
 * - 浓度（alpha）决定去色强度与压暗程度，越大越暗。
 * 两者都会即时作用于正在显示的灰罩（通过 refreshStyle 广播重绘），方便边调边看。
 */
@Composable
private fun GrayscaleDisplayGroup(
    color: Int,
    alpha: Int,
    onPickColor: () -> Unit,
    onAlphaChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TagChip(text = stringResource(R.string.settings_grayscale_label))
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            // 颜色行：左侧色块 + 名称/说明，右侧色值
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickColor() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF000000.toInt() or color))
                        .border(1.dp, Divider, RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.grayscale_color_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.grayscale_color_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = toHexColor(color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandTeal,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

            // 浓度滑块
            // 拖动时只更新本地状态，松手才写入 DataStore：
            // 避免拖动过程中每帧都触发一次磁盘写入 + 灰罩重绘。
            var dragging by remember(alpha) { mutableStateOf(false) }
            var localPercent by remember(alpha) {
                mutableStateOf(alphaToPercent(alpha).toFloat())
            }
            val shownPercent = if (dragging) localPercent else alphaToPercent(alpha).toFloat()
            // 预览用的即时 alpha：拖动时跟着滑块走，松手后以持久值收口
            val previewAlpha = percentToAlpha(shownPercent.toInt())

            Column(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.grayscale_alpha_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.grayscale_alpha_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            R.string.grayscale_alpha_value,
                            shownPercent.toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BrandTeal,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value = shownPercent,
                    onValueChange = {
                        dragging = true
                        localPercent = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onAlphaChange(percentToAlpha(localPercent.toInt()))
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = BrandTeal,
                        activeTrackColor = BrandTeal,
                        inactiveTrackColor = BgButtonDisabled,
                    ),
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))

            // 效果预览：把色块压在一段示意内容上，直观看出浓度
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.grayscale_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCardElevated),
                ) {
                    // 底层：几块彩色示意，用于体现"去色"效果
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            Color(0xFFEF4444), Color(0xFFF59E0B),
                            Color(0xFF2DD4BF), Color(0xFF3B82F6),
                            Color(0xFF8B5CF6),
                        ).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c)
                            )
                        }
                    }
                    // 上层：与真实灰罩同色同 alpha
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color(
                                    alpha = previewAlpha and 0xFF,
                                    red = (color shr 16) and 0xFF,
                                    green = (color shr 8) and 0xFF,
                                    blue = color and 0xFF,
                                )
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.grayscale_preview_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun GrayscaleColorDialog(
    current: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // 预设以中性灰为主（真灰度），另附两种微暖/微冷，避免只能千篇一律
    val presets = listOf(
        0x9E9E9E to "浅灰",
        0x808080 to "标准灰",
        0x616161 to "深灰",
        0x424242 to "暗夜灰",
        0x8C8378 to "暖灰",
        0x78828C to "冷灰",
    )
    var selected by remember(current) { mutableStateOf(current) }
    var hexInput by remember(current) {
        mutableStateOf(toHexColor(current))
    }
    var error by remember(current) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BgCardElevated)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.grayscale_color_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))

            // 预设色块网格（每行 3 个）
            presets.chunked(3).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowItems.forEach { (rgb, name) ->
                        val isSelected = rgb == selected
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BgCard else Color.Transparent)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) BrandTeal else Divider,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    selected = rgb
                                    hexInput = toHexColor(rgb)
                                    error = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF000000.toInt() or rgb))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) TextPrimary else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    hexInput = input
                    val parsed = parseHexColor(input)
                    if (parsed != null) {
                        selected = parsed
                        error = false
                    } else {
                        error = true
                    }
                },
                label = { Text(stringResource(R.string.grayscale_color_hex_label)) },
                singleLine = true,
                isError = error,
                supportingText = if (error) {
                    { Text(stringResource(R.string.grayscale_color_invalid)) }
                } else {
                    null
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandTeal,
                    unfocusedBorderColor = Divider,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GuideActionButton(
                    text = stringResource(R.string.action_cancel),
                    primary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                GuideActionButton(
                    text = stringResource(R.string.action_confirm),
                    primary = true,
                    onClick = { onConfirm(selected) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 0xRRGGBB → "#RRGGBB" */
private fun toHexColor(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

/** 宽松解析：接受 "#808080" / "808080" / "0x808080" */
private fun parseHexColor(input: String): Int? {
    val cleaned = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    if (cleaned.length != 6) return null
    return cleaned.toIntOrNull(16)?.takeIf { it in 0..0xFFFFFF }
}

/** alpha(0..255) → 百分比(0..100)，以配置的可用区间为映射基准 */
private fun alphaToPercent(alpha: Int): Int {
    val min = ControlState.MIN_GRAYSCALE_ALPHA
    val max = ControlState.MAX_GRAYSCALE_ALPHA
    val clamped = alpha.coerceIn(min, max)
    return ((clamped - min) * 100f / (max - min)).toInt()
}

/** 百分比(0..100) → alpha(0..255) */
private fun percentToAlpha(percent: Int): Int {
    val min = ControlState.MIN_GRAYSCALE_ALPHA
    val max = ControlState.MAX_GRAYSCALE_ALPHA
    return (min + (max - min) * (percent.coerceIn(0, 100) / 100f)).toInt()
}
