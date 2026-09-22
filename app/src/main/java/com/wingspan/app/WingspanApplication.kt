package com.wingspan.app

import android.app.Application
import android.content.Context
import org.maplibre.android.MapLibre

class WingspanApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        container = AppContainer(this)
    }
}

fun Context.appContainer(): AppContainer = (applicationContext as WingspanApplication).container
