package com.zyplayer.app.util

object Constants {

    /** 缓存限制：视频列表最多保存条数 */
    const val CACHE_MAX_VIDEOS = 500

    /** 首页每次展示的随机影片数 */
    const val HOME_RANDOM_COUNT = 10

    /** 每个源在首页最多取几条 */
    const val HOME_PER_SOURCE = 3

    /** 用户代理 */
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    /** 内置默认源 — 发布版不内置任何源，由用户自行添加 */
    val DEFAULT_SOURCES = emptyList<Pair<String, String>>()
}