package com.example.autocontrol.service

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 「权限指引」浮窗卡片的统一外观。
 *
 * 无障碍通道（[GrayscaleAccessibilityService]）与普通悬浮窗通道（[GuideOverlay]）
 * 共用同一套卡片构建逻辑，保证两种情况下用户看到的指引完全一致。
 */
internal object GuideCard {

    /**
     * 构建指引卡片。
     *
     * @param onDismiss 点击“知道了”时的回调
     */
    fun create(
        context: Context,
        title: String,
        steps: String,
        dismissText: String,
        onDismiss: () -> Unit,
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#F5152035"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }

            addView(TextView(context).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })

            if (steps.isNotBlank()) {
                addView(TextView(context).apply {
                    text = steps
                    setTextColor(Color.parseColor("#B9C2D0"))
                    textSize = 13f
                    setLineSpacing(dp(4).toFloat(), 1f)
                    setPadding(0, dp(8), 0, 0)
                })
            }

            // “知道了”：整行右对齐，点击区域做大一点，方便在系统页面上操作
            addView(TextView(context).apply {
                text = dismissText.ifBlank { "知道了" }
                setTextColor(Color.parseColor("#2DD4BF"))
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                setPadding(dp(12), dp(12), dp(12), dp(8))
                setOnClickListener { onDismiss() }
            })
        }
    }
}
