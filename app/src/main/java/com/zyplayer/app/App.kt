package com.zyplayer.app

import android.app.Application
import com.zyplayer.app.data.local.AppDatabase
import com.zyplayer.app.data.local.SourceDao
import com.zyplayer.app.data.local.VideoDao
import com.zyplayer.app.data.repository.VideoRepository

/**
 * 全局 Application，持有数据库与仓库单例
 */
class App : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val sourceDao: SourceDao by lazy { database.sourceDao() }
    val videoDao: VideoDao by lazy { database.videoDao() }
    val repository: VideoRepository by lazy { VideoRepository(sourceDao, videoDao) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 初始化 WebView 内置浏览器（用于绕过 Cloudflare 等拦截）
        com.zyplayer.app.util.WebViewFetcher.ensureInitialized()
    }
}