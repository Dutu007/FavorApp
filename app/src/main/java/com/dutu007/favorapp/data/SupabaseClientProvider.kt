package com.dutu007.favorapp.data

import com.dutu007.favorapp.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.createSupabaseClient

object SupabaseClientProvider {
    lateinit var client: io.github.jan.supabase.SupabaseClient
        private set

    fun initialize() {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "Missing supabase.url in local.properties"
        }
        require(BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()) {
            "Missing supabase.publishableKey in local.properties"
        }

        client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
}
