package com.zyplayer.app.data.repository

import com.zyplayer.app.data.local.SourceDao
import com.zyplayer.app.data.local.VideoDao
import com.zyplayer.app.data.model.*
import com.zyplayer.app.data.remote.SourceParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * 数据仓库：协调网络和本地缓存
 */
class VideoRepository(
    private val sourceDao: SourceDao,
    private val videoDao: VideoDao
) {

    /** 获取所有源 */
    fun getAllSources(): Flow<List<Source>> = sourceDao.getAllSources()

    /** 获取已启用的源 */
    fun getEnabledSources(): Flow<List<Source>> = sourceDao.getEnabledSources()

    /** 添加源 */
    suspend fun addSource(name: String, api: String): Boolean {
        val count = sourceDao.count()
        sourceDao.insert(Source(name = name, api = api, sort = count))
        return true
    }

    /** 测试源 */
    suspend fun testSource(api: String): Boolean = SourceParser.testSource(api)

    /** 更新源 */
    suspend fun updateSource(source: Source) = sourceDao.update(source)

    /** 删除源 */
    suspend fun deleteSource(source: Source) = sourceDao.delete(source)

    /** 清空所有内置源（重新导入时使用） */
    suspend fun clearDefaultSources() = sourceDao.clearAll()

    /** 开关源 */
    suspend fun setSourceEnabled(id: Long, enabled: Boolean) =
        sourceDao.setEnabled(id, enabled)

    /**
     * 获取首页随机影片（核心方法）
     * 随机选最多3个源 → 每个源取3~4条 → 汇总随机打乱 → 取10条
     * 并缓存到本地 Room，限制500条
     */
    suspend fun getHomeVideos(): List<Video> = coroutineScope {
        val sources = sourceDao.getEnabledSourcesOnce()
        if (sources.isEmpty()) return@coroutineScope emptyList()

        // 随机选最多3个源（减少首页加载时间）
        val selected = sources.shuffled().take(3)

        // 并发请求选中的源
        val deferredList = selected.map { source ->
            async {
                try {
                    SourceParser.getHomeList(source.api, source.id.toString(), source.name)
                } catch (e: Exception) {
                    emptyList<Video>()
                }
            }
        }

        // 收集结果，每个源最多取4条
        val allVideos = mutableListOf<Video>()
        deferredList.forEach { deferred ->
            val videos = deferred.await()
            allVideos.addAll(videos.take(4))
        }

        // 随机打乱，取10条
        val result = allVideos.shuffled().take(10)

        // 缓存到本地（异步）
        if (result.isNotEmpty()) {
            videoDao.insertAll(result)
            videoDao.trimTo(500) // 限制缓存500条，淘汰最旧的
        }

        result
    }

    /**
     * 搜索（多源并行，增量返回）
     * 每完成一个源就发射一次结果，搜到10条后首页立刻展示，后台继续搜
     * @param onProgress 每次一个源完成时回调 (completed, total, found)
     * @return Flow 每完成一个源发射一次当前全部结果
     */
    suspend fun search(
        keyword: String,
        onProgress: (completed: Int, total: Int, found: Int) -> Unit = { _, _, _ -> }
    ): Flow<List<Video>> = kotlinx.coroutines.flow.flow {
        val sources = sourceDao.getEnabledSourcesOnce()
        if (sources.isEmpty()) {
            emit(emptyList())
            return@flow
        }
        val total = sources.size

        // flow 内部没有 coroutineScope，需要手动创建
        val results = kotlinx.coroutines.coroutineScope {
            sources.map { source: Source ->
                async {
                    try {
                        SourceParser.search(source.api, keyword, source.id.toString(), source.name)
                    } catch (e: Exception) {
                        emptyList<Video>()
                    }
                }
            }.map { it.await() }
        }

        var completed = 0
        val allVideos = mutableListOf<Video>()
        val seenKeys = mutableSetOf<String>()
        for (videos in results) {
            // 去重后加入
            for (v in videos) {
                if (v.key !in seenKeys) {
                    seenKeys.add(v.key)
                    allVideos.add(v)
                }
            }
            completed++
            val snapshot = allVideos.take(100).toList()
            emit(snapshot)
            onProgress(completed, total, allVideos.size)
        }
    }

    /**
     * 获取详情 + 播放列表
     */
    suspend fun getDetail(siteKey: String, id: String): VideoDetail? {
        // 先从缓存找视频信息
        val cachedVideo = videoDao.getVideo(siteKey, id)
        val source = sourceDao.getSource(siteKey.toLongOrNull() ?: return null) ?: return null

        return SourceParser.getDetail(source.api, id, siteKey, source.name)
    }

    /** 获取本地缓存的视频列表 */
    fun getCachedVideos(): Flow<List<Video>> = videoDao.getCachedVideos(500)

    /** 清空缓存 */
    suspend fun clearCache() = videoDao.clearAll()
}