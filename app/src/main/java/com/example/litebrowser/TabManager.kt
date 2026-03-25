package com.example.litebrowser

import android.graphics.Bitmap
import java.util.UUID

object TabManager {
    data class BrowserTab(
        val id: UUID,
        var url: String,
        var title: String,
        var favicon: Bitmap?
    )

    private val tabs = mutableListOf<BrowserTab>()
    var activeTabIndex: Int = 0
        private set

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
