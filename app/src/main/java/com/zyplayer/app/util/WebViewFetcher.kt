package com.zyplayer.app.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import com.zyplayer.app.App
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 用隐藏 WebView（Chrome内核）直接加载 API URL
 * 解决 OkHttp 被 Cloudflare/防火墙拦截的问题
 *
 * 原理：WebView = 系统浏览器内核，加载 API URL 就和浏览器一样，
 * 然后用 evaluateJavascript 提取页面文本内容
 */
object WebViewFetcher {

    private const val TAG = "ZYPlayer-WebView"
    private const val TIMEOUT_MS = 30_000L

    private var webView: WebView? = null
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val requestCounter = AtomicInteger(0)

    /** 在主线程初始化 WebView */
    fun ensureInitialized() {
        if (webView != null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            createWebView()
        } else {
            mainHandler.post { createWebView() }
        }
    }

    private fun createWebView() {
        if (webView != null) return
        try {
            val wv = WebView(App.instance)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.allowContentAccess = true
            wv.settings.allowFileAccess = false
            wv.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            webView = wv
            Log.d(TAG, "WebView 初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "WebView 初始化失败: ${e.message}")
        }
    }

    /**
     * 用 WebView 直接加载 API URL，然后提取页面内容
     * 和浏览器访问效果完全一致
     */
    suspend fun fetch(url: String): String = withContext(Dispatchers.Main) {
        ensureInitialized()
        val wv = webView
        if (wv == null) {
            Log.e(TAG, "WebView 未初始化")
            return@withContext ""
        }

        val requestId = "wv_${requestCounter.incrementAndGet()}"
        val deferred = CompletableDeferred<String>()
        pendingRequests[requestId] = deferred

        // 设置超时
        val timeoutRunnable = Runnable {
            val d = pendingRequests.remove(requestId)
            if (d != null && !d.isCompleted) {
                d.completeExceptionally(Exception("WebView 请求超时 (30s)"))
            }
        }
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        // 设置 WebViewClient 拦截请求完成事件
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, loadedUrl: String) {
                Log.d(TAG, "[WebView] onPageFinished: $loadedUrl")
                // 提取页面文本内容
                view.evaluateJavascript("(function(){return document.documentElement.textContent;})()") { result ->
                    val text = if (result != null && result != "null") {
                        // evaluateJavascript 返回 JSON 编码的字符串（带引号和转义）
                        if (result.startsWith("\"") && result.length >= 2) {
                            // evaluateJavascript 返回 JSON 编码字符串，手动解码
                            val raw = result.substring(1, result.length - 1)
                            try {
                                org.json.JSONObject("{\"v\":$raw}").getString("v")
                            } catch (e: Exception) {
                                raw.replace("\\\"", "\"")
                                    .replace("\\\\", "\\")
                                    .replace("\\n", "\n")
                                    .replace("\\t", "\t")
                            }
                        } else {
                            result
                        }
                    } else {
                        ""
                    }
                    Log.d(TAG, "[WebView] 提取内容, 长度=${text.length}")
                    val d = pendingRequests.remove(requestId)
                    if (d != null && !d.isCompleted) {
                        d.complete(text)
                    }
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                // 只处理主框架错误（API URL 本身的错误）
                if (request.isForMainFrame) {
                    Log.e(TAG, "[WebView] 加载错误: ${error.description}")
                    val d = pendingRequests.remove(requestId)
                    if (d != null && !d.isCompleted) {
                        d.completeExceptionally(Exception("WebView 加载失败: ${error.description}"))
                    }
                }
            }
        }

        try {
            // 直接加载 API URL（就像浏览器一样）
            wv.loadUrl(url)
            Log.d(TAG, "[WebView] 开始加载: $url")

            // 等待结果
            val result = deferred.await()
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "[WebView] 请求失败: ${e.message}")
            throw e
        } finally {
            pendingRequests.remove(requestId)
            mainHandler.removeCallbacks(timeoutRunnable)
        }
    }

    /** 清理 WebView 资源 */
    fun destroy() {
        mainHandler.post {
            webView?.destroy()
            webView = null
        }
    }
}