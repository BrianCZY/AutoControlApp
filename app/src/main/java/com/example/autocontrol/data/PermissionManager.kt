package com.example.autocontrol.data

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * 统一权限/系统设置检查与跳转。
 * 全部为真实状态查询，替换设置页里原来的占位数据。
 *
 * 注意：本类持有的是 ApplicationContext，所有 startActivity 必须走 [safeStartActivity]。
 */
class PermissionManager(private val context: Context) {

    private companion object {
        const val TAG = "PermissionManager"
    }

    /** 通知权限是否已授予（Android 13+ 需要运行时申请） */
    fun isNotificationGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // minSdk 26 已 >= 23，直接用 Context#checkSelfPermission，不再依赖 androidx.core
            context.checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** 精确闹钟是否已授予（Android 12+） */
    fun isExactAlarmGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }

    /** 电池优化白名单是否已加入 */
    fun isIgnoringBatteryOptimizations(): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** 本应用的辅助功能服务是否已启用 */
    fun isAccessibilityEnabled(): Boolean {
        val expected = "${context.packageName}/${com.example.autocontrol.service.GrayscaleAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    // ---- 跳转 ----

    /**
     * 本类持有的是 ApplicationContext（非 Activity），
     * 因此所有 startActivity 必须带 FLAG_ACTIVITY_NEW_TASK，
     * 否则会抛 AndroidRuntimeException。
     */
    private fun safeStartActivity(intent: Intent, fallbackToAppSettings: Boolean = true) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 某些机型没有对应的系统设置页，降级到应用详情页
            Log.w(TAG, "设置页 ${intent.action} 不存在，降级到应用详情页", e)
            if (fallbackToAppSettings) openAppSettings()
        }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra("app_package", context.packageName)
            putExtra("app_uid", context.applicationInfo.uid)
        }
        safeStartActivity(intent)
    }

    /** 精确闹钟设置页（Android 12+）。预留给定时任务授权流程使用。 */
    @Suppress("unused")
    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            safeStartActivity(intent)
        }
    }

    /** 申请加入电池优化白名单（minSdk 26 已 >= M，无需再做版本判断） */
    fun openBatteryOptimizationSettings() {
        @Suppress("BatteryLife")
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        safeStartActivity(intent)
    }

    fun openAccessibilitySettings() {
        safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openAppSettings() {
        // 自身就是降级终点，避免异常时递归回自己
        safeStartActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            },
            fallbackToAppSettings = false,
        )
    }
}
