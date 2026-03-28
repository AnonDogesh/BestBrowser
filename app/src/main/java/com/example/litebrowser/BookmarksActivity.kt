package com.example.litebrowser

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.litebrowser.databinding.ActivityBookmarksBinding
import com.example.litebrowser.databinding.ItemBookmarkBinding

class BookmarksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private val adapter = BookmarkAdapter(
        onOpen = { bookmark ->
            startActivity(Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_URL, bookmark.url)
            })
            finish()
        },
        onLongDelete = { _, position ->
            AlertDialog.Builder(this)
                .setTitle("Delete bookmark")
                .setMessage("Delete this bookmark?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->
                    BookmarkStore.deleteAt(this, position)
                    loadBookmarks()
                }
                .show()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.rvBookmarks.layoutManager = LinearLayoutManager(this)
        binding.rvBookmarks.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadBookmarks()
    }

    private fun loadBookmarks() {
        adapter.submit(BookmarkStore.getAll(this))
        binding.tvEmpty.text = if (adapter.itemCount == 0) "No bookmarks yet" else ""
    }

    companion object {
        const val EXTRA_OPEN_URL = "open_url"
    }
}

private class BookmarkAdapter(
    val onOpen: (BookmarkStore.Bookmark) -> Unit,
    val onLongDelete: (BookmarkStore.Bookmark, Int) -> Unit
) : RecyclerView.Adapter<BookmarkVH>() {

    private val items = mutableListOf<BookmarkStore.Bookmark>()

    fun submit(newItems: List<BookmarkStore.Bookmark>) {
        items.clear(); items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): BookmarkVH {
        val binding = ItemBookmarkBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
        return BookmarkVH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: BookmarkVH, position: Int) {
        holder.bind(items[position], position, onOpen, onLongDelete)
    }
}

private class BookmarkVH(private val b: ItemBookmarkBinding) : RecyclerView.ViewHolder(b.root) {
    fun bind(
        item: BookmarkStore.Bookmark,
        position: Int,
        onOpen: (BookmarkStore.Bookmark) -> Unit,
        onLongDelete: (BookmarkStore.Bookmark, Int) -> Unit
    ) {
        b.tvTitle.text = item.title
        b.tvUrl.text = item.url
        b.root.setOnClickListener { onOpen(item) }
        b.root.setOnLongClickListener {
            onLongDelete(item, position)
            true
        }
    }
}
