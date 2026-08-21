package com.zyplayer.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 资源站源配置
 */
@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var name: String,
    val api: String,             // API 地址，如 https://xxx.com/api.php/provide/vod/
    var enabled: Boolean = true,
    val sort: Int = 0
)