package com.zyplayer.app.data.remote

import android.util.Log
import com.zyplayer.app.data.model.Video
import com.zyplayer.app.data.model.PlayGroup
import com.zyplayer.app.data.model.PlayItem
import com.zyplayer.app.data.model.VideoDetail
import com.zyplayer.app.util.Constants
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 苹果CMS JSON API 解析器
 * 支持接口：ac=list / ac=videolist / ac=detail / wd=xxx
 * 处理 $$ 多线路、# 多集、$ 字段分隔
 */
object SourceParser {

    private const val TAG = "ZYPlayer"

    /**
     * DNS over HTTPS：绕过被污染的本地 DNS（解决域名被解析到 127.0.0.1 的问题）
     * 使用阿里公共 DNS（国内可达）
     */
    private val dohDns = DnsOverHttpsCompat.create()

    private fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)   // 连接超时延长到15秒
        .readTimeout(30, TimeUnit.SECONDS)       // 读取超时延长到30秒
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)                   // 跟随重定向
        .followSslRedirects(true)                // 跟随 SSL 重定向
        .retryOnConnectionFailure(true)          // 连接失败自动重试

    /**
     * 主客户端：系统默认 TLS（和 Chrome 指纹更接近，减少 Cloudflare 拦截）
     * + DoH 防 DNS 污染
     */
    private val client = baseClientBuilder()
        .dns(dohDns)
        .build()

    /**
     * 备用客户端：宽松 SSL（兼容自签/过期证书的资源站）
     */
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslSocketFactory = run {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        sslContext.socketFactory
    }

    private val looseClient = baseClientBuilder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager) // 信任所有证书
        .hostnameVerifier { _, _ -> true }       // 不校验主机名
        .build()

    /**
     * 获取首页推荐列表
     */
    suspend fun getHomeList(api: String, siteKey: String, siteName: String): List<Video> {
        return try {
            // 尝试 ac=list 和 ac=videolist 两种参数
            val url = buildUrl(api, mapOf("ac" to "list", "pg" to "1", "pagesize" to "20"))
            val json = requestJson(url)
            val videos = parseVodList(json, siteKey, siteName, api)
            if (videos.isNotEmpty()) return videos

            // 如果 ac=list 没数据，尝试 ac=videolist（兼容旧版）
            val url2 = buildUrl(api, mapOf("ac" to "videolist", "pg" to "1", "pagesize" to "20"))
            val json2 = requestJson(url2)
            parseVodList(json2, siteKey, siteName, api)
        } catch (e: Exception) {
            Log.e(TAG, "[请求异常] ${e.javaClass.simpleName}: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 搜索
     */
    suspend fun search(api: String, keyword: String, siteKey: String, siteName: String): List<Video> {
        return try {
            // 尝试 ac=list 和 ac=videolist 两种参数
            val url = buildUrl(api, mapOf("ac" to "list", "wd" to keyword, "pagesize" to "30"))
            val json = requestJson(url)
            val videos = parseVodList(json, siteKey, siteName, api)
            if (videos.isNotEmpty()) return videos

            val url2 = buildUrl(api, mapOf("ac" to "videolist", "wd" to keyword, "pagesize" to "30"))
            val json2 = requestJson(url2)
            parseVodList(json2, siteKey, siteName, api)
        } catch (e: Exception) {
            Log.e(TAG, "[请求异常] ${e.javaClass.simpleName}: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 获取详情 + 播放列表
     */
    suspend fun getDetail(api: String, id: String, siteKey: String, siteName: String): VideoDetail? {
        return try {
            val url = buildUrl(api, mapOf("ac" to "detail", "ids" to id))
            val json = requestJson(url)
            val root = JSONObject(json)
            val item = extractFirstListItem(root) ?: return null
            val video = parseVodItem(item, siteKey, siteName, api) ?: return null

            val playFrom = item.optString("vod_play_from", "")
            val playUrl = item.optString("vod_play_url", "")
            val groups = parsePlayGroups(playFrom, playUrl)
            VideoDetail(video, groups)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 测试源是否可用
     */
    suspend fun testSource(api: String): Boolean {
        return try {
            val url = buildUrl(api, mapOf("ac" to "list", "pg" to "1", "pagesize" to "1"))
            val json = requestJson(url)
            val root = JSONObject(json)
            val result = extractListArray(root)
            if (result != null && result.length() > 0) return true

            // 再试 videolist
            val url2 = buildUrl(api, mapOf("ac" to "videolist", "pg" to "1", "pagesize" to "1"))
            val json2 = requestJson(url2)
            val root2 = JSONObject(json2)
            val result2 = extractListArray(root2)
            result2?.length() ?: 0 > 0
        } catch (e: Exception) {
            false
        }
    }

    // ========== 内部方法 ==========

    /**
     * 构建 API URL（健壮版）
     * 兼容各种输入：
     *   - https://xxx.com/api.php/provide/vod/
     *   - https://xxx.com/api.php/provide/vod
     *   - https://xxx.com/api.php/provide/vod?foo=bar（已带参数）
     *   - https://xxx.com/api.php/provide/
     *   - https://xxx.com/api.php（自动补 /provide/vod/）
     */
    private fun buildUrl(base: String, params: Map<String, String>): String {
        var b = base.trim()
        val queryIndex = b.indexOf("?")
        var existingQuery = ""
        if (queryIndex >= 0) {
            existingQuery = b.substring(queryIndex)
            b = b.substring(0, queryIndex)
        }

        // 如果以 api.php 结尾（没有 /provide/vod/ 路径），自动补全
        if (b.endsWith("api.php")) {
            b += "/provide/vod/"
        }

        // 确保末尾有斜杠（苹果CMS路由要求）
        if (!b.endsWith("/")) {
            b += "/"
        }

        val separator = if (existingQuery.isNotEmpty()) "&" else "?"
        return b + existingQuery + separator + params.map { "${it.key}=${it.value}" }.joinToString("&")
    }

    private suspend fun requestJson(url: String): String {
        Log.d(TAG, "[请求URL] $url")
        // 第一级：主客户端（系统默认 TLS）
        try {
            return requestWithClient(client, url)
        } catch (e: Exception) {
            Log.w(TAG, "[降级1] 主客户端失败: ${e.javaClass.simpleName}: ${e.message}")
        }
        // 第二级：宽松 SSL 客户端（自签证书）
        try {
            return requestWithClient(looseClient, url)
        } catch (e: Exception) {
            Log.w(TAG, "[降级2] 宽松SSL失败: ${e.javaClass.simpleName}: ${e.message}")
        }
        // 第三级：WebView 内置浏览器（Chrome内核，绕过Cloudflare）
        Log.w(TAG, "[降级3] 尝试 WebView 内置浏览器...")
        val webText = com.zyplayer.app.util.WebViewFetcher.fetch(url)
        if (webText.isEmpty()) {
            throw Exception("WebView 也失败了")
        }
        return webText
    }

    private suspend fun requestWithClient(httpClient: OkHttpClient, url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", Constants.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Referer", extractDomain(url)) // 加 Referer 防盗链
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Upgrade-Insecure-Requests", "1")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        val respCode = response.code
        val bytes = response.body?.bytes() ?: byteArrayOf()
        Log.d(TAG, "[响应] HTTP $respCode, 字节数=${bytes.size}, 开始字符=${String(bytes, Charset.forName("ISO-8859-1")).take(30)}")

        // 智能编码探测：UTF-8 严格解码失败 → 自动降级 GBK
        val text = decodeSmart(bytes)
        Log.d(TAG, "[解码] 前200字符: ${text.take(200)}")
        return text
    }

    /** 智能解码：处理 BOM、UTF-8、GBK */
    private fun decodeSmart(raw: ByteArray): String {
        var bytes = raw
        // 去掉 UTF-8 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            bytes = bytes.copyOfRange(3, bytes.size)
            Log.d(TAG, "[解码] 检测到 UTF-8 BOM，已移除")
        }
        // 先尝试严格 UTF-8
        return try {
            val decoder = Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            // UTF-8 失败，说明是 GBK/GB2312 老站
            Log.d(TAG, "[解码] UTF-8解析失败(${e.message})，尝试 GBK")
            String(bytes, Charset.forName("GBK"))
        }
    }

    /** 从 URL 中提取域名作为 Referer */
    private fun extractDomain(url: String): String {
        return try {
            val u = java.net.URL(url)
            "${u.protocol}://${u.host}/"
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseVodList(json: String, siteKey: String, siteName: String, baseUrl: String = ""): List<Video> {
        try {
            val root = JSONObject(json)
            val list = extractListArray(root) ?: return emptyList()
            val result = mutableListOf<Video>()
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                parseVodItem(item, siteKey, siteName, baseUrl)?.let { result.add(it) }
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "[解析] 非JSON内容（可能是网页/错误页）: ${e.message}")
            return emptyList()
        }
    }

    /** 提取 list 数组（兼容 data 包装） */
    private fun extractListArray(root: JSONObject): org.json.JSONArray? {
        val direct = root.optJSONArray("list")
        if (direct != null) return direct
        return root.optJSONObject("data")?.optJSONArray("list")
    }

    /** 提取第一个 list 元素 */
    private fun extractFirstListItem(root: JSONObject): org.json.JSONObject? {
        val arr = extractListArray(root) ?: return null
        return if (arr.length() > 0) arr.optJSONObject(0) else null
    }

    /** 解析单个 vod 对象 */
    private fun parseVodItem(item: org.json.JSONObject, siteKey: String, siteName: String, baseUrl: String = ""): Video? {
        val id = item.optString("vod_id", "")
        val name = item.optString("vod_name", "")
        if (id.isEmpty() || name.isEmpty()) return null
        var pic = item.optString("vod_pic", "")
        // 相对路径 → 自动拼接域名
        if (pic.startsWith("/") && baseUrl.isNotEmpty()) {
            val domain = extractDomain(baseUrl).trimEnd('/')
            pic = "$domain$pic"
        }
        val note = item.optString("vod_remarks", "")
        val type = item.optString("type_name", "")
        val year = item.optString("vod_year", "")
        val desc = item.optString("vod_content", "")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return Video(
            key = "${siteKey}_$id",
            siteKey = siteKey,
            siteName = siteName,
            id = id,
            name = name,
            pic = pic,
            note = note,
            type = type,
            year = year,
            desc = desc
        )
    }

    /**
     * 解析播放组（多线路）
     * 苹果CMS格式：
     *   vod_play_from = "ckm3u8$$$线路B"
     *   vod_play_url  = "第01集$url1#第02集$url2$$$第01集$url3#第02集$url4"
     */
    private fun parsePlayGroups(playFrom: String, playUrl: String): List<PlayGroup> {
        if (playUrl.isEmpty()) return listOf(PlayGroup("默认", listOf(PlayItem("暂无片源", ""))))

        val froms = playFrom.split("\\$\\$\\$".toRegex())
        val urls = playUrl.split("\\$\\$\\$".toRegex())

        val groups = mutableListOf<PlayGroup>()

        for (i in urls.indices) {
            val groupName = froms.getOrElse(i) { "线路${i + 1}" }.ifEmpty { "线路${i + 1}" }
            val episodes = urls[i].split("#")

            val items = mutableListOf<PlayItem>()
            for (ep in episodes) {
                val parts = ep.split("$")
                if (parts.size >= 2) {
                    val label = parts[0].trim()
                    var url = parts[1].trim()
                    if (parts.size > 2) {
                        url = parts[1].trim() + "?" + parts.drop(2).joinToString("&")
                    }
                    items.add(PlayItem(label, url))
                } else if (parts.size == 1 && parts[0].isNotEmpty()) {
                    items.add(PlayItem("第${items.size + 1}集", parts[0].trim()))
                }
            }
            if (items.isNotEmpty()) {
                groups.add(PlayGroup(groupName, items))
            }
        }
        return groups
    }
}

/**
 * DNS over HTTPS 客户端
 * 绕过被污染的本地 DNS（部分域名被解析到 127.0.0.1）
 * 使用阿里公共 DNS（国内可达，支持 JSON 格式）
 */
private object DnsOverHttpsCompat {

    private const val TAG = "ZYPlayer-DoH"
    private val DOH_URL = "https://dns.alidns.com/resolve"

    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun create(): Dns {
        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                try {
                    val url = "https://dns.alidns.com/resolve?name=$hostname&type=A"
                    val request = Request.Builder().url(url)
                        .header("Accept", "application/dns-json")
                        .build()
                    val response = bootstrapClient.newCall(request).execute()
                    val body = response.body?.string() ?: "{}"
                    val json = JSONObject(body)
                    if (json.has("Answer")) {
                        val answers = json.getJSONArray("Answer")
                        val ips = mutableListOf<InetAddress>()
                        for (i in 0 until answers.length()) {
                            val obj = answers.getJSONObject(i)
                            if (obj.optInt("type") == 1) { // A record (IPv4)
                                obj.optString("data").let { ip ->
                                    try {
                                        ips.add(InetAddress.getByName(ip))
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        if (ips.isNotEmpty()) {
                            Log.d(TAG, "[DoH] $hostname → ${ips.joinToString { it.hostAddress }}")
                            return ips
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[DoH] $hostname 解析失败: ${e.message}")
                }
                // 降级到系统 DNS
                return Dns.SYSTEM.lookup(hostname)
            }
        }
    }
}