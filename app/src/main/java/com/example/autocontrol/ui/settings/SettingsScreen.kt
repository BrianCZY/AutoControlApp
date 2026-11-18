package com.example.autocontrol.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autocontrol.R
import com.example.autocontrol.data.ControlState
import com.example.autocontrol.service.PermissionTarget
import com.example.autocontrol.service.RecentsController
import com.example.autocontrol.ui.components.InfoBanner
import com.example.autocontrol.ui.components.InfoBannerTone
import com.example.autocontrol.ui.components.PermissionRow
import com.example.autocontrol.ui.components.PermissionStatus
import com.example.autocontrol.ui.components.StatusCard
import com.example.autocontrol.ui.components.TagChip
import com.example.autocontrol.ui.components.TealSwitch
import com.example.autocontrol.ui.home.LocalAppContext
import com.example.autocontrol.ui.theme.AutoControlTheme
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

    SettingsContent(
        state = state,
        settings = settings,
        contentPadding = contentPadding,
        onToggleService = viewModel::onToggleService,
        onPickColor = { showColorPicker = true },
        onAlphaChange = viewModel::setGrayscaleAlpha,
        onToggleHideFromRecents = viewModel::setHideFromRecents,
        onPermissionClick = { guideTarget = it },
    )

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
 * 设置页的**无状态**内容层。
 *
 * 从 [SettingsScreen] 拆出，供 `@Preview` 与 UI 测试直接渲染：
 * 预览环境里没有 ViewModel、也没有真实系统权限，只能靠注入的 [SettingsUiState]。
 * 所有动作通过回调向外抛，自身不持有可变状态（除滑块拖动这类纯视觉临时量）。
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    settings: ControlState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onToggleService: () -> Unit = {},
    onPickColor: () -> Unit = {},
    onAlphaChange: (Int) -> Unit = {},
    onToggleHideFromRecents: (Boolean) -> Unit = {},
    onPermissionClick: (PermissionTarget) -> Unit = {},
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
                    onPickColor = onPickColor,
                    onAlphaChange = onAlphaChange,
                )
            }
            item {
                AppDisplayGroup(
                    hideFromRecents = settings.hideFromRecents,
                    onToggle = onToggleHideFromRecents,
                )
            }
            item {
                PermissionGroup(
                    label = stringResource(R.string.settings_core_label),
                ) {
                    // 灰度遮罩的首选通道：授权一次长期有效，不会被强停/覆盖安装撤销
                    PermissionRow(
                        icon = Icons.Outlined.Layers,
                        title = stringResource(R.string.perm_overlay_title),
                        subtitle = stringResource(R.string.perm_overlay_subtitle),
                        status = state.overlayStatus,
                        onClick = { onPermissionClick(PermissionTarget.Overlay) },
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.BatteryFull,
                        title = stringResource(R.string.perm_battery_title),
                        subtitle = stringResource(R.string.perm_battery_subtitle),
                        status = state.batteryStatus,
                        onClick = { onPermissionClick(PermissionTarget.Battery) },
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.SettingsRemote,
                        title = stringResource(R.string.perm_foreground_title),
                        subtitle = stringResource(R.string.perm_foreground_subtitle),
                        status = state.foregroundServiceStatus,
                        onClick = { onPermissionClick(PermissionTarget.ForegroundService) },
                    )
                }
            }
            item {
                PermissionGroup(
                    label = stringResource(R.string.settings_extra_label),
                ) {
                    PermissionRow(
                        icon = Icons.Outlined.Visibility,
                        title = stringResource(R.string.perm_accessibility_title),
                        subtitle = when {
                            state.accessibilityStatus == PermissionStatus.Granted ->
                                stringResource(R.string.perm_accessibility_subtitle)
                            // 曾被系统关闭：给出明确原因，避免误以为是 App 在反复索要权限
                            state.accessibilityWasRevoked ->
                                stringResource(R.string.perm_accessibility_subtitle_revoked)
                            else ->
                                stringResource(R.string.perm_accessibility_subtitle_off)
                        },
                        status = state.accessibilityStatus,
                        onClick = { onPermissionClick(PermissionTarget.Accessibility) },
                    )
                    // 被系统关闭时附上最近一次生命周期记录，便于确认是解绑还是崩溃
                    val revokedHint = state.accessibilityHint
                    if (state.accessibilityStatus != PermissionStatus.Granted &&
                        state.accessibilityWasRevoked &&
                        revokedHint != null
                    ) {
                        Text(
                            text = revokedHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 60.dp, bottom = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.Alarm,
                        title = stringResource(R.string.perm_exact_alarm_title),
                        subtitle = stringResource(R.string.perm_exact_alarm_subtitle),
                        status = state.exactAlarmStatus,
                        onClick = { onPermissionClick(PermissionTarget.ExactAlarm) },
                    )
                    Spacer(Modifier.height(1.dp).fillMaxWidth().background(Divider))
                    PermissionRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.perm_notification_title),
                        subtitle = stringResource(R.string.perm_notification_subtitle),
                        status = state.notificationStatus,
                        onClick = { onPermissionClick(PermissionTarget.Notification) },
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
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

// ------- 应用显示（最近任务） -------

/**
 * 「应用显示」分组：控制是否从系统最近任务列表中隐藏。
 *
 * 本应用是后台常驻型工具，留在最近任务里既没意义，又容易被顺手划掉，
 * 因此默认隐藏；但必须给一个能恢复的入口，否则用户找不到 App 会很困惑。
 */
@Composable
private fun AppDisplayGroup(
    hideFromRecents: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val activity = LocalContext.current as? Activity
    // 开关变化后立即同步任务可见性（设置页内不会触发 Activity 的 onResume）
    LaunchedEffect(hideFromRecents) {
        activity?.let { RecentsController.sync(it) }
    }

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
            TagChip(text = stringResource(R.string.settings_app_display_label))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.hide_from_recents_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        if (hideFromRecents) {
                            R.string.hide_from_recents_subtitle_on
                        } else {
                            R.string.hide_from_recents_subtitle_off
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            TealSwitch(checked = hideFromRecents, onCheckedChange = onToggle)
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
    // ---- HSV 作为唯一真源 ----
    // 不直接用 RGB 存状态的原因：灰色（饱和度=0）的色相反向计算时无定义，
    // 若每次都从 RGB 反解，用户一选纯灰、色相条就会跳回红色。
    // 这里把 hue 单独保留：当选择的颜色无彩色时，只更新饱和度与明度。
    val initial = remember(current) { rgbToHsv(current) }
    var hue by remember(current) { mutableStateOf(initial.h) }
    var sat by remember(current) { mutableStateOf(initial.s) }
    var brightness by remember(current) { mutableStateOf(initial.v) }

    val selected = hsvToRgb(hue, sat, brightness)
    var hexInput by remember(current) { mutableStateOf(toHexColor(current)) }
    var error by remember(current) { mutableStateOf(false) }

    /** 从 HSV 同步色值文本（拖滑块时调用） */
    fun syncHexFromHsv() {
        val hex = toHexColor(hsvToRgb(hue, sat, brightness))
        if (hex != hexInput) hexInput = hex
    }

    /** 从色值反解 HSV（输入框 / 点色板时调用） */
    fun applyRgb(rgb: Int) {
        val hsv = rgbToHsv(rgb)
        // 只有带彩色时才改写色相，纯灰保留当前色相，避免色相条乱跳
        if (hsv.s > 0.004f) hue = hsv.h
        sat = hsv.s
        brightness = hsv.v
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(BgCardElevated)
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            // 标题 + 当前颜色预览
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.grayscale_color_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color(0xFF000000.toInt() or selected))
                        .border(1.dp, Divider, RoundedCornerShape(9.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = toHexColor(selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BrandTeal,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(14.dp))

            // 饱和度（横）× 明度（纵）面板
            SaturationValuePanel(
                hue = hue,
                sat = sat,
                brightness = brightness,
                onChange = { s, v ->
                    sat = s
                    brightness = v
                    syncHexFromHsv()
                },
            )

            Spacer(Modifier.height(12.dp))

            // 色相条
            HueSlider(
                hue = hue,
                onChange = { h ->
                    hue = h
                    syncHexFromHsv()
                },
            )

            // ---- 预设色板 ----
            Spacer(Modifier.height(16.dp))
            PaletteLabel(stringResource(R.string.color_picker_palette_gray))
            Spacer(Modifier.height(8.dp))
            SwatchGrid(
                colors = GrayPalette,
                selected = selected,
                onPick = { rgb ->
                    applyRgb(rgb)
                    hexInput = toHexColor(rgb)
                    error = false
                },
            )

            Spacer(Modifier.height(14.dp))
            PaletteLabel(stringResource(R.string.color_picker_palette_tinted))
            Spacer(Modifier.height(8.dp))
            SwatchGrid(
                colors = TintedPalette,
                selected = selected,
                onPick = { rgb ->
                    applyRgb(rgb)
                    hexInput = toHexColor(rgb)
                    error = false
                },
            )

            Spacer(Modifier.height(14.dp))
            PaletteLabel(stringResource(R.string.color_picker_palette_hue))
            Spacer(Modifier.height(8.dp))
            SwatchGrid(
                colors = StandardPalette,
                selected = selected,
                onPick = { rgb ->
                    applyRgb(rgb)
                    hexInput = toHexColor(rgb)
                    error = false
                },
            )

            // ---- 手动输入色值 ----
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    hexInput = input
                    val parsed = parseHexColor(input)
                    if (parsed != null) {
                        applyRgb(parsed)
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

// ---------------- 取色器子组件 ----------------

/** 中性灰阶：灰度遮罩的主要用途，从近白到近黑 */
private val GrayPalette = listOf(
    0xF5F5F5, 0xD9D9D9, 0xBDBDBD, 0x9E9E9E,
    0x808080, 0x616161, 0x424242, 0x212121,
)

/** 常用偏色灰：带极轻微冷暖，视觉上仍是灰但有氛围 */
private val TintedPalette = listOf(
    0x8C8378, 0x78828C, 0x7C8C78, 0x8C7C84,
    0xB0A48C, 0x8C9AA3, 0xA3928C, 0x7A7A85,
)

/** 标准色板：八种高辨识度色相，供需要明显色调时使用 */
private val StandardPalette = listOf(
    0xE24B4A, 0xEF9F27, 0xFAC775, 0x639922,
    0x1D9E75, 0x378ADD, 0x7F77DD, 0xD4537E,
)

@Composable
private fun PaletteLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
    )
}

/**
 * 色板网格：等分一行，正方形色块。
 *
 * 之所以用 weight + aspectRatio 而不是固定尺寸：
 * 对话框宽度随屏幕变化，固定 dp 会在窄屏溢出、宽屏留白。
 */
@Composable
private fun SwatchGrid(
    colors: List<Int>,
    selected: Int,
    onPick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { rgb ->
            val pure = rgb and 0xFFFFFF
            val isSelected = pure == (selected and 0xFFFFFF)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF000000.toInt() or pure))
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) BrandTeal else Divider,
                        shape = RoundedCornerShape(9.dp),
                    )
                    .clickable { onPick(pure) },
            )
        }
    }
}

