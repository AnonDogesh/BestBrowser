package com.example.litebrowser

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.example.litebrowser.databinding.ActivityMainBinding
import java.net.URLEncoder
import java.util.UUID

class MainActivity : AppCompatActivity(), TabSheet.Callback {

    private lateinit var binding: ActivityMainBinding
    private var desktopSiteEnabled = false
    private val tabWebViews = mutableMapOf<UUID, WebView>()
    private var currentTabId: UUID? = null

    private val currentWebView: WebView?
        get() = currentTabId?.let { tabWebViews[it] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TabManager.initialize(this)
        if (!AppSettings.shouldSaveTabsOnExit(this)) {
            TabManager.clearAll(this)
        }

        setupUrlBar()
        setupNavigationButtons()
        setupOverflowMenus()
        setupBackPressHandler()

        runCatching { AdBlocker.init(this) }
        runCatching { AdBlocker.updateFromRemote(this) }

        val startupTab = if (AppSettings.shouldOpenLastTab(this) && TabManager.getTabs().isNotEmpty()) {
            TabManager.getActiveTab()
        } else {
            null
        } ?: TabManager.newTab(getDefaultHomeUrl())

        switchToTab(startupTab.id)
    }

    private fun createWebViewForTab(tabId: UUID): WebView {
        return WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            webViewClient = BrowserWebViewClient(tabId)
            webChromeClient = BrowserWebChromeClient(tabId)
        }
    }

    private fun setupUrlBar() {
        setupUrlClearAffordance()

        binding.etUrl.setOnEditorActionListener { _, actionId, event ->
            val isGoAction = actionId == EditorInfo.IME_ACTION_GO
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN

            if (isGoAction || isEnterKey) {
                val input = binding.etUrl.text?.toString().orEmpty().trim()
                if (input.isNotEmpty()) {
                    currentWebView?.loadUrl(parseInput(input))
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupUrlClearAffordance() {
        val clearIcon = android.R.drawable.ic_menu_close_clear_cancel

        val updateClearIcon = {
            val shouldShow = binding.etUrl.hasFocus() && !binding.etUrl.text.isNullOrEmpty()
            binding.etUrl.setCompoundDrawablesWithIntrinsicBounds(0, 0, if (shouldShow) clearIcon else 0, 0)
        }

        binding.etUrl.setOnFocusChangeListener { _, _ -> updateClearIcon() }
        binding.etUrl.doOnTextChanged { _, _, _, _ -> updateClearIcon() }

        binding.etUrl.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && binding.etUrl.compoundDrawables[2] != null) {
                val drawableWidth = binding.etUrl.compoundDrawables[2].bounds.width()
                val isTappedOnEnd = event.x >= (binding.etUrl.width - binding.etUrl.paddingEnd - drawableWidth)
                if (isTappedOnEnd) {
                    binding.etUrl.text?.clear()
                    updateClearIcon()
                    return@setOnTouchListener true
                }
            }
            false
        }

        updateClearIcon()
    }

    private fun setupNavigationButtons() {
        binding.navBack.setOnClickListener {
            currentWebView?.takeIf { webView -> webView.canGoBack() }?.goBack()
        }
        binding.navForward.setOnClickListener {
            currentWebView?.takeIf { webView -> webView.canGoForward() }?.goForward()
        }

        binding.navTabs.setOnClickListener {
            TabSheet().show(supportFragmentManager, TAB_SHEET_TAG)
        }

        updateNavigationState()
    }

    private fun setupOverflowMenus() {
        val showMenu = { anchor: View ->
            val popupMenu = PopupMenu(this, anchor)
            popupMenu.menu.apply {
                add(Menu.NONE, MENU_REFRESH, Menu.NONE, "Refresh")
                add(Menu.NONE, MENU_NEW_TAB, Menu.NONE, "New Tab")
                add(Menu.NONE, MENU_BOOKMARKS, Menu.NONE, "Bookmarks")
                add(Menu.NONE, MENU_SHARE, Menu.NONE, "Share")
                add(Menu.NONE, MENU_SETTINGS, Menu.NONE, "Settings")
                add(Menu.NONE, MENU_DESKTOP_SITE, Menu.NONE, "Desktop Site").apply {
                    isCheckable = true
                    isChecked = desktopSiteEnabled
                }
            }

            popupMenu.setOnMenuItemClickListener { item ->
                handleMenuItem(item)
                true
            }
            popupMenu.show()
        }

        binding.btnOverflow.setOnClickListener { showMenu(it) }
        binding.navMenu.setOnClickListener { showMenu(it) }
    }

    private fun handleMenuItem(item: MenuItem) {
        when (item.itemId) {
            MENU_REFRESH -> currentWebView?.reload()
            MENU_NEW_TAB -> onNewTabRequested()
            MENU_BOOKMARKS -> Toast.makeText(this, "Bookmarks coming soon", Toast.LENGTH_SHORT).show()
            MENU_SHARE -> shareCurrentUrl()
            MENU_SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java))
            MENU_DESKTOP_SITE -> {
                desktopSiteEnabled = !desktopSiteEnabled
                item.isChecked = desktopSiteEnabled
                tabWebViews.values.forEach { setDesktopMode(it, desktopSiteEnabled) }
                currentWebView?.reload()
            }
        }
    }

    private fun shareCurrentUrl() {
        val url = currentWebView?.url ?: binding.etUrl.text?.toString().orEmpty()
        if (url.isBlank()) return

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "Share link"))
    }

    private fun setDesktopMode(webView: WebView, enabled: Boolean) {
        webView.settings.apply {
            useWideViewPort = enabled
            loadWithOverviewMode = enabled
            userAgentString = if (enabled) {
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            } else {
                WebSettings.getDefaultUserAgent(this@MainActivity)
            }
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val webView = currentWebView
                if (webView != null && webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun parseInput(input: String): String {
        val isLikelyUrl = input.contains(".") && !input.contains(" ")
        return if (isLikelyUrl) {
            if (input.startsWith("http://") || input.startsWith("https://")) input else "https://$input"
        } else {
            "${AppSettings.getSearchEngine(this).queryUrlPrefix}${URLEncoder.encode(input, Charsets.UTF_8.name())}"
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.etUrl.windowToken, 0)
    }

    private fun switchToTab(tabId: UUID) {
        val tab = TabManager.getTabs().firstOrNull { it.id == tabId } ?: return
        TabManager.switchTo(tabId)

        val webView = tabWebViews.getOrPut(tabId) {
            createWebViewForTab(tabId).also {
                setDesktopMode(it, desktopSiteEnabled)
            }
        }

        binding.webContainer.removeAllViews()
        (webView.parent as? ViewGroup)?.removeView(webView)
        binding.webContainer.addView(webView)

        currentTabId = tabId

        if (webView.url.isNullOrBlank()) {
            webView.loadUrl(tab.url.ifBlank { getDefaultHomeUrl() })
        } else {
            binding.etUrl.setText(webView.url)
            updateNavigationState()
        }

        updateTabCount()
        TabManager.persist(this)
    }

    private fun updateNavigationState() {
        val webView = currentWebView
        val canGoBack = webView?.canGoBack() == true
        val canGoForward = webView?.canGoForward() == true

        binding.navBack.isEnabled = canGoBack
        binding.navForward.isEnabled = canGoForward
        binding.navBack.alpha = if (canGoBack) 1f else 0.4f
        binding.navForward.alpha = if (canGoForward) 1f else 0.4f
    }

    private fun updateTabCount() {
        binding.tabCount.text = TabManager.getTabs().size.toString()
    }

    override fun onTabSelected(id: UUID) {
        switchToTab(id)
    }

    override fun onTabClosed(id: UUID) {
        val wasActive = currentTabId == id

        tabWebViews.remove(id)?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }

        TabManager.closeTab(id)
        if (TabManager.getTabs().isEmpty()) {
            val created = TabManager.newTab(getDefaultHomeUrl())
            switchToTab(created.id)
            return
        }

        if (wasActive) {
            val activeTab = TabManager.getActiveTab() ?: return
            switchToTab(activeTab.id)
        } else {
            updateTabCount()
            TabManager.persist(this)
        }
    }

    override fun onNewTabRequested() {
        val tab = TabManager.newTab(getDefaultHomeUrl())
        switchToTab(tab.id)
        Toast.makeText(this, "New tab opened", Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        super.onStop()
        if (AppSettings.shouldSaveTabsOnExit(this)) {
            TabManager.persist(this)
        } else {
            TabManager.clearAll(this)
        }
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        tabWebViews.values.forEach { webView ->
            webView.stopLoading()
            webView.destroy()
        }
        tabWebViews.clear()
        super.onDestroy()
    }

    private inner class BrowserWebViewClient(private val tabId: UUID) : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
            return try {
                val reqUrl = request.url.toString()
                val pageUrl = view.url ?: ""
                if (AdBlocker.shouldBlock(reqUrl, pageUrl)) {
                    AdBlocker.incrementBlockedCount(this@MainActivity)
                    android.webkit.WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        java.io.ByteArrayInputStream(ByteArray(0))
                    )
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            } catch (_: Exception) {
                super.shouldInterceptRequest(view, request)
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            val host = uri.host ?: return false
            if (AdBlocker.REDIRECT_HOSTS.any { host.contains(it) }) {
                val dest = uri.getQueryParameter("url")
                    ?: uri.getQueryParameter("adurl")
                    ?: uri.getQueryParameter("q")
                if (dest != null) {
                    view.loadUrl(dest)
                    return true
                }
                return true
            }
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val tab = TabManager.getTabs().firstOrNull { it.id == tabId } ?: return
            tab.url = url.orEmpty()
            if (tabId == currentTabId) {
                binding.progressBar.visibility = View.VISIBLE
                binding.etUrl.setText(url.orEmpty())
            }
            TabManager.persist(this@MainActivity)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val tab = TabManager.getTabs().firstOrNull { it.id == tabId } ?: return
            tab.url = url.orEmpty()
            tab.title = view?.title.orEmpty().ifBlank { tab.url }
            tab.favicon = view?.favicon

            if (tabId == currentTabId) {
                binding.progressBar.visibility = View.GONE
                binding.etUrl.setText(url.orEmpty())
                hideKeyboard()
                updateNavigationState()
            }
            getSharedPreferences("adblock_prefs", MODE_PRIVATE).edit().putString("last_page_url", url.orEmpty()).apply()

            AdBlocker.getCosmeticCSS()?.let { css ->
                val escaped = css
                    .replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                view?.evaluateJavascript(
                    """
                    (function(){
                        var s = document.createElement('style');
                        s.textContent = '$escaped';
                        document.head.appendChild(s);
                    })();
                    """.trimIndent(),
                    null
                )
            }
            view?.evaluateJavascript(AdBlocker.getJsHardening(), null)
            TabManager.persist(this@MainActivity)
        }
    }

    private inner class BrowserWebChromeClient(private val tabId: UUID) : WebChromeClient() {
        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?
        ): Boolean {
            if (!isUserGesture) return false
            return false
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (tabId == currentTabId) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            super.onReceivedTitle(view, title)
            val tab = TabManager.getTabs().firstOrNull { it.id == tabId } ?: return
            if (!title.isNullOrBlank()) tab.title = title
            TabManager.persist(this@MainActivity)
        }

        override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
            super.onReceivedIcon(view, icon)
            val tab = TabManager.getTabs().firstOrNull { it.id == tabId } ?: return
            tab.favicon = icon
        }
    }


    private fun getDefaultHomeUrl(): String = AppSettings.getSearchEngine(this).homeUrl


    companion object {
        private const val MENU_REFRESH = 1
        private const val MENU_NEW_TAB = 2
        private const val MENU_BOOKMARKS = 3
        private const val MENU_SHARE = 4
        private const val MENU_SETTINGS = 5
        private const val MENU_DESKTOP_SITE = 6
        private const val TAB_SHEET_TAG = "tab_sheet"
    }
}
