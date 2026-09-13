package com.example.autocontrol.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import com.example.autocontrol.R

/**
 * 执行三类系统动作。普通设备（无 root）上关机/断网会失败，
 * 采用"诚实降级"：返回结果，由调用方通知用户。
 */
object ActionExecutor {

    /** 动作结果：成功 / 失败（附原因） */
    data class ActionResult(
        val success: Boolean,
        val message: String,
    )

    /**
     * 灰度开关对应的 Settings.Secure 键：原 @hide 常量 ACCESSIBILITY_DISPLAY_DALTONIZER，
     * 公开 SDK 不可见，故用其字符串字面量值替代以通过编译。
     */
    private const val KEY_ACCESSIBILITY_DISPLAY_DALTONIZER = "accessibility_display_daltonizer"

    /**
     * 尝试关机。反射调用 PowerManager.shutdown()（隐藏 API），
     * 普通应用会抛 SecurityException（缺少 android.permission.SHUTDOWN）。
     */
    fun performShutdown(context: Context): ActionResult {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val method = PowerManager::class.java.getMethod("shutdown", Boolean::class.javaPrimitiveType, String::class.java, Boolean::class.javaPrimitiveType)
            method.invoke(pm, false, null, false)
            ActionResult(true, context.getString(R.string.action_shutdown_success))
        } catch (e: Exception) {
            ActionResult(
                false,
                context.getString(R.string.action_shutdown_failed)
            )
        }
    }

    /**
     * 尝试断开网络（关闭 WiFi）。移动数据为隐藏 API 普通应用不可用。
     * Android 10+ 上 setWifiEnabled() 对 targetSdk>=10 的应用始终返回 false。
     */
    fun performDisconnect(context: Context, enable: Boolean): ActionResult {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            @Suppress("DEPRECATION")
            val ok = wifi.setWifiEnabled(enable)
            if (ok) {
                ActionResult(true, context.getString(if (enable) R.string.action_network_on_success else R.string.action_network_off_success))
            } else {
                ActionResult(false, context.getString(R.string.action_network_failed))
            }
        } catch (e: Exception) {
            ActionResult(false, context.getString(R.string.action_network_failed))
        }
    }

    /**
     * 灰度模式：通过 Settings.Secure 的 "accessibility_display_daltonizer" 键控制。
     * 值 1 = 灰度（GRAYSCALE），0 = 关闭。
     * 注意：该常量为 @hide 隐藏 API（未进公开 SDK），且写入需 WRITE_SECURE_SETTINGS 权限（普通应用无），
     * 因此此操作通常会抛 SecurityException 被 catch 降级为失败提示。
     */
    fun setGrayscale(context: Context, on: Boolean): ActionResult {
        return try {
            val value = if (on) 1 else 0
            Settings.Secure.putInt(
                context.contentResolver,
                KEY_ACCESSIBILITY_DISPLAY_DALTONIZER,
                value
            )
            ActionResult(true, context.getString(if (on) R.string.action_grayscale_on_success else R.string.action_grayscale_off_success))
        } catch (e: Exception) {
            ActionResult(false, context.getString(R.string.action_grayscale_failed))
        }
    }

    fun isGrayscaleEnabled(context: Context): Boolean =
        Settings.Secure.getInt(
            context.contentResolver,
            KEY_ACCESSIBILITY_DISPLAY_DALTONIZER,
            0
        ) == 1
}
