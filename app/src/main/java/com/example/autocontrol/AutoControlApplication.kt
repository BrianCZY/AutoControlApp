package com.example.autocontrol

import android.app.Application
import com.example.autocontrol.data.PermissionManager
import com.example.autocontrol.data.SettingsRepository

class AutoControlApplication : Application() {

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var permissionManager: PermissionManager
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        permissionManager = PermissionManager(this)
    }
}
