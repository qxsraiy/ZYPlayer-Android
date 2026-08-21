package com.zyplayer.app.data.local

import androidx.room.*
import com.zyplayer.app.data.model.Video
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM videos ORDER BY cachedAt DESC LIMIT :limit")
    fun getCachedVideos(limit: Int = 500): Flow<List<Video>>

    @Query("SELECT * FROM videos WHERE siteKey = :siteKey AND id = :id LIMIT 1")
    suspend fun getVideo(siteKey: String, id: String): Video?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: Video)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(videos: List<Video>)

    @Query("DELETE FROM videos WHERE key NOT IN (SELECT key FROM videos ORDER BY cachedAt DESC LIMIT :keepCount)")
    suspend fun trimTo(keepCount: Int = 500)

    @Query("DELETE FROM videos")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM videos")
    suspend fun count(): Int
}