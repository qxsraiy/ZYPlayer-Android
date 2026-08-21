package com.zyplayer.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 视频缓存实体
 * key = siteKey + "_" + id，保证唯一
 */
@Entity(tableName = "videos")
data class Video(
    @PrimaryKey val key: String,        // siteKey_id
    val siteKey: String,                // 来源标识
    val siteName: String,               // 来源名称
    val id: String,                     // 源站视频ID
    val name: String,                   // 视频名称
    val pic: String,                    // 海报地址
    val note: String,                   // 备注（如：第XX集/HD/更新至XX）
    val type: String,                   // 类型名称
    val year: String,                   // 年份
    val desc: String = "",              // 简介
    val cachedAt: Long = System.currentTimeMillis()  // 缓存时间
)