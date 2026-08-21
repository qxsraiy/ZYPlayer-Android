package com.zyplayer.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zyplayer.app.R
import com.zyplayer.app.databinding.FragmentHomeBinding
import com.zyplayer.app.data.model.Video
import com.zyplayer.app.ui.detail.DetailActivity

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel
    private lateinit var adapter: HomeAdapter
    private var allVideos: List<Video> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this).get(HomeViewModel::class.java)

        setupUi()
        observeData()

        // 首次加载
        viewModel.loadHomeVideos()
    }

    private fun setupUi() {
        adapter = HomeAdapter { video ->
            DetailActivity.start(requireContext(), video)
        }

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerView.adapter = adapter

        // 换一批
        binding.btnRefresh.setOnClickListener {
            viewModel.loadHomeVideos()
        }

        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadHomeVideos()
        }

        // 短视频网格的滚动监听（简单防抖）
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    binding.btnRefresh.visibility = View.GONE
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    binding.btnRefresh.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun observeData() {
        viewModel.videos.observe(viewLifecycleOwner) { videos ->
            if (videos.isNotEmpty()) {
                allVideos = videos
                adapter.submitList(videos)
                binding.tvEmpty.visibility = View.GONE
            } else {
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            binding.swipeRefresh.isRefreshing = loading
            if (loading && adapter.itemCount == 0) {
                binding.tvEmpty.text = getString(R.string.loading)
                binding.tvEmpty.visibility = View.VISIBLE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) {
                binding.tvEmpty.text = msg
                binding.tvEmpty.visibility = View.VISIBLE
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "HomeFragment"
    }
}