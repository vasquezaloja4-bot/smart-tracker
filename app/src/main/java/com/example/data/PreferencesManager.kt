package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "smarttracker_prefs")

class PreferencesManager(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromRaw(prefs[themeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }
}
