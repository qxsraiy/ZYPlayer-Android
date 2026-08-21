package com.zyplayer.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏实体
 */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val key: String,        // siteKey_id
    val siteKey: String,
    val siteName: String,
    val id: String,
    val name: String,
    val pic: String,
    val note: String,
    val type: String,
    val year: String,
    val desc: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toVideo() = Video(
        key = key, siteKey = siteKey, siteName = siteName,
        id = id, name = name, pic = pic, note = note,
        type = type, year = year, desc = desc
    )
}