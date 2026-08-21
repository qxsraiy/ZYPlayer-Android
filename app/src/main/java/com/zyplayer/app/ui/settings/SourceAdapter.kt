package com.zyplayer.app.ui.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zyplayer.app.data.model.Source
import com.zyplayer.app.databinding.ItemSourceBinding

/**
 * 源列表适配器
 * 使用 ListAdapter + DiffUtil：仅刷新变化的项，开关稳定不闪动
 */
class SourceAdapter(
    private val onToggle: (Source) -> Unit,
    private val onDelete: (Source) -> Unit
) : ListAdapter<Source, SourceAdapter.SourceHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceHolder {
        val binding = ItemSourceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SourceHolder(binding)
    }

    override fun onBindViewHolder(holder: SourceHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SourceHolder(private val binding: ItemSourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(source: Source) {
            binding.tvName.text = source.name
            binding.tvApi.text = source.api
            binding.tvStatus.text =
                if (source.enabled) "已启用" else "已禁用"

            // 先清除旧监听器，再设状态，最后设新监听器
            // 避免 RecyclerView 回收复用导致旧监听器触发错误源的切换
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = source.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                // 读取最新的源数据，避免快速操作时用旧值取反导致错乱
                val latest = getItem(bindingAdapterPosition)
                if (isChecked != latest.enabled) {
                    onToggle(latest)
                }
            }
            binding.btnDelete.setOnClickListener { onDelete(source) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Source>() {
            override fun areItemsTheSame(oldItem: Source, newItem: Source): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Source, newItem: Source): Boolean =
                oldItem.enabled == newItem.enabled &&
                    oldItem.name == newItem.name &&
                    oldItem.api == newItem.api &&
                    oldItem.sort == newItem.sort
        }
    }
}