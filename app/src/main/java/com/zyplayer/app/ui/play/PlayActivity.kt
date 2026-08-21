package com.zyplayer.app.ui.play

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.zyplayer.app.util.M3u8Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class PlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayBinding
    private var player: ExoPlayer? = null
    private var playUrl: String = ""
    private var currentMode: PlayMode = PlayMode.DETECTING
    private var isFullscreen = false

    enum class PlayMode { DETECTING, EXO_PLAYER, WEB_VIEW }

    private val downloadManager by lazy { getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 锁定竖屏，只有点击全屏才横屏
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

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
        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }
        binding.btnDownload.setOnClickListener { startDownload() }

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

        val videoExtensions = listOf(".m3u8", ".mp4", ".ts", ".mkv", ".avi", ".flv", ".webm", ".mov", ".3gp")
        if (videoExtensions.any { lower.contains(it) }) return PlayMode.EXO_PLAYER

        val webPatterns = listOf("/player/", "/play/", "?url=", "?vid=", "?v=", ".html", ".php")
        if (webPatterns.any { lower.contains(it) }) return PlayMode.WEB_VIEW

        return PlayMode.DETECTING
    }

    /** 初始化 ExoPlayer */
    @SuppressLint("SetTextI18n")
    private fun setupExoPlayer() {
        binding.tvLoading.visibility = View.GONE
        binding.webView.visibility = View.GONE
        binding.playerView.visibility = View.VISIBLE
        binding.tvSwitchMode.text = "切换到网页"

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

                // 监听播放错误
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        if (currentMode == PlayMode.DETECTING) {
                            Toast.makeText(this@PlayActivity, "原生播放失败，尝试网页模式", Toast.LENGTH_SHORT).show()
                            switchToWebView()
                        } else {
                            Toast.makeText(this@PlayActivity, "播放失败: ${error.message}", Toast.LENGTH_LONG).show()
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
                url?.let { checkAndSwitchToExoPlayer(it) }
                return true
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

    /** 检查 URL 是否为视频 */
    private fun checkAndSwitchToExoPlayer(url: String) {
        val lower = url.lowercase()
        val videoExtensions = listOf(".m3u8", ".mp4", ".ts", ".mkv")
        if (videoExtensions.any { lower.contains(it) }) {
            playUrl = url
            Toast.makeText(this, "检测到视频地址，切换到原生播放", Toast.LENGTH_SHORT).show()
            setupExoPlayer()
        } else {
            binding.webView.loadUrl(url)
        }
    }

    private fun switchToWebView() {
        setupWebView()
    }

    private fun switchToExoPlayer() {
        currentMode = PlayMode.EXO_PLAYER
        setupExoPlayer()
    }

    /** 全屏/退出全屏切换 */
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) enterFullscreen() else exitFullscreen()
    }

    private fun enterFullscreen() {
        binding.toolbar.visibility = View.GONE
        binding.btnFullscreen.text = "⤡"
        // 强制横屏
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // 隐藏系统栏（沉浸模式）
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    private fun exitFullscreen() {
        binding.toolbar.visibility = View.VISIBLE
        binding.btnFullscreen.text = "⛶"
        // 恢复竖屏
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    /** 使用系统 DownloadManager 下载视频（支持 m3u8 分片下载与合并） */
    private fun startDownload() {
        if (playUrl.isEmpty()) {
            Toast.makeText(this, R.string.play_error, Toast.LENGTH_SHORT).show()
            return
        }
        val lower = playUrl.lowercase()
        val name = intent.getStringExtra(EXTRA_NAME) ?: "video"

        if (lower.contains(".m3u8")) {
            // m3u8 用分片流下载器
            showDownloadProgress(name)
        } else {
            // 直链用系统 DownloadManager
            downloadDirect()
        }
    }

    private fun downloadDirect() {
        try {
            val fileName = (intent.getStringExtra(EXTRA_NAME) ?: "video").replace("/", "_") +
                "_" + System.currentTimeMillis() + ".mp4"
            val request = DownloadManager.Request(Uri.parse(playUrl))
                .setTitle(fileName)
                .setDescription("ZYPlayer 视频下载")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            downloadManager.enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** 显示 m3u8 下载进度对话框，在后台下载所有分片并合并 */
    @SuppressLint("SetTextI18n")
    private fun showDownloadProgress(videoName: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("下载中")
            .setCancelable(false)
            .create()

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.max = 100
        progressBar.progress = 0
        val tvMsg = TextView(this).apply {
            text = "正在解析视频分片..."
            setPadding(40, 20, 40, 10)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tvMsg)
            addView(progressBar)
            setPadding(40, 20, 40, 30)
        }
        dialog.setView(layout)
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "后台下载") { _, _ -> dialog.dismiss() }
        dialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            val result = M3u8Downloader.download(
                context = this@PlayActivity,
                url = playUrl,
                title = videoName,
                onProgress = { done, total ->
                    tvMsg.text = "下载中: $done / $total 分片"
                    progressBar.max = total
                    progressBar.progress = done
                }
            )
            result?.let {
                tvMsg.text = "下载完成: ${it.fileName}\n(${it.fileSize / 1024} KB)"
                Toast.makeText(this@PlayActivity, "下载完成: ${it.fileName}", Toast.LENGTH_LONG).show()
            } ?: run {
                tvMsg.text = "下载失败，请重试"
                Toast.makeText(this@PlayActivity, "下载失败", Toast.LENGTH_SHORT).show()
            }
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "确定") { _, _ -> dialog.dismiss() }
        }
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
        if (isFullscreen) {
            exitFullscreen()
            return
        }
        if (binding.webView.canGoBack() && currentMode == PlayMode.WEB_VIEW) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 保持全屏状态时不显示工具栏
        if (isFullscreen) binding.toolbar.visibility = View.GONE
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