package com.zyplayer.app.data.model

/**
 * 播放条目（某集的某线路）
 */
data class PlayItem(
    val label: String,   // 显示名称，如"第01集" / "线路A 第01集"
    val url: String      // 播放地址
)

/**
 * 视频详情（含播放列表）
 */
data class VideoDetail(
    val video: Video,
    val groups: List<PlayGroup>  // 多个播放组（线路）
)

/**
 * 播放组（线路）
 */
data class PlayGroup(
    val name: String,        // 线路名，如"ckm3u8" / "线路A"
    val items: List<PlayItem>
)