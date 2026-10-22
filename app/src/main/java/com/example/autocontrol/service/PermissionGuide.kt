package com.example.autocontrol.service

import android.content.Context
import com.example.autocontrol.R

/**
 * 需要引导用户去系统设置授权的权限项。
 * 所有权限入口统一收敛到「设置」页，由设置页按此枚举给出对应的浮窗指引。
 */
enum class PermissionTarget {
    Overlay,
    Battery,
    ForegroundService,
    Accessibility,
    Notification,
    ExactAlarm,
}

/**
 * 权限指引文案 + 系统级浮窗指引入口。
 *
 * 两段式引导：
 * 1. 应用内先弹“浮窗式指引卡”（见 SettingsScreen），说明需要在系统设置里点什么；
 * 2. 跳到系统设置页后，把同一张指引卡片悬浮在系统页面之上，用户不必记住步骤来回切换。
 *
 * 浮窗有两条通道，按可用性自动选择：
 * - 无障碍服务已开启 → TYPE_ACCESSIBILITY_OVERLAY（顺带可穿透点击）；
 * - 否则 → 普通悬浮窗 TYPE_APPLICATION_OVERLAY（需「显示在其他应用上层」权限）。
 *   这条通道是为了覆盖“首次开启无障碍”这个场景：那时无障碍服务还没运行，只能靠普通浮窗。
 */
object PermissionGuide {

    fun title(context: Context, target: PermissionTarget): String = context.getString(
        when (target) {
            PermissionTarget.Overlay -> R.string.perm_guide_overlay_title
            PermissionTarget.Battery -> R.string.perm_guide_battery_title
            PermissionTarget.ForegroundService -> R.string.perm_guide_foreground_title
            PermissionTarget.Accessibility -> R.string.perm_guide_accessibility_title
            PermissionTarget.Notification -> R.string.perm_guide_notification_title
            PermissionTarget.ExactAlarm -> R.string.perm_guide_exact_alarm_title
        }
    )

    fun steps(context: Context, target: PermissionTarget): String = context.getString(
        when (target) {
            PermissionTarget.Overlay -> R.string.perm_guide_overlay_steps
            PermissionTarget.Battery -> R.string.perm_guide_battery_steps
            PermissionTarget.ForegroundService -> R.string.perm_guide_foreground_steps
            PermissionTarget.Accessibility -> R.string.perm_guide_accessibility_steps
            PermissionTarget.Notification -> R.string.perm_guide_notification_steps
            PermissionTarget.ExactAlarm -> R.string.perm_guide_exact_alarm_steps
        }
    )

    /**
     * 在系统设置页上方显示浮窗指引（无障碍通道优先，普通悬浮窗兜底）。
     *
     * @return true 表示浮窗已经挂上；false 表示两条通道都不可用（既没开无障碍、也没给悬浮窗权限），
     *         此时调用方应提示用户先开启「悬浮窗」，以便后续授权时能看到指引。
     */
    fun showFloating(context: Context, target: PermissionTarget): Boolean {
        val resolvedTitle = title(context, target)
        val resolvedSteps = steps(context, target)
        val dismissText = context.getString(R.string.perm_guide_dismiss)

        // 通道一：无障碍浮窗（可信窗口，能浮在系统设置页之上）
        if (GrayscaleAccessibilityService.isEnabled(context)) {
            GrayscaleAccessibilityService.showGuide(
                context = context,
                title = resolvedTitle,
                steps = resolvedSteps,
                dismissText = dismissText,
            )
            return true
        }

        // 通道二：普通悬浮窗（首次开启无障碍时走这里）
        if (GuideOverlay.canShow(context)) {
            return GuideOverlay.show(context, resolvedTitle, resolvedSteps, dismissText)
        }

        return false
    }

    /** 移除系统级浮窗指引（用户回到本应用后调用） */
    fun hideFloating(context: Context) {
        GrayscaleAccessibilityService.hideGuide(context)
        GuideOverlay.hide(context)
    }
}
