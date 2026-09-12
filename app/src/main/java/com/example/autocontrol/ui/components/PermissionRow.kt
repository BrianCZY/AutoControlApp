package com.example.autocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autocontrol.ui.theme.BgCard
import com.example.autocontrol.ui.theme.BgButtonDisabled
import com.example.autocontrol.ui.theme.BrandTeal
import com.example.autocontrol.ui.theme.Divider
import com.example.autocontrol.ui.theme.TextPrimary
import com.example.autocontrol.ui.theme.TextSecondary

enum class PermissionStatus { NotGranted, Granted }

@Composable
fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: PermissionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon = icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        PermissionButton(status = status, onClick = onClick)
    }
}

@Composable
fun PermissionButton(
    status: PermissionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, background, content) = when (status) {
        PermissionStatus.NotGranted -> Triple("授权", BrandTeal, Color(0xFF052E2B))
        PermissionStatus.Granted -> Triple("已授权", BgButtonDisabled, TextSecondary)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(enabled = status == PermissionStatus.NotGranted) { onClick() }
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 状态卡：粗体大标题 + 副状态 + 右侧状态按钮 + 分隔线以下几行 key-value。
 */
@Composable
fun StatusCard(
    title: String,
    status: String,
    hint: String,
    actionLabel: String,
    onAction: () -> Unit,
    meta: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            PermissionButton(
                status = PermissionStatus.NotGranted,
                onClick = onAction,
            )
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.size(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(1.dp)
                    .background(Divider)
            )
            Spacer(Modifier.size(12.dp))
            meta.forEachIndexed { index, (k, v) ->
                KeyValueRow(key = k, value = v)
                if (index != meta.lastIndex) {
                    Spacer(Modifier.size(8.dp))
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
        )
    }
}
