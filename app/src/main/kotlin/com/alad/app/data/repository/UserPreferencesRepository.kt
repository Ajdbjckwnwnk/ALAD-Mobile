package com.alad.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "alad_settings")

enum class CaptureMode {
    MIC,        // Ambient microphone (TV, classroom, room audio)
    SYSTEM      // Internal media audio (YouTube, Netflix, games)
}

class UserPreferencesRepository(private val context: Context) {

    companion object {
        val WS_URL = stringPreferencesKey("ws_url")
        val API_KEY = stringPreferencesKey("api_key")
        val SOURCE_LANG = stringPreferencesKey("source_lang")
        val TARGET_LANG = stringPreferencesKey("target_lang")
        val VOLUME_RATIO = floatPreferencesKey("volume_ratio")
        
        // New advanced features
        val CAPTURE_MODE = stringPreferencesKey("capture_mode")
        val CUSTOM_WS_URL = stringPreferencesKey("custom_ws_url")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val PROXY_TYPE = stringPreferencesKey("proxy_type") // "SOCKS5" or "HTTP"
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val FILTER_NATIVE_SPEECH = booleanPreferencesKey("filter_native_speech")
        val VAD_ENABLED = booleanPreferencesKey("vad_enabled")
        val NOISE_GATE_THRESHOLD = floatPreferencesKey("noise_gate_threshold")
    }

    val wsUrlFlow: Flow<String> = context.dataStore.data.map { it[WS_URL] ?: "" }
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val sourceLangFlow: Flow<String> = context.dataStore.data.map { it[SOURCE_LANG] ?: "en" }
    val targetLangFlow: Flow<String> = context.dataStore.data.map { it[TARGET_LANG] ?: "fa" }
    val volumeRatioFlow: Flow<Float> = context.dataStore.data.map { it[VOLUME_RATIO] ?: 1.0f }

    val captureModeFlow: Flow<CaptureMode> = context.dataStore.data.map {
        val modeStr = it[CAPTURE_MODE] ?: CaptureMode.MIC.name
        try {
            CaptureMode.valueOf(modeStr)
        } catch (e: Exception) {
            CaptureMode.MIC
        }
    }

    val customWsUrlFlow: Flow<String> = context.dataStore.data.map { it[CUSTOM_WS_URL] ?: "" }
    val proxyHostFlow: Flow<String> = context.dataStore.data.map { it[PROXY_HOST] ?: "" }
    val proxyPortFlow: Flow<Int> = context.dataStore.data.map { it[PROXY_PORT] ?: 10808 }
    val proxyTypeFlow: Flow<String> = context.dataStore.data.map { it[PROXY_TYPE] ?: "SOCKS5" }
    val proxyEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[PROXY_ENABLED] ?: false }
    val filterNativeSpeechFlow: Flow<Boolean> = context.dataStore.data.map { it[FILTER_NATIVE_SPEECH] ?: true }
    val vadEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[VAD_ENABLED] ?: true }
    val noiseGateThresholdFlow: Flow<Float> = context.dataStore.data.map { it[NOISE_GATE_THRESHOLD] ?: 300f }

    suspend fun updateWsUrl(url: String) { context.dataStore.edit { it[WS_URL] = url } }
    suspend fun updateApiKey(key: String) { context.dataStore.edit { it[API_KEY] = key } }
    suspend fun updateSourceLang(lang: String) { context.dataStore.edit { it[SOURCE_LANG] = lang } }
    suspend fun updateTargetLang(lang: String) { context.dataStore.edit { it[TARGET_LANG] = lang } }
    suspend fun updateVolumeRatio(ratio: Float) { context.dataStore.edit { it[VOLUME_RATIO] = ratio } }
    
    suspend fun updateCaptureMode(mode: CaptureMode) { context.dataStore.edit { it[CAPTURE_MODE] = mode.name } }
    suspend fun updateCustomWsUrl(url: String) { context.dataStore.edit { it[CUSTOM_WS_URL] = url } }
    suspend fun updateProxyHost(host: String) { context.dataStore.edit { it[PROXY_HOST] = host } }
    suspend fun updateProxyPort(port: Int) { context.dataStore.edit { it[PROXY_PORT] = port } }
    suspend fun updateProxyType(type: String) { context.dataStore.edit { it[PROXY_TYPE] = type } }
    suspend fun updateProxyEnabled(enabled: Boolean) { context.dataStore.edit { it[PROXY_ENABLED] = enabled } }
    suspend fun updateFilterNativeSpeech(filter: Boolean) { context.dataStore.edit { it[FILTER_NATIVE_SPEECH] = filter } }
    suspend fun updateVadEnabled(enabled: Boolean) { context.dataStore.edit { it[VAD_ENABLED] = enabled } }
    suspend fun updateNoiseGateThreshold(threshold: Float) { context.dataStore.edit { it[NOISE_GATE_THRESHOLD] = threshold } }
}
