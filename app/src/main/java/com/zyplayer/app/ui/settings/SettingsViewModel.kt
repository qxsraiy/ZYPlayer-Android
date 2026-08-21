package com.zyplayer.app.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyplayer.app.App
import com.zyplayer.app.data.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel : ViewModel() {

    private val repository = App.instance.repository

    private val _sources = MutableLiveData<List<Source>>()
    val sources: LiveData<List<Source>> = _sources

    init {
        viewModelScope.launch {
            repository.getAllSources().collect { list ->
                _sources.value = list
            }
        }
    }

    fun addSource(name: String, api: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.addSource(name, api)
            }
        }
    }

    fun toggleSource(source: Source) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.setSourceEnabled(source.id, !source.enabled)
            }
        }
    }

    fun deleteSource(source: Source) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteSource(source)
            }
        }
    }

    fun updateSource(source: Source) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateSource(source)
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.clearCache()
            }
        }
    }
}