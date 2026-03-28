package com.example.litebrowser

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.litebrowser.databinding.ActivityDownloadsBinding
import com.example.litebrowser.databinding.ItemDownloadBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private val adapter = DownloadAdapter(
        onPause = { DownloadCenter.pause(this, it.id) },
        onResume = { DownloadCenter.resume(this, it.id) },
        onCancel = { DownloadCenter.cancel(this, it.id) },
        onDelete = { DownloadCenter.delete(this, it.id) },
        onOpen = { item ->
            if (item.status == DownloadStatus.COMPLETED && item.filePath != null) {
                val file = File(item.filePath)
                if (file.exists() && isImage(file.name)) {
                    ImageViewerFragment.newInstance(Uri.fromFile(file).toString())
                        .show(supportFragmentManager, "viewer")
                }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        DownloadCenter.init(this)

        binding.rvDownloads.layoutManager = LinearLayoutManager(this)
        binding.rvDownloads.adapter = adapter

        lifecycleScope.launch {
            DownloadCenter.downloads.collectLatest { adapter.submit(it) }
        }
    }

    private fun isImage(name: String): Boolean =
        listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif", ".svg").any { name.lowercase().endsWith(it) }
}

private class DownloadAdapter(
    val onPause: (DownloadItem) -> Unit,
    val onResume: (DownloadItem) -> Unit,
    val onCancel: (DownloadItem) -> Unit,
    val onDelete: (DownloadItem) -> Unit,
    val onOpen: (DownloadItem) -> Unit
) : RecyclerView.Adapter<DownloadVH>() {

    private val items = mutableListOf<DownloadItem>()

    fun submit(newItems: List<DownloadItem>) {
        items.clear(); items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): DownloadVH {
        val binding = ItemDownloadBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
        return DownloadVH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: DownloadVH, position: Int) {
        val item = items[position]
        holder.bind(item, onPause, onResume, onCancel, onDelete, onOpen)
    }
}

private class DownloadVH(private val b: ItemDownloadBinding) : RecyclerView.ViewHolder(b.root) {
    fun bind(
        item: DownloadItem,
        onPause: (DownloadItem) -> Unit,
        onResume: (DownloadItem) -> Unit,
        onCancel: (DownloadItem) -> Unit,
        onDelete: (DownloadItem) -> Unit,
        onOpen: (DownloadItem) -> Unit
    ) {
        b.tvName.text = item.fileName
        b.tvStatus.text = when (item.status) {
            DownloadStatus.COMPLETED -> item.status.name
            else -> "${item.status} ${item.progress}%"
        }

        b.progress.progress = item.progress
        b.progress.visibility = if (item.status == DownloadStatus.COMPLETED) View.GONE else View.VISIBLE

        b.btnMore.setOnClickListener { anchor ->
            showMenu(anchor, item, onPause, onResume, onCancel, onDelete)
        }
        b.root.setOnClickListener { onOpen(item) }
    }

    private fun showMenu(
        anchor: View,
        item: DownloadItem,
        onPause: (DownloadItem) -> Unit,
        onResume: (DownloadItem) -> Unit,
        onCancel: (DownloadItem) -> Unit,
        onDelete: (DownloadItem) -> Unit
    ) {
        val popup = PopupMenu(anchor.context, anchor)
        when (item.status) {
            DownloadStatus.PAUSED -> {
                popup.menu.add(Menu.NONE, MENU_RESUME, Menu.NONE, "Resume")
                popup.menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, "Delete")
            }
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                popup.menu.add(Menu.NONE, MENU_PAUSE, Menu.NONE, "Pause")
                popup.menu.add(Menu.NONE, MENU_CANCEL, Menu.NONE, "Cancel")
            }
            DownloadStatus.COMPLETED, DownloadStatus.CANCELED, DownloadStatus.FAILED -> {
                popup.menu.add(Menu.NONE, MENU_DELETE, Menu.NONE, "Delete")
            }
        }

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                MENU_PAUSE -> onPause(item)
                MENU_RESUME -> onResume(item)
                MENU_CANCEL -> onCancel(item)
                MENU_DELETE -> onDelete(item)
            }
            true
        }
        popup.show()
    }

    companion object {
        private const val MENU_PAUSE = 1
        private const val MENU_RESUME = 2
        private const val MENU_CANCEL = 3
        private const val MENU_DELETE = 4
    }
}
