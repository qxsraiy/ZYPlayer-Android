package com.zyplayer.app.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import coil.load
import com.zyplayer.app.R
import com.zyplayer.app.data.model.PlayGroup
import com.zyplayer.app.data.model.PlayItem
import com.zyplayer.app.databinding.ActivityDetailBinding
import com.zyplayer.app.ui.play.PlayActivity
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_NAME
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_SITE_KEY
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_URL
import com.zyplayer.app.ui.play.PlayActivity.Companion.EXTRA_VIDEO_ID

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var viewModel: DetailViewModel
    private var groups: List<PlayGroup> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val siteKey = intent.getStringExtra("siteKey") ?: ""
        val id = intent.getStringExtra("id") ?: ""

        viewModel = ViewModelProvider(this, DetailViewModel.Factory(siteKey, id))
            .get(DetailViewModel::class.java)

        setupEvents()
        observeData()
        viewModel.loadDetail()
    }

    private fun setupEvents() {
        // 选集按钮：点击展开选集弹窗
        binding.btnEpisodes.setOnClickListener {
            if (groups.isNotEmpty()) {
                showEpisodeDialog()
            }
        }
    }

    private fun observeData() {
        viewModel.detail.observe(this) { detail ->
            if (detail == null) {
                Toast.makeText(this, R.string.no_episode, Toast.LENGTH_SHORT).show()
                return@observe
            }
            // 填充信息
            binding.tvName.text = detail.video.name
            binding.tvMeta.text = buildString {
                append(detail.video.type)
                if (detail.video.year.isNotEmpty()) append(" ·  ").append(detail.video.year)
            }
            binding.tvDesc.text = detail.video.desc.take(200)
            binding.imgPoster.load(detail.video.pic) {
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
                crossfade(true)
            }

            groups = detail.groups
            // 默认播放第一线路的第一集
            val firstGroup = detail.groups.firstOrNull()
            if (firstGroup != null && firstGroup.items.isNotEmpty()) {
                val first = firstGroup.items.first()
                binding.tvPlayStatus.text = "${firstGroup.name} · ${first.label}"
                binding.btnPlay.setOnClickListener {
                    startPlay(first.url, first.label, firstGroup.name)
                }
            }
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

    /** 选集/线路弹窗 */
    private fun showEpisodeDialog() {
        val items = mutableListOf<Pair<String, PlayItem>>()
        groups.forEach { group ->
            items.add(Pair("【${group.name}】", PlayItem(group.name, "")))
            group.items.forEach { items.add(Pair(group.name, it)) }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
        dialog.setTitle(R.string.episode)
        val names = items.map { it.first + " " + it.second.label }.toTypedArray()
        dialog.setItems(names) { _, which ->
            val item = items[which].second
            if (item.url.isNotEmpty()) {
                val groupName = items[which].first
                startPlay(item.url, item.label, groupName)
            }
        }
        dialog.show()
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