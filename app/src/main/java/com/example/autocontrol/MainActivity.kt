package com.example.autocontrol

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.example.autocontrol.nav.AppNav
import com.example.autocontrol.service.AutoControlService
import com.example.autocontrol.service.NotificationHelper
import com.example.autocontrol.service.PermissionGuide
import com.example.autocontrol.ui.home.LocalAppContext
import com.example.autocontrol.ui.theme.AutoControlTheme
import com.example.autocontrol.ui.theme.BgRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        startServiceIfNeeded()
        setContent {
            AutoControlTheme {
                CompositionLocalProvider(LocalAppContext provides application) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = BgRoot,
                    ) {
                        AppNav()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户从系统设置页返回：收起悬浮在该页面上的指引卡片，避免它跟回 App 里
        PermissionGuide.hideFloating(this)
    }

    private fun startServiceIfNeeded() {
        // 启动前台服务承载定时调度（幂等，已运行则无副作用）
        val intent = Intent(this, AutoControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
