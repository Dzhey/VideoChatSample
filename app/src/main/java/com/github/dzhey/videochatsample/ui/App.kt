package com.github.dzhey.videochatsample.ui

import android.app.Application
import com.github.dzhey.videochatsample.di.AppModule
import com.github.dzhey.videochatsample.di.ApplicationComponent
import com.github.dzhey.videochatsample.di.DaggerApplicationComponent

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        _instance = this
    }

    companion object {
        val component: ApplicationComponent by lazy {
            DaggerApplicationComponent.builder()
                .appModule(AppModule(_instance))
                .build()
        }

        private lateinit var _instance: App
    }
}