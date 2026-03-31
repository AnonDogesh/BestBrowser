package com.example.litebrowser

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "litebrowser_settings"
    private const val KEY_SAVE_TABS_ON_EXIT = "save_tabs_on_exit"
    private const val KEY_OPEN_LAST_TAB = "open_last_tab"
    private const val KEY_SEARCH_ENGINE = "search_engine"

    enum class SearchEngine(val value: String, val displayName: String, val homeUrl: String, val queryUrlPrefix: String) {
        GOOGLE("google", "Google", "https://www.google.com", "https://www.google.com/search?q="),
        BING("bing", "Bing", "https://www.bing.com", "https://www.bing.com/search?q="),
        DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com", "https://duckduckgo.com/?q="),
        YAHOO("yahoo", "Yahoo", "https://search.yahoo.com", "https://search.yahoo.com/search?p=");

        companion object {
            fun fromValue(value: String?): SearchEngine = entries.firstOrNull { it.value == value } ?: GOOGLE
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun shouldSaveTabsOnExit(context: Context): Boolean = prefs(context).getBoolean(KEY_SAVE_TABS_ON_EXIT, true)

    fun setSaveTabsOnExit(context: Context, save: Boolean) {
        prefs(context).edit().putBoolean(KEY_SAVE_TABS_ON_EXIT, save).apply()
    }

    fun shouldOpenLastTab(context: Context): Boolean = prefs(context).getBoolean(KEY_OPEN_LAST_TAB, true)

    fun setOpenLastTab(context: Context, openLast: Boolean) {
        prefs(context).edit().putBoolean(KEY_OPEN_LAST_TAB, openLast).apply()
    }

    fun getSearchEngine(context: Context): SearchEngine =
        SearchEngine.fromValue(prefs(context).getString(KEY_SEARCH_ENGINE, SearchEngine.GOOGLE.value))

    fun setSearchEngine(context: Context, engine: SearchEngine) {
        prefs(context).edit().putString(KEY_SEARCH_ENGINE, engine.value).apply()
    }
}
