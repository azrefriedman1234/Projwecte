package com.pasiflonet.mobile

import android.app.Application
import com.pasiflonet.mobile.td.TdLibManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        TdLibManager.init(this)
    }
}
