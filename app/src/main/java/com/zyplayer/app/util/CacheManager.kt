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

    private const val SOURCE_VERSION = 2 // 源列表版本号：更新内置源时 +1，触发重新导入

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 首次启动（或源版本升级）时写入默认源 */
    fun ensureDefaultSources(context: Context) {
        val repo = App.instance.repository
        val prefs = context.getSharedPreferences("zyplayer_prefs", Context.MODE_PRIVATE)

        // 版本号一致 → 跳过（已初始化过）
        if (prefs.getInt("source_version", 0) == SOURCE_VERSION) return

        scope.launch {
            try {
                // 清掉旧的内置源（只清 api 以 "api.php/provide/vod/" 结尾的，保留用户自定义源）
                repo.clearDefaultSources()

                // 写入最新内置源
                for ((api, name) in Constants.DEFAULT_SOURCES) {
                    repo.addSource(name, api)
                }
                prefs.edit().putInt("source_version", SOURCE_VERSION).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 清空所有内置源（仅保留用户在设置页手动添加的） */
    fun resetAllSources(context: Context) {
        val prefs = context.getSharedPreferences("zyplayer_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("source_version").apply() // 下次启动重新导入
    }
}