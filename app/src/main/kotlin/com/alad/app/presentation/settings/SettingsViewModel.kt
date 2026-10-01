package com.alad.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alad.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: UserPreferencesRepository) : ViewModel() {
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()
    
    private val _volumeRatio = MutableStateFlow(1.0f)
    val volumeRatio: StateFlow<Float> = _volumeRatio.asStateFlow()

    // Proxy and Network
    private val _customWsUrl = MutableStateFlow("")
    val customWsUrl: StateFlow<String> = _customWsUrl.asStateFlow()

    private val _proxyEnabled = MutableStateFlow(false)
    val proxyEnabled: StateFlow<Boolean> = _proxyEnabled.asStateFlow()

    private val _proxyHost = MutableStateFlow("")
    val proxyHost: StateFlow<String> = _proxyHost.asStateFlow()

    private val _proxyPort = MutableStateFlow(10808)
    val proxyPort: StateFlow<Int> = _proxyPort.asStateFlow()

    private val _proxyType = MutableStateFlow("SOCKS5")
    val proxyType: StateFlow<String> = _proxyType.asStateFlow()

    // AI & Filtering
    private val _filterNativeSpeech = MutableStateFlow(true)
    val filterNativeSpeech: StateFlow<Boolean> = _filterNativeSpeech.asStateFlow()

    private val _vadEnabled = MutableStateFlow(true)
    val vadEnabled: StateFlow<Boolean> = _vadEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            _apiKey.value = repository.apiKeyFlow.first()
            _volumeRatio.value = repository.volumeRatioFlow.first()
            _customWsUrl.value = repository.customWsUrlFlow.first()
            _proxyEnabled.value = repository.proxyEnabledFlow.first()
            _proxyHost.value = repository.proxyHostFlow.first()
            _proxyPort.value = repository.proxyPortFlow.first()
            _proxyType.value = repository.proxyTypeFlow.first()
            _filterNativeSpeech.value = repository.filterNativeSpeechFlow.first()
            _vadEnabled.value = repository.vadEnabledFlow.first()
        }
    }

    fun updateApiKey(key: String) { _apiKey.value = key }
    fun updateVolumeRatio(ratio: Float) { _volumeRatio.value = ratio }
    fun updateCustomWsUrl(url: String) { _customWsUrl.value = url }
    fun updateProxyEnabled(enabled: Boolean) { _proxyEnabled.value = enabled }
    fun updateProxyHost(host: String) { _proxyHost.value = host }
    fun updateProxyPort(port: Int) { _proxyPort.value = port }
    fun updateProxyType(type: String) { _proxyType.value = type }
    fun updateFilterNativeSpeech(filter: Boolean) { _filterNativeSpeech.value = filter }
    fun updateVadEnabled(enabled: Boolean) { _vadEnabled.value = enabled }

    fun saveSettings() {
        viewModelScope.launch {
            repository.updateApiKey(_apiKey.value)
            repository.updateVolumeRatio(_volumeRatio.value)
            repository.updateCustomWsUrl(_customWsUrl.value)
            repository.updateProxyEnabled(_proxyEnabled.value)
            repository.updateProxyHost(_proxyHost.value)
            repository.updateProxyPort(_proxyPort.value)
            repository.updateProxyType(_proxyType.value)
            repository.updateFilterNativeSpeech(_filterNativeSpeech.value)
            repository.updateVadEnabled(_vadEnabled.value)
        }
    }
}

class SettingsViewModelFactory(private val repository: UserPreferencesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
