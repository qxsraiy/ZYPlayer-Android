package com.zyplayer.app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyplayer.app.App
import com.zyplayer.app.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel : ViewModel() {

    private val repository = App.instance.repository

    private val _results = MutableLiveData<List<Video>>()
    val results: LiveData<List<Video>> = _results

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    /** 多源并行搜索 */
    fun search(keyword: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val videos = withContext(Dispatchers.IO) {
                    repository.search(keyword)
                }
                _results.value = videos
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }
}