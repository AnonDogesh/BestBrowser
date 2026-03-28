package com.example.litebrowser

import android.net.Uri
import android.os.Bundle
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
        holder.bind(item, onPause, onResume, onCancel, onOpen)
    }
}

private class DownloadVH(private val b: ItemDownloadBinding) : RecyclerView.ViewHolder(b.root) {
    fun bind(
        item: DownloadItem,
        onPause: (DownloadItem) -> Unit,
        onResume: (DownloadItem) -> Unit,
        onCancel: (DownloadItem) -> Unit,
        onOpen: (DownloadItem) -> Unit
    ) {
        b.tvName.text = item.fileName
        b.tvStatus.text = "${item.status} ${item.progress}%"
        b.progress.progress = item.progress
        b.btnPause.setOnClickListener { onPause(item) }
        b.btnResume.setOnClickListener { onResume(item) }
        b.btnCancel.setOnClickListener { onCancel(item) }
        b.root.setOnClickListener { onOpen(item) }
    }
}
