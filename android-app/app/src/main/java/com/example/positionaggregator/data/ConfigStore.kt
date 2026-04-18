package com.example.positionaggregator.data

import android.content.Context
import com.example.positionaggregator.data.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class ConfigStore(context: Context) {
    private val configFile = File(context.filesDir, CONFIG_FILE)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun load(): AppConfig = withContext(Dispatchers.IO) {
        if (!configFile.exists()) {
            return@withContext AppConfig()
        }
        val content = configFile.readText()
        if (content.isBlank()) {
            return@withContext AppConfig()
        }
        runCatching { json.decodeFromString<AppConfig>(content) }.getOrElse { AppConfig() }
    }

    suspend fun save(config: AppConfig) = withContext(Dispatchers.IO) {
        configFile.writeText(json.encodeToString(AppConfig.serializer(), config))
    }

    companion object {
        private const val CONFIG_FILE = "positions_app_config.json"
    }
}
