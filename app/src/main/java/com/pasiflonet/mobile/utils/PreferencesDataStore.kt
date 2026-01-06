package com.pasiflonet.mobile.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object AppDataStore {
    val LOGO_ENABLED = booleanPreferencesKey("logo_enabled")
    val LOGO_URI = stringPreferencesKey("logo_uri")
    val POS_PRESET = stringPreferencesKey("pos_preset")
    val POS_X = floatPreferencesKey("pos_x")
    val POS_Y = floatPreferencesKey("pos_y")
    val SCALE = floatPreferencesKey("scale")
    val OPACITY = floatPreferencesKey("opacity")

    fun getWatermarkSettings(context: Context): Flow<WatermarkSettings> {
        return context.dataStore.data.map { prefs ->
            WatermarkSettings(
                enabled = prefs[LOGO_ENABLED] ?: false,
                uri = prefs[LOGO_URI],
                preset = prefs[POS_PRESET] ?: "bottom_right",
                x = prefs[POS_X] ?: 0.9f,
                y = prefs[POS_Y] ?: 0.9f,
                scale = prefs[SCALE] ?: 0.2f,
                opacity = prefs[OPACITY] ?: 0.8f
            )
        }
    }

    suspend fun saveWatermarkSettings(context: Context, settings: WatermarkSettings) {
        context.dataStore.edit { prefs ->
            prefs[LOGO_ENABLED] = settings.enabled
            if (settings.uri != null) prefs[LOGO_URI] = settings.uri
            prefs[POS_PRESET] = settings.preset
            prefs[POS_X] = settings.x
            prefs[POS_Y] = settings.y
            prefs[SCALE] = settings.scale
            prefs[OPACITY] = settings.opacity
        }
    }
}
