package com.example.autocontrol.ui.home

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 在 Composition 树根部提供 Application 引用（由 [com.example.autocontrol.MainActivity] 注入）。
 * HomeScreen / SettingsScreen 通过 [LocalAppContext.current] 拿到它来构造 ViewModel。
 */
val LocalAppContext = staticCompositionLocalOf<Application> {
    error("LocalAppContext not provided")
}
