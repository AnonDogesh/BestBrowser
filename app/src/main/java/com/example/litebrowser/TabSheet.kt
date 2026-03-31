package com.example.litebrowser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.litebrowser.TabManager.BrowserTab
import com.example.litebrowser.databinding.ItemTabCardBinding
import com.example.litebrowser.databinding.SheetTabsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.UUID

class TabSheet : BottomSheetDialogFragment() {

    interface Callback {
        fun onTabSelected(id: UUID)
        fun onTabClosed(id: UUID)
        fun onNewTabRequested()
    }

    private var _binding: SheetTabsBinding? = null
    private val binding get() = _binding!!

    private val tabAdapter = TabAdapter(
        onSelect = { id ->
            (activity as? Callback)?.onTabSelected(id)
            dismiss()
        },
        onClose = { id ->
            (activity as? Callback)?.onTabClosed(id)
            refreshTabs()
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetTabsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvTabs.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = tabAdapter
            itemAnimator = DefaultItemAnimator().apply {
                removeDuration = 180
            }
        }

        binding.fabAddTab.setOnClickListener {
            (activity as? Callback)?.onNewTabRequested()
            refreshTabs()
        }

        refreshTabs()
    }

    override fun onResume() {
        super.onResume()
        refreshTabs()
    }

    private fun refreshTabs() {
        val activeId = TabManager.getActiveTab()?.id
        tabAdapter.submit(activeId, TabManager.getTabs())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class TabAdapter(
        val onSelect: (UUID) -> Unit,
        val onClose: (UUID) -> Unit
    ) : ListAdapter<BrowserTab, TabAdapter.TabViewHolder>(TabDiff()) {

        private var activeTabId: UUID? = null

        fun submit(activeTabId: UUID?, tabs: List<BrowserTab>) {
            this.activeTabId = activeTabId
            submitList(tabs)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
            val binding = ItemTabCardBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return TabViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
            val tab = getItem(position)
            holder.bind(tab, tab.id == activeTabId, onSelect, onClose)
        }

        class TabViewHolder(private val binding: ItemTabCardBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(tab: BrowserTab, isActive: Boolean, onSelect: (UUID) -> Unit, onClose: (UUID) -> Unit) {
                binding.tvTitle.text = tab.title.ifBlank { tab.url }
                binding.ivFavicon.setImageBitmap(tab.favicon)
                if (tab.favicon == null) {
                    binding.ivFavicon.setImageResource(android.R.drawable.ic_menu_view)
                }

                val strokeColor = if (isActive) {
                    ContextCompat.getColor(binding.root.context, R.color.tab_active_ring)
                } else {
                    ContextCompat.getColor(binding.root.context, android.R.color.transparent)
                }
                binding.root.strokeColor = strokeColor
                binding.root.strokeWidth = if (isActive) 3.dp(binding.root.context) else 1.dp(binding.root.context)

                binding.root.setOnClickListener { onSelect(tab.id) }
                binding.btnCloseTab.setOnClickListener { onClose(tab.id) }
            }

            private fun Int.dp(context: android.content.Context): Int =
                (this * context.resources.displayMetrics.density).toInt()
        }

        private class TabDiff : DiffUtil.ItemCallback<BrowserTab>() {
            override fun areItemsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: BrowserTab, newItem: BrowserTab): Boolean =
                oldItem == newItem
        }
    }
}
