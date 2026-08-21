package com.zyplayer.app.ui.detail

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zyplayer.app.App
import com.zyplayer.app.data.model.VideoDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DetailViewModel(
    private val siteKey: String,
    private val videoId: String
) : ViewModel() {

    private val repository = App.instance.repository

    private val _detail = MutableLiveData<VideoDetail?>()
    val detail: LiveData<VideoDetail?> = _detail

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData("")
    val error: LiveData<String> = _error

    fun loadDetail() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getDetail(siteKey, videoId)
                }
                _detail.value = result
            } catch (e: Exception) {
                _error.value = "获取详情失败: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    class Factory(
        private val siteKey: String,
        private val videoId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailViewModel(siteKey, videoId) as T
        }
    }
}