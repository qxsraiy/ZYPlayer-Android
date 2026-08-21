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

    /** 开关源 */
    suspend fun setSourceEnabled(id: Long, enabled: Boolean) =
        sourceDao.setEnabled(id, enabled)

    /**
     * 获取首页随机影片（核心方法）
     * 并发请求所有启用的源 → 每个源取3条 → 汇总随机打乱 → 取20条
     * 并缓存到本地 Room，限制500条
     */
    suspend fun getHomeVideos(): List<Video> = coroutineScope {
        val sources = sourceDao.getEnabledSourcesOnce()
        if (sources.isEmpty()) return@coroutineScope emptyList()

        // 并发请求所有源
        val deferredList = sources.map { source ->
            async {
                try {
                    SourceParser.getHomeList(source.api, source.id.toString(), source.name)
                } catch (e: Exception) {
                    emptyList<Video>()
                }
            }
        }

        // 收集结果，每个源最多取3条
        val allVideos = mutableListOf<Video>()
        deferredList.forEach { deferred ->
            val videos = deferred.await()
            allVideos.addAll(videos.take(3))
        }

        // 随机打乱，取20条
        val result = allVideos.shuffled().take(20)

        // 缓存到本地（异步）
        if (result.isNotEmpty()) {
            videoDao.insertAll(result)
            videoDao.trimTo(500) // 限制缓存500条，淘汰最旧的
        }

        result
    }

    /**
     * 搜索（多源并行）
     */
    suspend fun search(keyword: String): List<Video> = coroutineScope {
        val sources = sourceDao.getEnabledSourcesOnce()
        if (sources.isEmpty()) return@coroutineScope emptyList()

        val deferredList = sources.map { source ->
            async {
                try {
                    SourceParser.search(source.api, keyword, source.id.toString(), source.name)
                } catch (e: Exception) {
                    emptyList<Video>()
                }
            }
        }

        val allVideos = mutableListOf<Video>()
        deferredList.forEach { deferred ->
            allVideos.addAll(deferred.await())
        }
        allVideos.distinctBy { it.key }.take(50)
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