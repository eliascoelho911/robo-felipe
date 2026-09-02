package com.example.robofelipe.data

import android.content.Context
import android.content.SharedPreferences

// Persiste configuração do app (URLs do Core e xiaozhi-server, petId) em
// SharedPreferences. O Sobrinho configura uma vez e o app lembra.
class PetConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var coreUrl: String
        get() = prefs.getString(KEY_CORE_URL, DEFAULT_CORE_URL) ?: DEFAULT_CORE_URL
        set(value) = prefs.edit().putString(KEY_CORE_URL, value).apply()

    var xiaozhiUrl: String
        get() = prefs.getString(KEY_XIAOZHI_URL, DEFAULT_XIAOZHI_URL) ?: DEFAULT_XIAOZHI_URL
        set(value) = prefs.edit().putString(KEY_XIAOZHI_URL, value).apply()

    var petId: String
        get() = prefs.getString(KEY_PET_ID, DEFAULT_PET_ID) ?: DEFAULT_PET_ID
        set(value) = prefs.edit().putString(KEY_PET_ID, value).apply()

    var platformId: String
        get() = prefs.getString(KEY_PLATFORM_ID, DEFAULT_PLATFORM_ID) ?: DEFAULT_PLATFORM_ID
        set(value) = prefs.edit().putString(KEY_PLATFORM_ID, value).apply()

    companion object {
        private const val PREFS_NAME = "robo_felipe_config"
        private const val KEY_CORE_URL = "core_url"
        private const val KEY_XIAOZHI_URL = "xiaozhi_url"
        private const val KEY_PET_ID = "pet_id"
        private const val KEY_PLATFORM_ID = "platform_id"

        // Máquina dev atual da rede (192.168.1.100 é host morto) — ver
        // Project Learnings no AGENTS.md da raiz.
        const val DEFAULT_CORE_URL = "http://192.168.1.21:8090"
        const val DEFAULT_XIAOZHI_URL = "ws://192.168.1.21:8091/xiaozhi/v1/"
        const val DEFAULT_PET_ID = "felipe"
        const val DEFAULT_PLATFORM_ID = "android-robo-felipe"
    }
}
