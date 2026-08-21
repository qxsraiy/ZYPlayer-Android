package com.zyplayer.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zyplayer.app.R
import com.zyplayer.app.data.model.Video
import com.zyplayer.app.databinding.ItemVideoListBinding

/**
 * 视频列表适配器（无图片，纯文本列表，加载快）
 */
class HomeAdapter(
    private val onClick: (Video) -> Unit
) : RecyclerView.Adapter<HomeAdapter.VideoHolder>() {

    private var items: List<Video> = emptyList()

    fun submitList(list: List<Video>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoHolder {
        val binding = ItemVideoListBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VideoHolder(private val binding: ItemVideoListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            binding.tvName.text = video.name
            binding.tvSource.text = video.siteName
            binding.tvNote.text = video.note.ifEmpty { video.year.ifEmpty { video.type } }
            binding.root.setOnClickListener { onClick(video) }
        }
    }
}