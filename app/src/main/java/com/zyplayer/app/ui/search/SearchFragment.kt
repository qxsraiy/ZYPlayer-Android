package com.zyplayer.app.ui.search

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.zyplayer.app.R
import com.zyplayer.app.databinding.FragmentSearchBinding
import com.zyplayer.app.ui.detail.DetailActivity
import com.zyplayer.app.ui.home.HomeAdapter

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: HomeAdapter

    // 搜索历史
    private val searchHistory = mutableListOf<String>()
    private val MAX_HISTORY = 10

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this).get(SearchViewModel::class.java)

        // 点击跳转详情页（核心修复）
        adapter = HomeAdapter { video ->
            DetailActivity.start(requireContext(), video)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // 加载搜索历史
        loadSearchHistory()

        // 回车键搜索
        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        // 点击搜索按钮
        binding.btnSearch.setOnClickListener { doSearch() }

        // 观察搜索进度
        viewModel.progress.observe(viewLifecycleOwner) { text ->
            if (text.isNotEmpty()) {
                binding.tvProgress.text = text
                binding.tvProgress.visibility = View.VISIBLE
            } else {
                binding.tvProgress.visibility = View.GONE
            }
        }

        // 观察"显示更多"按钮
        viewModel.showLoadMore.observe(viewLifecycleOwner) { show ->
            binding.btnLoadMore.visibility = if (show) View.VISIBLE else View.GONE
        }
        binding.btnLoadMore.setOnClickListener { viewModel.loadMore() }

        // 观察搜索结果
        viewModel.results.observe(viewLifecycleOwner) { videos ->
            adapter.submitList(videos)
            if (videos.isNotEmpty()) {
                binding.tvEmpty.visibility = View.GONE
                binding.tvEmpty.text = getString(R.string.no_data)
            } else {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = if (viewModel.isSearched) getString(R.string.no_data) else ""
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (loading && adapter.itemCount == 0) {
                binding.tvEmpty.text = getString(R.string.loading)
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        // 点击搜索历史标签
        // 用 OnClickListener 在 updateTags 里设置
    }

    private fun doSearch() {
        val keyword = binding.editSearch.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(requireContext(), R.string.input_search_hint, Toast.LENGTH_SHORT).show()
            return
        }
        // 保存搜索历史
        saveSearchHistory(keyword)
        // 隐藏标签
        binding.tagGroup.visibility = View.GONE
        binding.tvHistoryLabel.visibility = View.GONE
        // 重置旧结果
        adapter.submitList(emptyList())
        binding.btnLoadMore.visibility = View.GONE
        // 执行搜索
        viewModel.search(keyword)
    }

    /** 加载搜索历史 */
    private fun loadSearchHistory() {
        val prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
        val history = prefs.getString("history", "") ?: ""
        searchHistory.clear()
        if (history.isNotEmpty()) {
            searchHistory.addAll(history.split(",").filter { it.isNotEmpty() })
        }
        updateTags()
    }

    /** 保存搜索历史 */
    private fun saveSearchHistory(keyword: String) {
        searchHistory.remove(keyword) // 去重，移到最前
        searchHistory.add(0, keyword)
        if (searchHistory.size > MAX_HISTORY) {
            searchHistory.removeAt(searchHistory.lastIndex)
        }
        // 持久化
        val prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("history", searchHistory.joinToString(",")).apply()
        updateTags()
    }

    /** 删除单条搜索历史 */
    private fun deleteSearchHistory(keyword: String) {
        searchHistory.remove(keyword)
        val prefs = requireContext().getSharedPreferences("search_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("history", searchHistory.joinToString(",")).apply()
        updateTags()
    }

    /** 更新标签（无论是否为空都刷新 UI） */
    private fun updateTags() {
        binding.tagGroup.removeAllViews()

        if (searchHistory.isEmpty()) {
            binding.tagGroup.visibility = View.GONE
            binding.tvHistoryLabel.visibility = View.GONE
            return
        }

        binding.tagGroup.visibility = View.VISIBLE
        binding.tvHistoryLabel.visibility = View.VISIBLE
        val recent = searchHistory.take(8)
        for (tag in recent) {
            val chip = Chip(requireContext()).apply {
                text = tag
                isClickable = true
                isCheckable = false
                isCloseIconVisible = true
                setOnClickListener {
                    binding.editSearch.setText(tag)
                    binding.editSearch.setSelection(tag.length)
                    doSearch()
                }
                // 点 X 删除单条历史
                setOnCloseIconClickListener {
                    deleteSearchHistory(tag)
                    Toast.makeText(requireContext(), "已删除搜索词", Toast.LENGTH_SHORT).show()
                }
            }
            binding.tagGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}