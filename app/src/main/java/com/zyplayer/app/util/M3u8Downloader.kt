package com.zyplayer.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * m3u8 视频下载引擎（纯下载逻辑，无通知）
 * 流程：获取 m3u8 → 解析分片列表 → 下载分片（支持AES-128解密）→ 合并为 .ts → 保存到下载目录
 * 通知和前台服务由 [DownloadService] 管理
 */
object M3u8Downloader {

    private const val TAG = "M3u8Downloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private const val CONCURRENCY = 4

    data class Result(val fileName: String, val fileSize: Long)

    /**
     * 下载 m3u8 视频
     * @param context Context
     * @param url m3u8 地址
     * @param title 视频标题（用于命名文件）
     * @param onProgress (已完成分片数, 总分片数) — 用于通知栏/UI更新
     * @return 下载结果（文件名/大小），失败返回 null
     */
    suspend fun download(
        context: Context,
        url: String,
        title: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result? = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 m3u8 内容
            onProgress(0, 0)
            val m3u8Content = fetchText(url) ?: return@withContext null

            // 2. 如果是 master playlist（包含多个码率），选最大的子列表
            val mediaUrl = resolveMediaPlaylist(url, m3u8Content)
            val mediaContent = if (mediaUrl != url) fetchText(mediaUrl) ?: m3u8Content else m3u8Content

            // 3. 解析分片列表 + 密钥信息
            val parsed = parseSegments(mediaUrl, mediaContent)
            if (parsed.segments.isEmpty()) {
                Log.e(TAG, "未解析到任何分片")
                return@withContext null
            }

            // 4. 下载所有分片到缓存目录
            val tempDir = File(context.cacheDir, "hls_${System.currentTimeMillis()}")
            if (!tempDir.exists()) tempDir.mkdirs()

            var done = 0
            val total = parsed.segments.size
            // 分片并发下载（每批4个）
            val chunks = parsed.segments.chunked(CONCURRENCY)
            for (chunk in chunks) {
                coroutineScope {
                    val deferreds = chunk.map { seg ->
                        async {
                            val bytes = fetchBytes(seg.url) ?: return@async null
                            if (parsed.keyBytes != null) {
                                decrypt(bytes, parsed.keyBytes, ivFor(seg.index, parsed.iv))
                            } else bytes
                        }
                    }
                    deferreds.forEach { deferred ->
                        val data = deferred.await()
                        if (data != null) {
                            File(tempDir, "seg_${String.format("%05d", done)}.ts").writeBytes(data)
                            done++
                            onProgress(done, total)
                        }
                    }
                }
            }

            if (done == 0) {
                Log.e(TAG, "分片全部下载失败")
                return@withContext null
            }

            // 5. 合并所有分片为单个 .ts 文件
            val merged = File(tempDir, "merged.ts")
            FileOutputStream(merged).use { fos ->
                for (i in 0 until done) {
                    val part = File(tempDir, "seg_${String.format("%05d", i)}.ts")
                    if (part.exists()) part.inputStream().use { it.copyTo(fos) }
                }
            }

            // 6. 保存到下载目录（系统 Downloads）
            val fileName = sanitize(title) + ".ts"
            val saved = saveToDownloads(context, merged, fileName)
            val size = saved ?: merged.length()

            // 清理缓存
            tempDir.deleteRecursively()

            Log.d(TAG, "下载完成: $fileName (${size} bytes)")
            Result(fileName, size)
        } catch (e: Exception) {
            Log.e(TAG, "下载失败: ${e.message}", e)
            null
        }
    }

    // ──────────── 解析部分 ────────────

    private data class Segment(val index: Int, val url: String)
    private data class ParsedPlaylist(val segments: List<Segment>, val keyBytes: ByteArray?, val iv: ByteArray?)

    private fun resolveMediaPlaylist(baseUrl: String, content: String): String {
        if (content.contains("#EXT-X-STREAM-INF")) {
            val urls = content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toList()
            if (urls.isNotEmpty()) {
                return resolveUrl(baseUrl, urls.last())
            }
        }
        return baseUrl
    }

    private fun parseSegments(playlistUrl: String, content: String): ParsedPlaylist {
        val segments = mutableListOf<Segment>()
        var keyBytes: ByteArray? = null
        var iv: ByteArray? = null
        var pendingKeyUri: String? = null
        var index = 0

        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-KEY") -> {
                    val method = Regex("METHOD=([^,]+)").find(line)?.groupValues?.get(1)
                    if (method == "AES-128") {
                        pendingKeyUri = Regex("URI=\"?([^\",]+)\"?").find(line)?.groupValues?.get(1)
                        val ivHex = Regex("IV=0x([0-9a-fA-F]+)").find(line)?.groupValues?.get(1)
                        iv = ivHex?.let { hexToBytes(it) }
                    }
                }
                line.startsWith("#") -> { }
                line.isNotEmpty() -> {
                    val segUrl = resolveUrl(playlistUrl, line)
                    segments.add(Segment(index, segUrl))
                    index++
                    if (pendingKeyUri != null && keyBytes == null) {
                        keyBytes = fetchBytes(resolveUrl(playlistUrl, pendingKeyUri!!))
                        pendingKeyUri = null
                    }
                }
            }
        }
        return ParsedPlaylist(segments, keyBytes, iv)
    }

    private fun ivFor(segIndex: Int, globalIv: ByteArray?): ByteArray {
        val iv = ByteArray(16)
        if (globalIv != null) {
            globalIv.copyInto(iv)
            return iv
        }
        var idx = segIndex.toLong()
        for (i in 7 downTo 0) {
            iv[8 + i] = (idx and 0xFF).toByte()
            idx = idx ushr 8
        }
        return iv
    }

    private fun decrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(data)
        } catch (e: Exception) {
            Log.e(TAG, "解密失败: ${e.message}")
            data
        }
    }

    // ──────────── 网络部分 ────────────

    private fun fetchText(url: String): String? {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", url.substringBefore("/", "") + "/")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文本失败: ${e.message}")
            null
        }
    }

    private fun fetchBytes(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", url.substringBefore("/", "") + "/")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载分片失败: ${e.message}")
            null
        }
    }

    // ──────────── 工具部分 ────────────

    private fun resolveUrl(base: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val baseUri = Uri.parse(base)
        if (path.startsWith("/")) {
            return "${baseUri.scheme}://${baseUri.host}${path}"
        }
        val basePath = base.substringBeforeLast("/", "")
        return "$basePath/$path"
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    private fun sanitize(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
    }

    private fun saveToDownloads(context: Context, file: File, fileName: String): Long? {
        return try {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "video/mp2t")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ZYPlayer")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                    return file.length()
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val zyDir = File(dir, "ZYPlayer")
                if (!zyDir.exists()) zyDir.mkdirs()
                val target = File(zyDir, fileName)
                file.inputStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                return target.length()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "保存失败: ${e.message}")
            null
        }
    }
}