package com.zyplayer.app.ui.detail

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.zyplayer.app.R
import com.zyplayer.app.data.local.AppDatabase
import com.zyplayer.app.data.model.Favorite
import com.zyplayer.app.data.model.PlayGroup
import com.zyplayer.app.data.model.PlayItem
import com.zyplayer.app.databinding.ActivityDetailBinding
import com.zyplayer.app.ui.play.PlayActivity
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_NAME
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_SITE_KEY
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_URL
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_VIDEO_ID
import com.zyplayer.app.util.M3u8Downloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var viewModel: DetailViewModel
    private lateinit var episodeAdapter: EpisodeAdapter
    private var groups: List<PlayGroup> = emptyList()
    private var isFavorited = false
    private var currentVideo: com.zyplayer.app.data.model.Video? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val siteKey = intent.getStringExtra("siteKey") ?: ""
        val id = intent.getStringExtra("id") ?: ""

        viewModel = ViewModelProvider(this, DetailViewModel.Factory(siteKey, id))
            .get(DetailViewModel::class.java)

        // 选集网格：每行5个
        episodeAdapter = EpisodeAdapter { item, groupName ->
            startPlay(item.url, item.label, groupName)
        }
        binding.rvEpisodes.layoutManager = GridLayoutManager(this, 5)
        binding.rvEpisodes.adapter = episodeAdapter

        // 按钮事件
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnDownload.setOnClickListener { startDownload() }

        observeData()
        viewModel.loadDetail()
    }

    private fun observeData() {
        viewModel.detail.observe(this) { detail ->
            if (detail == null) {
                Toast.makeText(this, R.string.no_episode, Toast.LENGTH_SHORT).show()
                return@observe
            }
            // 填充信息
            currentVideo = detail.video
            binding.tvName.text = detail.video.name
            binding.tvMeta.text = buildString {
                append(detail.video.type)
                if (detail.video.year.isNotEmpty()) append(" · ").append(detail.video.year)
            }
            binding.tvDesc.text = detail.video.desc.take(200)
            binding.imgPoster.load(detail.video.pic) {
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
                crossfade(true)
            }

            // 检查是否已收藏
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(this@DetailActivity)
                val fav = db.favoriteDao().getFavorite(detail.video.key)
                isFavorited = fav != null
                runOnUiThread { updateFavoriteButton() }
            }

            groups = detail.groups
            val firstGroup = detail.groups.firstOrNull()
            if (firstGroup != null && firstGroup.items.isNotEmpty()) {
                val first = firstGroup.items.first()
                binding.tvPlayStatus.text = "${firstGroup.name} · ${first.label}"
                binding.btnPlay.setOnClickListener {
                    startPlay(first.url, first.label, firstGroup.name)
                }
            }

            // 选集网格
            val episodes = mutableListOf<Pair<PlayItem, String>>()
            groups.forEach { group ->
                group.items.forEach { item ->
                    val label = if (groups.size > 1) "${group.name} ${item.label}" else item.label
                    episodes.add(Pair(item, label))
                }
            }
            episodeAdapter.submitEpisodes(episodes)
        }

        viewModel.error.observe(this) { msg ->
            if (msg.isNotEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.loading.observe(this) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    /** 切换收藏状态 */
    private fun toggleFavorite() {
        val video = currentVideo ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(this@DetailActivity)
            if (isFavorited) {
                db.favoriteDao().deleteByKey(video.key)
                isFavorited = false
            } else {
                db.favoriteDao().insert(Favorite(
                    key = video.key,
                    siteKey = video.siteKey,
                    siteName = video.siteName,
                    id = video.id,
                    name = video.name,
                    pic = video.pic,
                    note = video.note,
                    type = video.type,
                    year = video.year,
                    desc = video.desc
                ))
                isFavorited = true
            }
            runOnUiThread {
                updateFavoriteButton()
                Toast.makeText(
                    this@DetailActivity,
                    if (isFavorited) R.string.favorite_added else R.string.favorite_removed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** 更新收藏按钮文字 */
    private fun updateFavoriteButton() {
        binding.btnFavorite.text = if (isFavorited) "♥ 已收藏" else "♡ 收藏"
    }

    /** 下载视频（支持 m3u8 分片下载与直链下载） */
    private fun startDownload() {
        val video = currentVideo ?: return
        val firstUrl = groups.firstOrNull()?.items?.firstOrNull()?.url ?: run {
            Toast.makeText(this, "暂无播放地址可下载", Toast.LENGTH_SHORT).show()
            return
        }
        if (firstUrl.contains(".m3u8", ignoreCase = true)) {
            // m3u8 用分片流下载器
            showM3u8DownloadProgress(video.name, firstUrl)
        } else {
            // 直链用系统 DownloadManager
            try {
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val fileName = video.name.replace("/", "_") + "_" + System.currentTimeMillis() + ".mp4"
                val request = DownloadManager.Request(Uri.parse(firstUrl))
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
    }

    @SuppressLint("SetTextI18n")
    private fun showM3u8DownloadProgress(videoName: String, url: String) {
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
                context = this@DetailActivity,
                url = url,
                title = videoName,
                onProgress = { done, total ->
                    tvMsg.text = "下载中: $done / $total 分片"
                    progressBar.max = total
                    progressBar.progress = done
                }
            )
            result?.let {
                tvMsg.text = "下载完成: ${it.fileName}\n(${it.fileSize / 1024} KB)"
                Toast.makeText(this@DetailActivity, "下载完成: ${it.fileName}", Toast.LENGTH_LONG).show()
            } ?: run {
                tvMsg.text = "下载失败，请重试"
                Toast.makeText(this@DetailActivity, "下载失败", Toast.LENGTH_SHORT).show()
            }
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, "确定") { _, _ -> dialog.dismiss() }
        }
    }

    private fun startPlay(url: String, label: String, groupName: String) {
        val siteKey = intent.getStringExtra("siteKey") ?: ""
        val videoId = intent.getStringExtra("id") ?: ""
        val name = binding.tvName.text.toString()
        val intent = Intent(this, PlayActivity::class.java).apply {
            putExtra(EXTRA_SITE_KEY, siteKey)
            putExtra(EXTRA_VIDEO_ID, videoId)
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_NAME, "$name - $label")
        }
        startActivity(intent)
    }

    companion object {
        fun start(context: Context, video: com.zyplayer.app.data.model.Video) {
            val intent = Intent(context, DetailActivity::class.java).apply {
                putExtra("siteKey", video.siteKey)
                putExtra("id", video.id)
            }
            context.startActivity(intent)
        }
    }
}

/** 选集网格适配器 */
class EpisodeAdapter(
    private val onClick: (PlayItem, String) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeHolder>() {

    private val episodes = mutableListOf<Pair<PlayItem, String>>()

    fun submitEpisodes(list: List<Pair<PlayItem, String>>) {
        episodes.clear()
        episodes.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode, parent, false)
        return EpisodeHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeHolder, position: Int) {
        val (item, label) = episodes[position]
        holder.tvLabel.text = label
        holder.itemView.setOnClickListener { onClick(item, label) }
    }

    override fun getItemCount(): Int = episodes.size

    inner class EpisodeHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLabel: TextView = view.findViewById(R.id.text)
    }
}