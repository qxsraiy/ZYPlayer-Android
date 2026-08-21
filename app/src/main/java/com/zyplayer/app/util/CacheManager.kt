package com.zyplayer.app.util

import android.content.Context
import com.zyplayer.app.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 缓存管理：应用启动时用默认源初始化数据库（仅首次）
 */
object CacheManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 首次启动时写入默认源（等价于旧 App 的 db.init('site')） */
    fun ensureDefaultSources(context: Context) {
        val repo = App.instance.repository
        val prefs = context.getSharedPreferences("zyplayer_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("initialized", false)) return

        scope.launch {
            try {
                for ((api, name) in Constants.DEFAULT_SOURCES) {
                    repo.addSource(name, api)
                }
                prefs.edit().putBoolean("initialized", true).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}