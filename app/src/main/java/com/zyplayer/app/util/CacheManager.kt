package com.zyplayer.app.util

import android.content.Context
import com.zyplayer.app.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 缓存管理 — 发布版不内置任何源
 */
object CacheManager {

    private const val SOURCE_VERSION = 4 // 发布版零内置源

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 首次启动标记版本号（不写入任何源） */
    fun ensureDefaultSources(context: Context) {
        val prefs = context.getSharedPreferences("zyplayer_prefs", Context.MODE_PRIVATE)

        // 版本号一致 → 已初始化过，跳过
        if (prefs.getInt("source_version", 0) == SOURCE_VERSION) return

        // 首次启动：标记版本号，不写入任何源
        scope.launch {
            try {
                prefs.edit().putInt("source_version", SOURCE_VERSION).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}