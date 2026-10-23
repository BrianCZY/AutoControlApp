package com.example.autocontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.example.autocontrol.nav.AppNav
import com.example.autocontrol.service.NotificationHelper
import com.example.autocontrol.service.PermissionGuide
import com.example.autocontrol.service.ServiceKeeper
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
        // 幂等：启动前台服务 + 续上保活心跳。
        // 用户若在设置页主动停用过，这里不会擅自拉起。
        ServiceKeeper.ensureRunning(this)
    }
}
