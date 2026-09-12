package com.example.autocontrol.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autocontrol.R
import com.example.autocontrol.ui.components.InfoBanner
import com.example.autocontrol.ui.components.InfoBannerTone
import com.example.autocontrol.ui.components.PermissionRow
import com.example.autocontrol.ui.components.StatusCard
import com.example.autocontrol.ui.components.TagChip
import com.example.autocontrol.ui.home.LocalAppContext
import com.example.autocontrol.ui.theme.BgRoot
import com.example.autocontrol.ui.theme.BgCard
import com.example.autocontrol.ui.theme.Divider
import com.example.autocontrol.ui.theme.TextPrimary
import com.example.autocontrol.ui.theme.TextSecondary
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(LocalAppContext.current))
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 每次页面回到前台时刷新真实权限状态（用户可能刚在系统设置里完成授权）
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
                        onClick = viewModel::onRequestBattery,
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.SettingsRemote,
                        title = stringResource(R.string.perm_foreground_title),
                        subtitle = stringResource(R.string.perm_foreground_subtitle),
                        status = state.foregroundServiceStatus,
                        onClick = viewModel::onRequestForegroundService,
                    )
                }
            }
            item {
                PermissionGroup(
                    label = stringResource(R.string.settings_extra_label),
                ) {
                    PermissionRow(
                        icon = Icons.Outlined.AccessibilityNew,
                        title = stringResource(R.string.perm_a11y_title),
                        subtitle = stringResource(R.string.perm_a11y_subtitle),
                        status = state.accessibilityStatus,
                        onClick = viewModel::onRequestAccessibility,
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.perm_notification_title),
                        subtitle = stringResource(R.string.perm_notification_subtitle),
                        status = state.notificationStatus,
                        onClick = viewModel::onRequestNotification,
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
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            TagChip(text = label)
        }
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            content()
        }
    }
}
