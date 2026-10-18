package com.example.autocontrol.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AutoControlDarkScheme = darkColorScheme(
    primary = BrandTeal,
    onPrimary = TextOnBrand,
    primaryContainer = BgChipGreen,
    onPrimaryContainer = BrandTeal,
    secondary = BrandTealDim,
    onSecondary = TextOnBrand,
    background = BgRoot,
    onBackground = TextPrimary,
    surface = BgCard,
    onSurface = TextPrimary,
    surfaceVariant = BgCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    error = StatusRed,
    onError = TextPrimary,
    errorContainer = BgChipRed,
    onErrorContainer = StatusRed,
)

@Composable
// statusBarColor / navigationBarColor 在 API 35 起被标记弃用（官方推荐 edge-to-edge），
// 但这里仍需在低版本上把系统栏刷成 App 背景色，故保留调用并抑制告警。
@Suppress("DEPRECATION")
fun AutoControlTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = AutoControlDarkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BgRoot.toArgb()
            window.navigationBarColor = BgRoot.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
