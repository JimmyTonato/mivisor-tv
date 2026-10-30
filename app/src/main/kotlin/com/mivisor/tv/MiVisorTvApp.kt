package com.mivisor.tv

import android.app.Application

class MiVisorTvApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
