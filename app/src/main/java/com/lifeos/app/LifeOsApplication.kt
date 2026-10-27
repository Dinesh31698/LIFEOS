package com.lifeos.app

import android.app.Application
import com.lifeos.app.data.LifeOsDatabase
import com.lifeos.app.engine.BatteryBudgetManager
import com.lifeos.app.engine.SafeModeManager
import com.lifeos.app.security.PermissionFirewall
import com.lifeos.app.service.TtlCleanupWorker

class LifeOsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LifeOsDatabase.getDatabase(this)
        SafeModeManager.init(this)
        PermissionFirewall.init(this)
        TtlCleanupWorker.schedule(this)
        BatteryBudgetManager.initialize(this)
        com.lifeos.app.data.LifeMapRepository.initialize(this)
        org.osmdroid.config.Configuration.getInstance().userAgentValue = "LIFEOS/1.0 (Android; Dinesh LIFEOS App)"
    }
}