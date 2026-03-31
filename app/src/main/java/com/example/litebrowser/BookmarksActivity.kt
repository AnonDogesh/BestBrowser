package com.example.litebrowser

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.litebrowser.databinding.ActivityBookmarksBinding
import com.example.litebrowser.databinding.ItemBookmarkBinding

class BookmarksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private val selectedUrls = linkedSetOf<String>()
    private val adapter = BookmarkAdapter(
        onOpen = { bookmark ->
            if (selectedUrls.isNotEmpty()) {
                toggleSelection(bookmark.url)
            } else {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_URL, bookmark.url)
                })
                finish()
            }
        },
        onLongSelect = { bookmark ->
            toggleSelection(bookmark.url)
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnDelete.visibility = View.GONE
        binding.btnDelete.setOnClickListener { confirmDeleteSelected() }
        binding.rvBookmarks.layoutManager = LinearLayoutManager(this)
        binding.rvBookmarks.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadBookmarks()
    }

    private fun toggleSelection(url: String) {
        if (selectedUrls.contains(url)) selectedUrls.remove(url) else selectedUrls.add(url)
        adapter.setSelection(selectedUrls)
        binding.btnDelete.visibility = if (selectedUrls.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun confirmDeleteSelected() {
        if (selectedUrls.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Delete bookmarks")
            .setMessage("Delete ${selectedUrls.size} selected bookmarks?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                BookmarkStore.deleteByUrls(this, selectedUrls)
                selectedUrls.clear()
                binding.btnDelete.visibility = View.GONE
                loadBookmarks()
            }
            .show()
    }

    private fun loadBookmarks() {
        adapter.submit(BookmarkStore.getAll(this))
        adapter.setSelection(selectedUrls)
        binding.tvEmpty.text = if (adapter.itemCount == 0) "No bookmarks yet" else ""
    }

    companion object {
        const val EXTRA_OPEN_URL = "open_url"
    }
}

private class BookmarkAdapter(
    val onOpen: (BookmarkStore.Bookmark) -> Unit,
    val onLongSelect: (BookmarkStore.Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkVH>() {

    private val items = mutableListOf<BookmarkStore.Bookmark>()
    private val selectedUrls = mutableSetOf<String>()

    fun submit(newItems: List<BookmarkStore.Bookmark>) {
        items.clear(); items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setSelection(urls: Set<String>) {
        selectedUrls.clear()
        selectedUrls.addAll(urls)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BookmarkVH {
        val binding = ItemBookmarkBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
        return BookmarkVH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BookmarkVH, position: Int) {
        val item = items[position]
        holder.bind(item, selectedUrls.contains(item.url), onOpen, onLongSelect)
    }
}

private class BookmarkVH(private val b: ItemBookmarkBinding) : RecyclerView.ViewHolder(b.root) {
    fun bind(
        item: BookmarkStore.Bookmark,
        selected: Boolean,
        onOpen: (BookmarkStore.Bookmark) -> Unit,
        onLongSelect: (BookmarkStore.Bookmark) -> Unit
    ) {
        b.tvTitle.text = item.title
        b.tvUrl.text = item.url
        b.root.alpha = if (selected) 0.6f else 1f
        b.root.setOnClickListener { onOpen(item) }
        b.root.setOnLongClickListener {
            onLongSelect(item)
            true
        }
    }
}
