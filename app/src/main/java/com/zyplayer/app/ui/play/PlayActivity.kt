package com.zyplayer.app.ui.play

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.zyplayer.app.R
import com.zyplayer.app.databinding.ActivityPlayBinding
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayBinding
    private var player: ExoPlayer? = null
    private var playUrl: String = ""
    private var currentMode: PlayMode = PlayMode.DETECTING

    enum class PlayMode { DETECTING, EXO_PLAYER, WEB_VIEW }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        val name = intent.getStringExtra(EXTRA_NAME) ?: ""

        binding.tvTitle.text = name

        if (playUrl.isEmpty()) {
            Toast.makeText(this, R.string.play_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 自动检测播放模式
        currentMode = detectPlayMode(playUrl)

        binding.btnBack.setOnClickListener { finish() }

        when (currentMode) {
            PlayMode.EXO_PLAYER -> {
                setupExoPlayer()
                binding.tvSwitchMode.visibility = View.VISIBLE
                binding.tvSwitchMode.setOnClickListener { switchToWebView() }
            }
            PlayMode.WEB_VIEW -> {
                setupWebView()
                binding.tvSwitchMode.visibility = View.VISIBLE
                binding.tvSwitchMode.text = "切换原生播放"
                binding.tvSwitchMode.setOnClickListener { switchToExoPlayer() }
            }
            PlayMode.DETECTING -> {
                // 先试 ExoPlayer，失败后自动切 WebView
                setupExoPlayer()
                binding.tvSwitchMode.visibility = View.VISIBLE
                binding.tvSwitchMode.setOnClickListener { switchToWebView() }
            }
        }
    }

    /** 根据 URL 检测播放模式 */
    private fun detectPlayMode(url: String): PlayMode {
        val lower = url.lowercase()

        // 明确是视频格式 → ExoPlayer
        val videoExtensions = listOf(".m3u8", ".mp4", ".ts", ".mkv", ".avi", ".flv", ".webm", ".mov", ".3gp")
        if (videoExtensions.any { lower.contains(it) }) {
            return PlayMode.EXO_PLAYER
        }

        // 明确是网页格式 → WebView
        val webPatterns = listOf("/player/", "/play/", "?url=", "?vid=", "?v=", ".html", ".php")
        if (webPatterns.any { lower.contains(it) }) {
            return PlayMode.WEB_VIEW
        }

        // 不确定 → 先试 ExoPlayer
        return PlayMode.DETECTING
    }

    /** 初始化 ExoPlayer */
    @SuppressLint("SetTextI18n")
    @OptIn(UnstableApi::class)
    private fun setupExoPlayer() {
        binding.tvLoading.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE
        binding.tvSwitchMode.text = "切换到网页"

        // 主客户端：系统默认 TLS（指纹接近浏览器，减少 Cloudflare 拦截）
        // DoH 在播放器里不需要，视频流不经过 DNS 污染
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")

        player = ExoPlayer.Builder(this)
            .build()
            .also { exoPlayer ->
                binding.playerView.player = exoPlayer

                val mediaItem = MediaItem.fromUri(playUrl)
                val mediaSource = if (playUrl.contains(".m3u8", ignoreCase = true)) {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                } else {
                    ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                }

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true

                // 播放失败监听 → 自动切 WebView
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // 如果当前是 DETECTING 模式，自动切 WebView
                        if (currentMode == PlayMode.DETECTING) {
                            Toast.makeText(this@PlayActivity, "原生播放失败，尝试网页模式", Toast.LENGTH_SHORT).show()
                            switchToWebView()
                        } else {
                            Toast.makeText(
                                this@PlayActivity,
                                "播放失败: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                })
            }
    }

    /** 初始化 WebView */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        currentMode = PlayMode.WEB_VIEW
        binding.tvLoading.visibility = View.GONE
        binding.playerView.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE

        // 释放 ExoPlayer
        player?.release()
        player = null

        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.tvLoading.visibility = View.VISIBLE
                binding.tvLoading.text = "正在加载播放页面..."
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.tvLoading.visibility = View.GONE
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                // 拦截视频链接，尝试用 ExoPlayer 播放
                url?.let { checkAndSwitchToExoPlayer(it) }
                return true // 拦截，不继续加载
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    binding.tvLoading.visibility = View.VISIBLE
                    binding.tvLoading.text = "加载中 $newProgress%"
                } else {
                    binding.tvLoading.visibility = View.GONE
                }
            }
        }

        binding.webView.loadUrl(playUrl)
    }

    /** 检查 URL 是否为视频，若是则切换到 ExoPlayer */
    private fun checkAndSwitchToExoPlayer(url: String) {
        val lower = url.lowercase()
        val videoExtensions = listOf(".m3u8", ".mp4", ".ts", ".mkv")
        if (videoExtensions.any { lower.contains(it) }) {
            playUrl = url
            Toast.makeText(this, "检测到视频地址，切换到原生播放", Toast.LENGTH_SHORT).show()
            setupExoPlayer()
        } else {
            // 不是视频，继续在 WebView 加载
            binding.webView.loadUrl(url)
        }
    }

    /** 切换到 WebView 模式 */
    private fun switchToWebView() {
        setupWebView()
    }

    /** 切换到 ExoPlayer 模式 */
    private fun switchToExoPlayer() {
        currentMode = PlayMode.EXO_PLAYER
        setupExoPlayer()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        binding.webView.destroy()
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack() && currentMode == PlayMode.WEB_VIEW) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        const val EXTRA_SITE_KEY = "site_key"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_URL = "play_url"
        const val EXTRA_NAME = "play_name"

        fun start(context: Context, url: String, name: String) {
            val intent = Intent(context, PlayActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_NAME, name)
            }
            context.startActivity(intent)
        }
    }
}