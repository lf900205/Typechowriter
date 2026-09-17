package com.example.typechowriter

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

object Settings {
    private val BASE_URL = stringPreferencesKey("base_url")
    private val TOKEN = stringPreferencesKey("token")

    private val DRAFT_TITLE = stringPreferencesKey("draft_title")
    private val DRAFT_CONTENT = stringPreferencesKey("draft_content")
    private val DRAFT_TAGS = stringPreferencesKey("draft_tags")
    private val DRAFT_SAVED_AT = longPreferencesKey("draft_saved_at")

    // ---------- 连接配置 ----------
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

    // ---------- 本地草稿 ----------
    fun draftTitle(context: Context): Flow<String> =
        context.dataStore.data.map { it[DRAFT_TITLE] ?: "" }

    fun draftContent(context: Context): Flow<String> =
        context.dataStore.data.map { it[DRAFT_CONTENT] ?: "" }

    fun draftTags(context: Context): Flow<String> =
        context.dataStore.data.map { it[DRAFT_TAGS] ?: "" }

    fun draftSavedAt(context: Context): Flow<Long> =
        context.dataStore.data.map { it[DRAFT_SAVED_AT] ?: 0L }

    suspend fun saveDraft(context: Context, title: String, content: String, tags: String) {
        context.dataStore.edit {
            it[DRAFT_TITLE] = title
            it[DRAFT_CONTENT] = content
            it[DRAFT_TAGS] = tags
            it[DRAFT_SAVED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearDraft(context: Context) {
        context.dataStore.edit {
            it.remove(DRAFT_TITLE)
            it.remove(DRAFT_CONTENT)
            it.remove(DRAFT_TAGS)
            it.remove(DRAFT_SAVED_AT)
        }
    }
}
