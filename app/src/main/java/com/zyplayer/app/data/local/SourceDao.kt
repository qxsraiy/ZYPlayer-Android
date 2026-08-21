package com.zyplayer.app.data.local

import androidx.room.*
import com.zyplayer.app.data.model.Source
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {

    @Query("SELECT * FROM sources ORDER BY sort ASC, id ASC")
    fun getAllSources(): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY sort ASC, id ASC")
    fun getEnabledSources(): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY sort ASC, id ASC")
    suspend fun getEnabledSourcesOnce(): List<Source>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getSource(id: Long): Source?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: Source): Long

    @Update
    suspend fun update(source: Source)

    @Delete
    suspend fun delete(source: Source)

    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun count(): Int

    @Query("DELETE FROM sources")
    suspend fun clearAll()
}