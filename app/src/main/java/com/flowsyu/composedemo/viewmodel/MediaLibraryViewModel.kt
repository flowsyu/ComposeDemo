package com.flowsyu.composedemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flowsyu.composedemo.data.SettingsRepository
import com.flowsyu.composedemo.model.MediaFile
import com.flowsyu.composedemo.model.ViewType
import com.flowsyu.composedemo.util.MediaScanner
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MediaLibraryState(
    val mediaFiles: List<MediaFile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val viewType: ViewType = ViewType.LIST
)

class MediaLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    private val mediaScanner = MediaScanner(application)
    
    private val _state = MutableStateFlow(MediaLibraryState())
    val state: StateFlow<MediaLibraryState> = _state.asStateFlow()
    
    private var currentDirectory: String = ""

    init {
        viewModelScope.launch {
            repository.scanDirectory.collectLatest { directory ->
                currentDirectory = directory
                loadMediaFiles(directory)
            }
        }
        
        viewModelScope.launch {
            repository.viewType.collectLatest { type ->
                _state.update { 
                    it.copy(viewType = if (type == "GRID") ViewType.GRID else ViewType.LIST) 
                }
            }
        }
    }
    
    fun loadMediaFiles(directory: String? = null) {
        val targetDirectory = directory ?: currentDirectory
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val files = mediaScanner.scanMediaFiles(targetDirectory)
                _state.update { it.copy(mediaFiles = files, isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    
    fun toggleViewType() {
        val newType = if (state.value.viewType == ViewType.LIST) ViewType.GRID else ViewType.LIST
        viewModelScope.launch {
            repository.setViewType(newType.name)
        }
    }
}
