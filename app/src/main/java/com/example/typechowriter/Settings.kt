package com.example.typechowriter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

object Settings {
    private val BASE_URL = stringPreferencesKey("base_url")
    private val TOKEN = stringPreferencesKey("token")

    fun baseUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[BASE_URL] ?: "" }

    fun token(context: Context): Flow<String> =
        context.dataStore.data.map { it[TOKEN] ?: "" }

    suspend fun save(context: Context, baseUrl: String, token: String) {
        context.dataStore.edit {
            it[BASE_URL] = baseUrl
            it[TOKEN] = token
        }
    }
}