/**
 * 饱和度（横轴 0..1）× 明度（纵轴 1..0）选择面板。
 *
 * 绘制方式：先铺「白 → 纯色相」的横向渐变，再叠一层「透明 → 黑」的纵向渐变，
 * 两层的乘积正好等价于 HSV 的 S/V 平面。这是各家取色器的通用做法，
 * 比逐像素绘制便宜得多。
 */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    sat: Float,
    brightness: Float,
    onChange: (Float, Float) -> Unit,
) {
    var panel by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    fun handle(pos: Offset) {
        if (panel.width <= 0 || panel.height <= 0) return
        onChange(
            (pos.x / panel.width).coerceIn(0f, 1f),
            (1f - pos.y / panel.height).coerceIn(0f, 1f),
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .onSizeChanged { panel = it }
            .pointerInput(Unit) { detectTapGestures { handle(it) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { handle(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        handle(change.position)
                    },
                )
            },
    ) {
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.White, Color.hsv(hue, 1f, 1f)),
            )
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black),
            )
        )

        // 指示器：外圈半透明黑 + 内圈白环，深浅底色下都能看清
        val cx = sat * size.width
        val cy = (1f - brightness) * size.height
        val outer = with(density) { 10.dp.toPx() }
        val inner = with(density) { 8.dp.toPx() }
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = outer,
            center = Offset(cx, cy),
            style = Stroke(width = with(density) { 3.dp.toPx() }),
        )
        drawCircle(
            color = Color.White,
            radius = inner,
            center = Offset(cx, cy),
            style = Stroke(width = with(density) { 2.dp.toPx() }),
        )
    }
}

