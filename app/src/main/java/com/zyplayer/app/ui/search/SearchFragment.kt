package com.zyplayer.app.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.zyplayer.app.R
import com.zyplayer.app.databinding.FragmentSearchBinding
import com.zyplayer.app.ui.home.HomeAdapter

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SearchViewModel
    private lateinit var adapter: HomeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this).get(SearchViewModel::class.java)

        adapter = HomeAdapter { video ->
            Toast.makeText(requireContext(), video.name, Toast.LENGTH_SHORT).show()
        }
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter

        binding.editSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else false
        }

        binding.btnSearch.setOnClickListener { doSearch() }

        viewModel.results.observe(viewLifecycleOwner) { videos ->
            adapter.submitList(videos)
            binding.tvEmpty.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun doSearch() {
        val keyword = binding.editSearch.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(requireContext(), "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.search(keyword)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}