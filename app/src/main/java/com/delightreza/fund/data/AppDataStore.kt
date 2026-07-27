package com.delightreza.fund.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

val Context.dataStore by preferencesDataStore(name = "settings")

class AppDataStore(private val context: Context) {
    companion object {
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val SELECTED_USER = stringPreferencesKey("selected_user")
        val CACHED_DATA = stringPreferencesKey("cached_json_data")
        val CONFIG_URL = stringPreferencesKey("config_url")
        val CACHED_CONFIG = stringPreferencesKey("cached_config_json")
        val SAVED_REPOS = stringSetPreferencesKey("saved_repo_urls")
        val PENDING_DATA_SYNC = androidx.datastore.preferences.core.booleanPreferencesKey("pending_data_sync")
        val PENDING_CONFIG_SYNC = androidx.datastore.preferences.core.booleanPreferencesKey("pending_config_sync")
        val PENDING_COMMIT_MSG = stringPreferencesKey("pending_commit_msg")
    }

    val pendingSyncFlow: Flow<Boolean> = context.dataStore.data.map { 
        (it[PENDING_DATA_SYNC] == true) || (it[PENDING_CONFIG_SYNC] == true)
    }

    suspend fun setPendingDataSync(pending: Boolean, msg: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[PENDING_DATA_SYNC] = pending
            if (pending && !msg.isNullOrEmpty()) {
                preferences[PENDING_COMMIT_MSG] = msg
            } else if (!pending) {
                preferences.remove(PENDING_COMMIT_MSG)
            }
        }
    }

    suspend fun setPendingConfigSync(pending: Boolean) {
        context.dataStore.edit { it[PENDING_CONFIG_SYNC] = pending }
    }

    suspend fun hasPendingDataSync(): Boolean {
        return context.dataStore.data.map { it[PENDING_DATA_SYNC] == true }.first()
    }

    suspend fun hasPendingConfigSync(): Boolean {
        return context.dataStore.data.map { it[PENDING_CONFIG_SYNC] == true }.first()
    }

    suspend fun getPendingCommitMsg(): String? {
        return context.dataStore.data.map { it[PENDING_COMMIT_MSG] }.first()
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        val stored = preferences[GITHUB_TOKEN]
        if (!stored.isNullOrBlank()) {
            stored
        } else {
            null
        }
    }
    val userFlow: Flow<String?> = context.dataStore.data.map { it[SELECTED_USER] }
    val configUrlFlow: Flow<String?> = context.dataStore.data.map { it[CONFIG_URL] }
    val savedReposFlow: Flow<Set<String>> = context.dataStore.data.map { it[SAVED_REPOS] ?: emptySet() }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[GITHUB_TOKEN] = token }
    }
    
    suspend fun saveUser(user: String) {
        context.dataStore.edit { it[SELECTED_USER] = user }
    }

    suspend fun saveConfigUrl(url: String) {
        context.dataStore.edit { it[CONFIG_URL] = url }
    }

    suspend fun addSavedRepo(url: String, title: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[SAVED_REPOS] ?: emptySet()
            val filtered = currentSet.filter { entry ->
                parseUrlFromEntry(entry) != url
            }.toMutableSet()
            val jsonEntry = JSONObject().apply {
                put("t", title)
                put("u", url)
            }.toString()
            filtered.add(jsonEntry)
            preferences[SAVED_REPOS] = filtered
        }
    }

    suspend fun removeSavedRepo(url: String) {
        context.dataStore.edit { preferences ->
            val currentSet = preferences[SAVED_REPOS] ?: emptySet()
            preferences[SAVED_REPOS] = currentSet.filter { parseUrlFromEntry(it) != url }.toSet()
        }
    }
    
    private fun parseUrlFromEntry(entry: String): String {
        return if (entry.trim().startsWith("{")) {
            try { JSONObject(entry).optString("u") } catch (e: Exception) { entry }
        } else {
            entry
        }
    }

    suspend fun saveCache(json: String) {
        context.dataStore.edit { it[CACHED_DATA] = json }
    }

    suspend fun getCache(): String? {
        return context.dataStore.data.map { it[CACHED_DATA] }.first()
    }

    suspend fun saveConfigCache(json: String) {
        context.dataStore.edit { it[CACHED_CONFIG] = json }
    }

    suspend fun getConfigCache(): AppConfig? {
        val json = context.dataStore.data.map { it[CACHED_CONFIG] }.first()
        return if (!json.isNullOrEmpty()) {
            try { Gson().fromJson(json, AppConfig::class.java) } catch (e: Exception) { null }
        } else null
    }

    suspend fun clearUser() {
        context.dataStore.edit { it.remove(SELECTED_USER) }
    }
    
    suspend fun clearConfig() {
        context.dataStore.edit { 
            it.remove(CONFIG_URL)
            it.remove(CACHED_CONFIG)
            it.remove(CACHED_DATA)
            it.remove(SELECTED_USER)
        }
    }
}