/**
 * 色相条：0..360 线性映射到宽度。
 *
 * 渐变端点用 0/60/120/.../360 七个色相，
 * 首尾都是红（0° 与 360° 同色），保证两端视觉连续。
 */
@Composable
private fun HueSlider(
    hue: Float,
    onChange: (Float) -> Unit,
) {
    var panel by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    fun handle(pos: Offset) {
        if (panel.width <= 0) return
        onChange((pos.x / panel.width).coerceIn(0f, 1f) * 360f)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { panel = it }
            .pointerInput(Unit) { detectTapGestures { handle(it) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { handle(it) },
                    onDrag = { change, _ ->
                        change.consume()
                        handle(change.position)
                    },
                )
            },
    ) {
        val stops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
            .map { Color.hsv(it % 360f, 1f, 1f) }
        drawRect(Brush.horizontalGradient(stops))

        val r = with(density) { 10.dp.toPx() }
        val cx = ((hue / 360f) * size.width).coerceIn(r, size.width - r)
        val cy = size.height / 2f
        // 白色圆环 + 内填当前纯色相，便于判断拖动位置
        drawCircle(
            color = Color.White,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = with(density) { 3.dp.toPx() }),
        )
        drawCircle(
            color = Color.hsv(hue, 1f, 1f),
            radius = r - with(density) { 4.dp.toPx() },
            center = Offset(cx, cy),
        )
    }
}

