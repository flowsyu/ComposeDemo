package com.flowsyu.composedemo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val SCAN_DIRECTORY_KEY = stringPreferencesKey("scan_directory")
    private val PLAYBACK_MODE_KEY = intPreferencesKey("playback_mode")
    private val VIDEO_PLAYBACK_SPEED_KEY = floatPreferencesKey("video_playback_speed")
    private val VIEW_TYPE_KEY = stringPreferencesKey("view_type")
    
    val scanDirectory: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[SCAN_DIRECTORY_KEY] ?: ""
    }

    val playbackMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PLAYBACK_MODE_KEY] ?: 0 // Default: LoopAll (0)
    }

    val videoPlaybackSpeed: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[VIDEO_PLAYBACK_SPEED_KEY] ?: 1.0f
    }
    
    val viewType: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[VIEW_TYPE_KEY] ?: "LIST"
    }
    
    suspend fun setScanDirectory(path: String) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_DIRECTORY_KEY] = path
        }
    }

    suspend fun setPlaybackMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PLAYBACK_MODE_KEY] = mode
        }
    }

    suspend fun setVideoPlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[VIDEO_PLAYBACK_SPEED_KEY] = speed
        }
    }
    
    suspend fun setViewType(type: String) {
        context.dataStore.edit { preferences ->
            preferences[VIEW_TYPE_KEY] = type
        }
    }
}
