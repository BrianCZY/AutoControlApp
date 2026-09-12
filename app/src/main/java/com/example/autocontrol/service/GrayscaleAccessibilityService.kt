package com.example.autocontrol.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 灰度模式依赖的辅助功能服务。
 * 灰度本身通过 [ActionExecutor.setGrayscale] 写 Settings.Secure 实现，
 * 但 Android 要求必须有一个已启用的辅助功能服务，颜色校正(Daltonizer)才会被应用。
 * 此服务保持空实现即可满足"已启用辅助功能"这一前置条件。
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 无需处理具体事件
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}
