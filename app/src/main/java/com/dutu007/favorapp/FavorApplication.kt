package com.dutu007.favorapp

import android.app.Application

class FavorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: FavorApplication
            private set
    }
}
