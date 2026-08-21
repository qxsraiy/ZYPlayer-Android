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

    /** 内置默认源（36个） */
    val DEFAULT_SOURCES = listOf(
        "https://www.zuidazy.co/api.php/provide/vod/" to "最达资源",
        "https://www.360zy.com/api.php/provide/vod/" to "360资源",
        "https://ryzy.tv/api.php/provide/vod/" to "如意资源",
        "https://moduzy.com/api.php/provide/vod/" to "魔都资源",
        "https://huohuzy.com/api.php/provide/vod/" to "火狐资源",
        "https://dyttzyw.tv/api.php/provide/vod/" to "电影天堂",
        "https://www.jisuzy.com/api.php/provide/vod/" to "极速资源",
        "https://huyazy.net/api.php/provide/vod/" to "虎牙资源",
        "https://dbzy.tv/api.php/provide/vod/" to "DB资源",
        "https://tyyszy.com/api.php/provide/vod/" to "天翼资源",
        "https://www.maoyanzy.com/api.php/provide/vod/" to "猫眼资源",
        "https://okzyw.cc/api.php/provide/vod/" to "OK资源",
        "https://yayazy2.com/api.php/provide/vod/" to "丫丫资源",
        "https://www.iqiyizy.com/api.php/provide/vod/" to "爱奇艺资源",
        "https://api.apibdzy.com/api.php/provide/vod/" to "APIBD资源",
        "https://cj.lziapi.com/api.php/provide/vod/?t=46" to "LZ资源(分类46)",
        "https://lebozy.com/api.php/provide/vod/" to "乐播资源",
        "https://dadizy.com/api.php/provide/vod/" to "大地资源",
        "https://jkunzy.com/api.php/provide/vod/" to "J昆资源",
        "https://yutuzy.com/api.php/provide/vod/" to "芋头资源",
        "https://jingpinx.com/api.php/provide/vod/" to "精品资源",
        "https://senlinzy.com/api.php/provide/vod/" to "森林资源",
        "https://thzy1.me/api.php/provide/vod/" to "天天资源",
        "https://sex8zy.com/api.php/provide/vod/" to "Sex8资源",
        "https://www.xiangjiaozyw.com/api.php/provide/vod/" to "香蕉资源",
        "https://fqzy.me/api.php/provide/vod/" to "番茄资源",
        "https://hsckzy001.com/api.php/provide/vod/" to "红树林资源",
        "https://laosebizy.com/api.php/provide/vod/" to "老色批资源",
        "https://www.hongniuzy2.com/api.php/provide/vod/" to "红牛资源",
        "https://bfzyapi.com/api.php/provide/vod/" to "暴风资源",
        "https://cj.lziapi.com/api.php/provide/vod/" to "LZ资源",
        "https://cj.ffzyapi.com/api.php/provide/vod/" to "非凡资源",
        "https://api.guangsuapi.com/api.php/provide/vod/" to "光速资源",
        "https://www.sdzyapi.com/api.php/provide/vod/" to "闪电资源",
        "https://suonizy.com/api.php/provide/vod/" to "索尼斯资源"
    )
}