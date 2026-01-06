package com.pasiflonet.mobile.utils

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreRepo(private val context: Context) {
    companion object {
        val API_ID = intPreferencesKey("api_id")
        val API_HASH = stringPreferencesKey("api_hash")
        val LOGO_URI = stringPreferencesKey("logo_uri")
        val TARGET_USERNAME = stringPreferencesKey("target_username")
    }

    val apiId: Flow<Int?> = context.dataStore.data.map { it[API_ID] }
    val apiHash: Flow<String?> = context.dataStore.data.map { it[API_HASH] }
    val logoUri: Flow<String?> = context.dataStore.data.map { it[LOGO_URI] }
    val targetUsername: Flow<String?> = context.dataStore.data.map { it[TARGET_USERNAME] }

    suspend fun saveApi(id: Int, hash: String) {
        context.dataStore.edit { it[API_ID] = id; it[API_HASH] = hash }
    }
    suspend fun saveLogo(uri: String) {
        context.dataStore.edit { it[LOGO_URI] = uri }
    }
    suspend fun saveTargetUsername(username: String) {
        context.dataStore.edit { it[TARGET_USERNAME] = username }
    }
}
