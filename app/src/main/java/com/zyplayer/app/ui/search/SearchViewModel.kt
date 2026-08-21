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

    /** 搜索进度文字 */
    private val _progress = MutableLiveData("")
    val progress: LiveData<String> = _progress

    /** 是否显示"显示更多"按钮 */
    private val _showLoadMore = MutableLiveData(false)
    val showLoadMore: LiveData<Boolean> = _showLoadMore

    /** 是否已搜索过（用于空状态提示） */
    var isSearched = false
        private set

    /** 全部搜索结果池（最多100条） */
    private var allResults: List<Video> = emptyList()

    /** 当前已显示的条数 */
    private var displayCount = 0

    companion object {
        private const val PAGE_SIZE = 50
        private const val MAX_RESULTS = 100
    }

    /** 多源并行搜索 */
    fun search(keyword: String) {
        viewModelScope.launch {
            _loading.value = true
            _progress.value = "正在搜索…"
            isSearched = true
            allResults = emptyList()
            displayCount = 0
            _showLoadMore.value = false

            try {
                allResults = withContext(Dispatchers.IO) {
                    repository.search(keyword) { completed, total, found ->
                        _progress.postValue("正在搜索 $total 个源…已返回 $found 个")
                    }
                }
                showNextPage()
            } catch (e: Exception) {
                _results.value = emptyList()
                _progress.value = ""
            } finally {
                _loading.value = false
            }
        }
    }

    /** 显示下一页 */
    private fun showNextPage() {
        val next = allResults.take(displayCount + PAGE_SIZE)
        displayCount = next.size
        _results.value = next
        _showLoadMore.value = allResults.size > displayCount
        _progress.value = "共找到 ${allResults.size} 条结果"
    }

    /** 加载更多 / 换一批 */
    fun loadMore() {
        if (allResults.isEmpty()) return
        showNextPage()
    }
}