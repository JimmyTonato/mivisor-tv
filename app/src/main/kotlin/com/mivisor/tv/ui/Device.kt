package com.mivisor.tv.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.mivisor.tv.model.DeviceClass
import com.mivisor.tv.ui.design.UiForm

/**
 * 3f · Detección del aparato (tele, tablet o móvil) con lo que Android sabe de verdad: el modo de interfaz, Leanback y
 * si hay pantalla táctil. La regla está en [DeviceClass.detect] (con pruebas).
 */
object Devices {
    fun detect(context: Context, override: String): DeviceClass {
        val pm = context.packageManager
        val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val tvMode = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature("android.software.leanback_only")
        val touch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return DeviceClass.detect(tvMode, leanback, touch, context.resources.configuration.smallestScreenWidthDp, override)
    }

    /**
     * Orientación: la tele como venga; tablet y móvil en horizontal (como se ve un partido). Las pantallas están
     * pensadas en apaisado; la versión vertical para el móvil queda pendiente.
     */
    fun applyOrientation(activity: Activity?, device: DeviceClass) {
        activity ?: return
        val want = if (device == DeviceClass.TV) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (activity.requestedOrientation != want) activity.requestedOrientation = want
    }
}

fun DeviceClass.ui(): UiForm = when (this) {
    DeviceClass.TV -> UiForm.TV
    DeviceClass.TABLET -> UiForm.TABLET
    DeviceClass.PHONE -> UiForm.PHONE
}
