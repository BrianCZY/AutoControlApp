package com.example.autocontrol.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.autocontrol.ui.theme.BrandTeal
import com.example.autocontrol.ui.theme.SwitchThumbOff
import com.example.autocontrol.ui.theme.SwitchThumbOn
import com.example.autocontrol.ui.theme.SwitchTrackOff

/**
 * 自绘 Switch：与 Material3 默认外观不同，匹配原型的薄荷绿/灰色样式。
 * 轨道宽 52dp、高 30dp；thumb 26dp，左右各留 2dp 边距。
 */
@Composable
fun TealSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) BrandTeal else SwitchTrackOff,
        label = "trackColor"
    )
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) SwitchThumbOn else SwitchThumbOff)
        )
    }
}
