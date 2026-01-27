package com.flowsyu.composedemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowsyu.composedemo.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    
    val scanDirectory: StateFlow<String> = repository.scanDirectory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    
    fun setScanDirectory(path: String) {
        viewModelScope.launch {
            repository.setScanDirectory(path)
        }
    }
}
