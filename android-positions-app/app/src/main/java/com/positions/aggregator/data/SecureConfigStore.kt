package com.positions.aggregator.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.positions.aggregator.data.model.AppConfig
import com.positions.aggregator.data.model.BrokerCredentials
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Persists [AppConfig] in encrypted prefs. Migrates from:
 * - legacy plain JSON (`positions_app_config.json` from merged `android-app`), and
 * - older per-field encrypted prefs ([SecureBrokerSettingsStore] keys).
 */
class SecureConfigStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    suspend fun load(): AppConfig = withContext(Dispatchers.IO) {
        migratePlainFileIfNeeded()
        migrateLegacyEncryptedFieldsIfNeeded()
        val raw = prefs.getString(KEY_CONFIG_JSON, null)
        if (raw.isNullOrBlank()) return@withContext AppConfig()
        runCatching { json.decodeFromString(AppConfig.serializer(), raw) }.getOrElse { AppConfig() }
    }

    suspend fun save(config: AppConfig) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_CONFIG_JSON, json.encodeToString(AppConfig.serializer(), config)).apply()
    }

    private fun migratePlainFileIfNeeded() {
        if (prefs.contains(KEY_CONFIG_JSON)) return
        val legacy = File(appContext.filesDir, LEGACY_PLAIN_FILE)
        if (!legacy.exists()) return
        val text = runCatching { legacy.readText() }.getOrNull() ?: return
        if (text.isBlank()) {
            legacy.delete()
            return
        }
        val cfg = runCatching { json.decodeFromString(AppConfig.serializer(), text) }.getOrNull() ?: return
        prefs.edit().putString(KEY_CONFIG_JSON, json.encodeToString(AppConfig.serializer(), cfg)).apply()
        legacy.delete()
    }

    private fun migrateLegacyEncryptedFieldsIfNeeded() {
        if (prefs.contains(KEY_CONFIG_JSON)) return
        val old = SecureBrokerSettingsStore(appContext)
        val hasAny = old.getKrakenKey().isNotBlank() ||
            old.getIbBaseUrl() != SecureBrokerSettingsStore.DEFAULT_IB_BASE ||
            old.getXtbUserId().isNotBlank() ||
            old.getEtoroBearerToken().isNotBlank()
        if (!hasAny) return

        val creds = BrokerCredentials(
            ibBaseUrl = old.getIbBaseUrl(),
            xtbWsUrl = if (old.isXtbDemo()) "wss://ws.xtb.com/demo" else "wss://ws.xtb.com/real",
            xtbLogin = old.getXtbUserId(),
            xtbPassword = old.getXtbPassword(),
            krakenApiKey = old.getKrakenKey(),
            krakenApiSecret = old.getKrakenSecret(),
            etoroAuthMode = BrokerCredentials.ETORO_AUTH_BEARER,
            etoroBearerToken = old.getEtoroBearerToken(),
            etoroBearerDemo = old.isEtoroDemo(),
            etoroApiBaseUrl = "https://public-api.etoro.com"
        )
        prefs.edit()
            .putString(KEY_CONFIG_JSON, json.encodeToString(AppConfig.serializer(), AppConfig(creds)))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "positions_secure_config"
        private const val KEY_CONFIG_JSON = "app_config_json"
        private const val LEGACY_PLAIN_FILE = "positions_app_config.json"
    }
}
