package com.dutu007.favorapp

import android.app.Application
import com.dutu007.favorapp.data.SupabaseClientProvider

class FavorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SupabaseClientProvider.initialize()
    }
}
