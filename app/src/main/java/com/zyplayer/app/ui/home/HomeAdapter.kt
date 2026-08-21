package com.zyplayer.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.zyplayer.app.R
import com.zyplayer.app.data.model.Video
import com.zyplayer.app.databinding.ItemVideoCardBinding

/**
 * 视频宫格适配器（3列）
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
        val binding = ItemVideoCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VideoHolder(private val binding: ItemVideoCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            binding.tvName.text = video.name
            binding.tvNote.text = video.note.ifEmpty { video.year.ifEmpty { video.type } }
            binding.tvSource.text = video.siteName
            binding.imgPoster.load(video.pic) {
                placeholder(R.drawable.ic_placeholder)
                error(R.drawable.ic_placeholder)
                crossfade(true)
            }
            binding.root.setOnClickListener { onClick(video) }
        }
    }
}