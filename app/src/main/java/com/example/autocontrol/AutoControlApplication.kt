package com.example.autocontrol

import android.app.Application
import com.example.autocontrol.data.PermissionManager
import com.example.autocontrol.data.SettingsRepository
import com.example.autocontrol.service.ServiceKeeper

class AutoControlApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var permissionManager: PermissionManager
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        permissionManager = PermissionManager(this)

        // 进程被创建时把保活心跳续上（仅在用户未主动停用的情况下）。
        // 这里不直接启服务：Application 可能是在后台被系统拉起的，
        // Android 12+ 此时禁止直启前台服务；只排心跳，由闹钟回调负责启动。
        if (ServiceKeeper.shouldRun(this)) {
            ServiceKeeper.scheduleWatchdog(this)
        }
    }
}
