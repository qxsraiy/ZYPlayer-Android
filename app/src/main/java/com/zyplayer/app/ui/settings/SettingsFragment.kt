package com.zyplayer.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.zyplayer.app.R
import com.zyplayer.app.data.model.Source
import com.zyplayer.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private lateinit var adapter: SourceAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        adapter = SourceAdapter(
            onToggle = { source -> viewModel.toggleSource(source) },
            onEdit = { source -> showEditDialog(source) },
            onDelete = { source ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_source)
                    .setMessage(R.string.delete_confirm)
                    .setPositiveButton(R.string.confirm) { _, _ -> viewModel.deleteSource(source) }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnAddSource.setOnClickListener { showAddDialog() }
        binding.btnClearCache.setOnClickListener {
            viewModel.clearCache()
            Toast.makeText(requireContext(), R.string.cache_cleared, Toast.LENGTH_SHORT).show()
        }

        viewModel.sources.observe(viewLifecycleOwner) { sources ->
            adapter.submitList(sources)
            binding.tvEmpty.visibility =
                if (sources.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_source, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_source_name)
        val etApi = dialogView.findViewById<TextInputEditText>(R.id.et_source_api)
        val tilApi = dialogView.findViewById<TextInputLayout>(R.id.til_source_api)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_source)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                val api = etApi.text?.toString()?.trim().orEmpty()
                if (name.isEmpty() || api.isEmpty()) {
                    Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (!api.startsWith("http")) {
                    tilApi.error = "API 地址需以 http 开头"
                    return@setPositiveButton
                }
                viewModel.addSource(name, api)
                Toast.makeText(requireContext(), R.string.save, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(source: Source) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_source, null)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.et_edit_name)
        val etApi = dialogView.findViewById<android.widget.EditText>(R.id.et_edit_api)
        etName.setText(source.name)
        etApi.setText(source.api)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑源")
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = etName.text?.toString()?.trim().orEmpty()
                val newApi = etApi.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty() || newApi.isEmpty()) {
                    Toast.makeText(requireContext(), "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.updateSource(source.copy(name = newName, api = newApi))
                Toast.makeText(requireContext(), "已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}