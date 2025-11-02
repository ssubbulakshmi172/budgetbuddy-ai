package com.budgetbuddy.mobile.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "budgetbuddy_preferences")

class PreferencesDataStore(private val context: Context) {
    
    companion object {
        private val CURRENT_USER_ID = longPreferencesKey("current_user_id")
        private val LAST_SYNC_DATE = stringPreferencesKey("last_sync_date")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val CURRENCY = stringPreferencesKey("currency")
    }
    
    val currentUserId: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[CURRENT_USER_ID]
    }
    
    suspend fun setCurrentUserId(userId: Long) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_USER_ID] = userId
        }
    }
    
    val lastSyncDate: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_SYNC_DATE]
    }
    
    suspend fun setLastSyncDate(date: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_DATE] = date
        }
    }
    
    val themeMode: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "light"
    }
    
    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }
    
    val currency: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CURRENCY] ?: "USD"
    }
    
    suspend fun setCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[CURRENCY] = currency
        }
    }
}

