package com.simpletv.app

import android.app.Application
import com.simpletv.app.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
