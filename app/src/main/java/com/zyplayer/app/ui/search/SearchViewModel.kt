package com.zyplayer.app.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyplayer.app.App
import com.zyplayer.app.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
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
        private const val EARLY_SHOW_COUNT = 10 // 搜到10条就提前展示
    }

    /** 多源并行搜索（增量返回） */
    fun search(keyword: String) {
        viewModelScope.launch {
            _loading.value = true
            _progress.value = "正在搜索…"
            isSearched = true
            allResults = emptyList()
            displayCount = 0
            _showLoadMore.value = false
            _results.value = emptyList()

            try {
                withContext(Dispatchers.IO) {
                    repository.search(keyword) { completed, total, found ->
                        _progress.postValue("正在搜索 $total 个源…已返回 $found 个")
                    }.collect { snapshot: List<Video> ->
                        // 每次收到增量结果，更新全部池
                        allResults = snapshot.take(MAX_RESULTS)

                        // 搜到10条以上就立刻展示前50条
                        if (allResults.size >= EARLY_SHOW_COUNT && displayCount == 0) {
                            displayCount = minOf(PAGE_SIZE, allResults.size)
                            _results.postValue(allResults.take(displayCount))
                        } else if (displayCount > 0) {
                            // 已有展示，更新显示量（如果之前显示的少于当前池）
                            val currentShow = allResults.take(displayCount)
                            _results.postValue(currentShow)
                        }
                    }
                }
                // 搜索全部完成
                if (displayCount == 0 && allResults.isNotEmpty()) {
                    // 结果不足10条也展示
                    displayCount = allResults.size
                    _results.value = allResults
                }
                _showLoadMore.value = allResults.size > displayCount
                _progress.value = "共找到 ${allResults.size} 条结果"
            } catch (e: Exception) {
                _results.value = emptyList()
                _progress.value = ""
            } finally {
                _loading.value = false
            }
        }
    }

    /** 加载更多 / 换一批 */
    fun loadMore() {
        if (allResults.isEmpty()) return
        val next = allResults.take(displayCount + PAGE_SIZE)
        displayCount = next.size
        _results.value = next
        _showLoadMore.value = allResults.size > displayCount
        _progress.value = "共找到 ${allResults.size} 条结果"
    }
}