package com.pasiflonet.mobile.utils

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreRepo(private val context: Context) {
    companion object {
        val LOGO_URI = stringPreferencesKey("logo_uri")
        val LOGO_ENABLED = booleanPreferencesKey("logo_enabled")
        val SCALE = floatPreferencesKey("scale")
    }

    val logoUri: Flow<String?> = context.dataStore.data.map { it[LOGO_URI] }
    val logoEnabled: Flow<Boolean> = context.dataStore.data.map { it[LOGO_ENABLED] ?: false }

    suspend fun saveLogo(uri: String) {
        context.dataStore.edit { it[LOGO_URI] = uri }
    }
}
