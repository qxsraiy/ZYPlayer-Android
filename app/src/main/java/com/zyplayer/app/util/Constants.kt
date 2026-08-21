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

    /** 内置默认源 */
    val DEFAULT_SOURCES = listOf(
        "https://www.hongniuzy2.com/api.php/provide/vod/" to "红牛资源",
        "https://bfzyapi.com/api.php/provide/vod/" to "暴风资源",
        "https://www.sdzyapi.com/api.php/provide/vod/" to "闪电资源",
        "https://dyttzyw.tv/api.php/provide/vod/" to "电影天堂",
        "https://tyyszy.com/api.php/provide/vod/" to "天翼资源",
        "https://www.iqiyizy.com/api.php/provide/vod/" to "爱奇艺资源",
        "https://fqzy.me/api.php/provide/vod/" to "番茄资源",
        "https://hsckzy001.com/api.php/provide/vod/" to "红树林资源",
        "https://thzy1.me/api.php/provide/vod/" to "天天资源",
        "https://bwzy.tv/api.php/provide/vod/" to "BW资源",
        "https://cj.lziapi.com/api.php/provide/vod/" to "LZ资源",
        "https://cj.ffzyapi.com/api.php/provide/vod/" to "非凡资源",
        "https://api.guangsuapi.com/api.php/provide/vod/" to "光速资源"
    )
}