// ---------------- HSV / RGB 转换 ----------------

/** HSV 三元组：色相 0..360，饱和度与明度 0..1 */
private data class Hsv(val h: Float, val s: Float, val v: Float)

/**
 * RGB(0xRRGGBB) → HSV。
 *
 * 注意：灰度（delta=0）时色相在数学上无定义，此处返回 0；
 * 调用方必须自行决定是否采用（见 [applyRgb] 的处理）。
 */
private fun rgbToHsv(rgb: Int): Hsv {
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8) and 0xFF) / 255f
    val b = (rgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val h = if (delta == 0f) {
        0f
    } else {
        val raw = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        }
        (raw * 60f + 360f) % 360f
    }
    val s = if (max == 0f) 0f else delta / max
    return Hsv(h, s, max)
}

/** HSV → RGB(0xRRGGBB) */
private fun hsvToRgb(h: Float, s: Float, v: Float): Int {
    val c = v * s
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hh % 2f - 1f))
    val m = v - c
    val (r1, g1, b1) = when (hh.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun to8(f: Float): Int = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return (to8(r1) shl 16) or (to8(g1) shl 8) or to8(b1)
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

// ======================= Compose 预览 =======================
//
// 预览渲染无状态的 [SettingsContent]，权限状态通过 [SettingsUiState] 注入，
// 因此不需要真实系统权限、也不依赖 ViewModel / DataStore。

/** 权限齐备 + 服务健康：不应出现顶部红色告警条 */
private fun previewAllGrantedState() = SettingsUiState(
    batteryStatus = PermissionStatus.Granted,
    foregroundServiceStatus = PermissionStatus.Granted,
    accessibilityStatus = PermissionStatus.Granted,
    notificationStatus = PermissionStatus.Granted,
    exactAlarmStatus = PermissionStatus.Granted,
    overlayStatus = PermissionStatus.Granted,
    serviceRunning = true,
    serviceAlive = true,
    serviceUptimeMinutes = 128,
)

/** 首次进入、尚未授权任何权限 */
private fun previewFreshState() = SettingsUiState(
    batteryStatus = PermissionStatus.NotGranted,
    foregroundServiceStatus = PermissionStatus.Granted,
    accessibilityStatus = PermissionStatus.NotGranted,
    notificationStatus = PermissionStatus.NotGranted,
    exactAlarmStatus = PermissionStatus.NotGranted,
    overlayStatus = PermissionStatus.NotGranted,
    serviceRunning = false,
    serviceAlive = false,
)

/** 无障碍曾被系统撤销：强停 / 覆盖安装后的典型状态 */
private fun previewRevokedState() = SettingsUiState(
    batteryStatus = PermissionStatus.Granted,
    foregroundServiceStatus = PermissionStatus.Granted,
    accessibilityStatus = PermissionStatus.NotGranted,
    notificationStatus = PermissionStatus.Granted,
    exactAlarmStatus = PermissionStatus.Granted,
    overlayStatus = PermissionStatus.Granted,
    serviceRunning = true,
    serviceAlive = false,
    accessibilityWasRevoked = true,
    accessibilityHint = "上次连接 11-15 21:40 · 已解绑（应用被强制停止）",
)

/** 演示用配置：低浓度、非默认灰，便于看清色块与滑块位置 */
private fun previewSettings() = ControlState(
    grayscaleColor = 0x9AA3B0,
    grayscaleAlpha = 90,
)

@Preview(
    name = "设置 · 全部已授权",
    group = "设置页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=1600dp,dpi=440",
)
@Composable
private fun SettingsPreview_AllGranted() {
    AutoControlTheme {
        SettingsContent(state = previewAllGrantedState(), settings = ControlState())
    }
}

/** 权限缺失：顶部出现告警条，各权限行按钮显示「授权」 */
@Preview(
    name = "设置 · 权限缺失",
    group = "设置页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=1600dp,dpi=440",
)
@Composable
private fun SettingsPreview_PermissionsMissing() {
    AutoControlTheme {
        SettingsContent(state = previewFreshState(), settings = previewSettings())
    }
}

/**
 * 无障碍被系统撤销——这一屏最关键：
 * 副标题需解释"强制停止或覆盖安装后系统会撤掉授权"，下方追加最近一次生命周期记录。
 * 该状态真机难以复现，靠预览随时可查。
 */
@Preview(
    name = "设置 · 无障碍被撤销",
    group = "设置页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=1600dp,dpi=440",
)
@Composable
private fun SettingsPreview_AccessibilityRevoked() {
    AutoControlTheme {
        SettingsContent(state = previewRevokedState(), settings = ControlState())
    }
}

/**
 * 取色器对话框预览。
 *
 * 重点检查：
 * 1. SV 面板与色相条在深色主题下的对比度是否足够；
 * 2. 三组色板能否一行排下（8 列等分，窄屏不溢出）；
 * 3. 内容总高度是否需要滚动（heightIn(640.dp) + verticalScroll）。
 */
@Preview(
    name = "设置 · 取色器",
    group = "设置页",
    showBackground = true,
    backgroundColor = 0xFF0B1220,
    device = "spec:width=393dp,height=851dp,dpi=440",
)
@Composable
private fun GrayscaleColorDialogPreview() {
    AutoControlTheme {
        GrayscaleColorDialog(current = 0x808080, onConfirm = {}, onDismiss = {})
    }
}
