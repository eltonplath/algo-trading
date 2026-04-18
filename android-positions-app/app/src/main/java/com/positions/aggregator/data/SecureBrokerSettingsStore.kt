package com.positions.aggregator.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureBrokerSettingsStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getKrakenKey(): String = prefs.getString(KEY_KRAKEN_API_KEY, "") ?: ""
    fun getKrakenSecret(): String = prefs.getString(KEY_KRAKEN_API_SECRET, "") ?: ""

    fun setKraken(key: String, secret: String) {
        prefs.edit()
            .putString(KEY_KRAKEN_API_KEY, key.trim())
            .putString(KEY_KRAKEN_API_SECRET, secret.trim())
            .apply()
    }

    fun getIbBaseUrl(): String =
        (prefs.getString(KEY_IB_BASE_URL, DEFAULT_IB_BASE) ?: DEFAULT_IB_BASE).trimEnd('/')

    fun setIbBaseUrl(url: String) {
        prefs.edit().putString(KEY_IB_BASE_URL, url.trim().trimEnd('/')).apply()
    }

    fun getXtbUserId(): String = prefs.getString(KEY_XTB_USER, "") ?: ""
    fun getXtbPassword(): String = prefs.getString(KEY_XTB_PASSWORD, "") ?: ""
    fun isXtbDemo(): Boolean = prefs.getBoolean(KEY_XTB_DEMO, false)

    fun setXtb(userId: String, password: String, demo: Boolean) {
        prefs.edit()
            .putString(KEY_XTB_USER, userId.trim())
            .putString(KEY_XTB_PASSWORD, password.trim())
            .putBoolean(KEY_XTB_DEMO, demo)
            .apply()
    }

    fun getEtoroBearerToken(): String = prefs.getString(KEY_ETORO_TOKEN, "") ?: ""
    fun isEtoroDemo(): Boolean = prefs.getBoolean(KEY_ETORO_DEMO, false)

    fun setEtoroBearerToken(token: String, demo: Boolean) {
        prefs.edit()
            .putString(KEY_ETORO_TOKEN, token.trim())
            .putBoolean(KEY_ETORO_DEMO, demo)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "broker_settings_enc"
        private const val KEY_KRAKEN_API_KEY = "kraken_api_key"
        private const val KEY_KRAKEN_API_SECRET = "kraken_api_secret"
        private const val KEY_IB_BASE_URL = "ib_base_url"
        private const val KEY_XTB_USER = "xtb_user"
        private const val KEY_XTB_PASSWORD = "xtb_password"
        private const val KEY_XTB_DEMO = "xtb_demo"
        private const val KEY_ETORO_TOKEN = "etoro_bearer"
        private const val KEY_ETORO_DEMO = "etoro_demo"
        const val DEFAULT_IB_BASE = "https://localhost:5000/v1/api"
    }
}
