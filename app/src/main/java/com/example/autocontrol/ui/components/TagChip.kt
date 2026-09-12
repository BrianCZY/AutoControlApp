package com.example.autocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autocontrol.ui.theme.BgChipGreen
import com.example.autocontrol.ui.theme.BrandTeal

/**
 * 通用胶囊标签（绿色 chip），用于「核心权限（必须）」「附加权限（功能增强）」这类标签。
 */
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = BgChipGreen,
    contentColor: Color = BrandTeal,
) {
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Medium,
        )
    }
}
