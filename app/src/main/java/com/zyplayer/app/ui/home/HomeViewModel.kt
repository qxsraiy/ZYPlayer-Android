package com.zyplayer.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyplayer.app.App
import com.zyplayer.app.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel : ViewModel() {

    private val repository = App.instance.repository

    private val _videos = MutableLiveData<List<Video>>()
    val videos: LiveData<List<Video>> = _videos

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData("")
    val error: LiveData<String> = _error

    /**
     * 每次打开 App / 点击换一批 / 下拉刷新时调用
     * 从所有启用的源拉取随机影片
     */
    fun loadHomeVideos() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = ""
            try {
                val videos = withContext(Dispatchers.IO) {
                    repository.getHomeVideos()
                }
                if (videos.isEmpty()) {
                    _error.value = "暂无数据，请在设置页添加可用源或检查网络"
                }
                _videos.value = videos
            } catch (e: Exception) {
                _error.value = "加载失败: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }
}