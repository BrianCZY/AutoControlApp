package com.example.autocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.autocontrol.ui.theme.BgChipGreen
import com.example.autocontrol.ui.theme.BgChipRed
import com.example.autocontrol.ui.theme.BrandTeal
import com.example.autocontrol.ui.theme.StatusRed
import com.example.autocontrol.ui.theme.TextPrimary
import com.example.autocontrol.ui.theme.TextSecondary

@Composable
fun InfoBanner(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    tone: InfoBannerTone = InfoBannerTone.Danger,
) {
    val container = if (tone == InfoBannerTone.Danger) BgChipRed else BgChipGreen
    val accent = if (tone == InfoBannerTone.Danger) StatusRed else BrandTeal
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

enum class InfoBannerTone { Danger, Success }

/**
 * 占位圆形头像，eg. 权限条左侧的图标容器
 */
@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(BgChipGreen),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}
