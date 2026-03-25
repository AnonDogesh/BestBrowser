package com.example.litebrowser

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object TabManager {
    data class BrowserTab(
        val id: UUID,
        var url: String,
        var title: String,
        var favicon: Bitmap?
    )

    private const val PREFS_NAME = "litebrowser_tabs"
    private const val KEY_TABS = "tabs"
    private const val KEY_ACTIVE_INDEX = "active_index"

    private val tabs = mutableListOf<BrowserTab>()
    var activeTabIndex: Int = 0
        private set

    fun initialize(context: Context) {
        if (tabs.isNotEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tabsJson = prefs.getString(KEY_TABS, null)
        val storedActiveIndex = prefs.getInt(KEY_ACTIVE_INDEX, 0)

        if (tabsJson.isNullOrBlank()) return

        runCatching {
            val jsonArray = JSONArray(tabsJson)
            for (i in 0 until jsonArray.length()) {
                val tabJson = jsonArray.getJSONObject(i)
                tabs.add(
                    BrowserTab(
                        id = UUID.fromString(tabJson.getString("id")),
                        url = tabJson.getString("url"),
                        title = tabJson.optString("title", tabJson.getString("url")),
                        favicon = null
                    )
                )
            }
        }.onFailure {
            tabs.clear()
        }

        activeTabIndex = storedActiveIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    }

    fun persist(context: Context) {
        val jsonArray = JSONArray()
        tabs.forEach { tab ->
            jsonArray.put(
                JSONObject().apply {
                    put("id", tab.id.toString())
                    put("url", tab.url)
                    put("title", tab.title)
                }
            )
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TABS, jsonArray.toString())
            .putInt(KEY_ACTIVE_INDEX, activeTabIndex)
            .apply()
    }

    fun getTabs(): List<BrowserTab> = tabs.toList()

    fun getActiveTab(): BrowserTab? = tabs.getOrNull(activeTabIndex)

    fun newTab(url: String): BrowserTab {
        val tab = BrowserTab(
            id = UUID.randomUUID(),
            url = url,
            title = url,
            favicon = null
        )
        tabs.add(tab)
        activeTabIndex = tabs.lastIndex
        return tab
    }

    fun closeTab(id: UUID) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return

        tabs.removeAt(index)

        if (tabs.isEmpty()) {
            activeTabIndex = 0
            return
        }

        activeTabIndex = when {
            activeTabIndex > index -> activeTabIndex - 1
            activeTabIndex >= tabs.size -> tabs.lastIndex
            else -> activeTabIndex
        }
    }

    fun switchTo(id: UUID): BrowserTab {
        val index = tabs.indexOfFirst { it.id == id }
        require(index != -1) { "Tab not found: $id" }
        activeTabIndex = index
        return tabs[index]
    }
}
