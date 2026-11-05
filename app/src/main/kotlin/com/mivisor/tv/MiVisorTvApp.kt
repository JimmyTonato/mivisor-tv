package com.mivisor.tv

import android.app.Application

class MiVisorTvApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        com.mivisor.tv.data.PerfLog.start()
    }
}
