package com.pasiflonet.mobile.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

class DataStoreRepo(private val context: Context) {

    companion object {
        val API_ID_KEY = intPreferencesKey("api_id")
        val API_HASH_KEY = stringPreferencesKey("api_hash")
        val TARGET_USERNAME_KEY = stringPreferencesKey("target_username")
        val LOGO_URI_KEY = stringPreferencesKey("logo_uri") // המפתח החסר
    }

    // קריאת נתונים
    val apiId: Flow<Int?> = context.dataStore.data.map { it[API_ID_KEY] }
    val apiHash: Flow<String?> = context.dataStore.data.map { it[API_HASH_KEY] }
    val targetUsername: Flow<String?> = context.dataStore.data.map { it[TARGET_USERNAME_KEY] }
    val logoUri: Flow<String?> = context.dataStore.data.map { it[LOGO_URI_KEY] } // זרימת נתונים ללוגו

    // שמירת נתונים
    suspend fun saveApi(id: Int, hash: String) {
        context.dataStore.edit { prefs ->
            prefs[API_ID_KEY] = id
            prefs[API_HASH_KEY] = hash
        }
    }

    suspend fun saveTargetUsername(username: String) {
        context.dataStore.edit { prefs ->
            prefs[TARGET_USERNAME_KEY] = username
        }
    }

    // הפונקציה שהייתה חסרה!
    suspend fun saveLogoUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[LOGO_URI_KEY] = uri
        }
    }
}
