package com.catcontroller.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val pollIntervalMs: Int = 500,
    val showSmeterBar: Boolean = true,
    val keepScreenOn: Boolean = true,
    val darkFreqDisplay: Boolean = true,
    val autoConnect: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    private object Keys {
        val pollInterval    = intPreferencesKey("poll_interval_ms")
        val showSmeter      = booleanPreferencesKey("show_smeter")
        val keepScreenOn    = booleanPreferencesKey("keep_screen_on")
        val darkDisplay     = booleanPreferencesKey("dark_display")
        val autoConnect     = booleanPreferencesKey("auto_connect")
    }

    val settings: StateFlow<AppSettings> = ctx.dataStore.data.map { prefs ->
        AppSettings(
            pollIntervalMs  = prefs[Keys.pollInterval] ?: 500,
            showSmeterBar   = prefs[Keys.showSmeter] ?: true,
            keepScreenOn    = prefs[Keys.keepScreenOn] ?: true,
            darkFreqDisplay = prefs[Keys.darkDisplay] ?: true,
            autoConnect     = prefs[Keys.autoConnect] ?: false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setPollInterval(ms: Int) = save { it[Keys.pollInterval] = ms }
    fun setShowSmeter(v: Boolean) = save { it[Keys.showSmeter] = v }
    fun setKeepScreenOn(v: Boolean) = save { it[Keys.keepScreenOn] = v }
    fun setDarkDisplay(v: Boolean) = save { it[Keys.darkDisplay] = v }
    fun setAutoConnect(v: Boolean) = save { it[Keys.autoConnect] = v }

    private fun save(block: (MutablePreferences) -> Unit) {
        viewModelScope.launch { ctx.dataStore.edit(block) }
    }
}
