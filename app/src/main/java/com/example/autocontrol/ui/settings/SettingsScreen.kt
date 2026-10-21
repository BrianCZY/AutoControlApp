package com.example.autocontrol.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
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
import com.example.autocontrol.service.PermissionTarget
import com.example.autocontrol.ui.components.InfoBanner
import com.example.autocontrol.ui.components.InfoBannerTone
import com.example.autocontrol.ui.components.PermissionRow
import com.example.autocontrol.ui.components.StatusCard
import com.example.autocontrol.ui.components.TagChip
import com.example.autocontrol.ui.home.LocalAppContext
import com.example.autocontrol.ui.theme.BgCard
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // 待展示指引的权限项（非空时弹出“浮窗式”指引卡）
    var guideTarget by remember { mutableStateOf<PermissionTarget?>(null) }

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
