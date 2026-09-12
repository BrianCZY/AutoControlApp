package com.example.autocontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autocontrol.ui.theme.BgCard
import com.example.autocontrol.ui.theme.Divider
import com.example.autocontrol.ui.theme.TextMuted
import com.example.autocontrol.ui.theme.TextPrimary
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * "灰度时间段" 那一行：左侧 label "灰度时间段" + 副文本 "22:00 ~ 07:00 屏幕变灰"，
 * 下方两个可点的时间胶囊（22:00 / 07:00）+ 中间 "至" 字。点击胶囊会弹起时间选择器。
 */
@Composable
fun TimeRangePickerField(
    start: LocalTime,
    end: LocalTime,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    label: String = "灰度时间段",
    middleText: String = "至",
    summaryHint: String = "屏幕变灰",
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${start.format(TimeFormatter)} ~ ${end.format(TimeFormatter)} $summaryHint",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimePill(time = start, onClick = onPickStart, modifier = Modifier.weight(1f))
            Text(
                text = middleText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
            TimePill(time = end, onClick = onPickEnd, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TimePill(
    time: LocalTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .border(1.dp, Divider, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = time.format(TimeFormatter),
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
