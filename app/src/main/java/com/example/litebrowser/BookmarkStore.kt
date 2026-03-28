package com.example.litebrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BookmarkStore {
    private const val PREFS = "bookmarks"
    private const val KEY_LIST = "bookmark_list"

    data class Bookmark(val title: String, val url: String)

    fun getAll(context: Context): List<Bookmark> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LIST, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val title = obj.optString("title").ifBlank { obj.optString("url") }
                    val url = obj.optString("url")
                    if (url.isNotBlank()) add(Bookmark(title = title, url = url))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, title: String, url: String) {
        if (url.isBlank()) return
        val existing = getAll(context).toMutableList()
        if (existing.any { it.url == url }) return
        existing.add(0, Bookmark(title.ifBlank { url }, url))
        persist(context, existing)
    }

    fun deleteAt(context: Context, index: Int) {
        val existing = getAll(context).toMutableList()
        if (index !in existing.indices) return
        existing.removeAt(index)
        persist(context, existing)
    }

    private fun persist(context: Context, bookmarks: List<Bookmark>) {
        val arr = JSONArray()
        bookmarks.forEach {
            arr.put(JSONObject().apply {
                put("title", it.title)
                put("url", it.url)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIST, arr.toString())
            .apply()
    }
}
