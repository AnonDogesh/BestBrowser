package com.example.litebrowser

import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.litebrowser.databinding.ActivityMainBinding
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var desktopSiteEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupUrlBar()
        setupNavigationButtons()
        setupOverflowMenus()
        setupBackPressHandler()

        loadUrl("https://www.google.com")
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        binding.webView.webViewClient = BrowserWebViewClient()
        binding.webView.webChromeClient = BrowserWebChromeClient()
    }

    private fun setupUrlBar() {
        binding.etUrl.setOnEditorActionListener { _, actionId, event ->
            val isGoAction = actionId == EditorInfo.IME_ACTION_GO
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN

            if (isGoAction || isEnterKey) {
                val input = binding.etUrl.text?.toString().orEmpty().trim()
                if (input.isNotEmpty()) {
                    loadUrl(parseInput(input))
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupNavigationButtons() {
        val goBack = {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }
        val goForward = {
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            }
        }

        binding.btnBack.setOnClickListener { goBack() }
        binding.navBack.setOnClickListener { goBack() }
        binding.btnForward.setOnClickListener { goForward() }
        binding.navForward.setOnClickListener { goForward() }

        binding.navTabs.setOnClickListener {
            Toast.makeText(this, "Tab manager coming soon", Toast.LENGTH_SHORT).show()
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
            MENU_REFRESH -> binding.webView.reload()
            MENU_NEW_TAB -> Toast.makeText(this, "New tab coming soon", Toast.LENGTH_SHORT).show()
            MENU_BOOKMARKS -> Toast.makeText(this, "Bookmarks coming soon", Toast.LENGTH_SHORT).show()
            MENU_SHARE -> shareCurrentUrl()
            MENU_DESKTOP_SITE -> {
                desktopSiteEnabled = !desktopSiteEnabled
                item.isChecked = desktopSiteEnabled
                setDesktopMode(desktopSiteEnabled)
                binding.webView.reload()
            }
        }
    }

    private fun shareCurrentUrl() {
        val url = binding.webView.url ?: binding.etUrl.text?.toString().orEmpty()
        if (url.isBlank()) return

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, url)
        }
        startActivity(android.content.Intent.createChooser(intent, "Share link"))
    }

    private fun setDesktopMode(enabled: Boolean) {
        val settings = binding.webView.settings
        settings.useWideViewPort = enabled
        settings.loadWithOverviewMode = enabled
        settings.userAgentString = if (enabled) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        } else {
            WebSettings.getDefaultUserAgent(this)
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
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
            if (input.startsWith("http://") || input.startsWith("https://")) {
                input
            } else {
                "https://$input"
            }
        } else {
            "https://www.google.com/search?q=${URLEncoder.encode(input, Charsets.UTF_8.name())}"
        }
    }

    private fun loadUrl(url: String) {
        binding.webView.loadUrl(url)
        binding.etUrl.setText(url)
        binding.etUrl.clearFocus()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.etUrl.windowToken, 0)
    }

    private fun updateNavigationState() {
        val canGoBack = binding.webView.canGoBack()
        val canGoForward = binding.webView.canGoForward()

        binding.btnBack.isEnabled = canGoBack
        binding.navBack.isEnabled = canGoBack
        binding.btnForward.isEnabled = canGoForward
        binding.navForward.isEnabled = canGoForward

        binding.navBack.alpha = if (canGoBack) 1f else 0.4f
        binding.navForward.alpha = if (canGoForward) 1f else 0.4f
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = null
            destroy()
        }
        super.onDestroy()
    }

    private inner class BrowserWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return false
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            binding.progressBar.visibility = View.VISIBLE
            binding.etUrl.setText(url.orEmpty())
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            binding.progressBar.visibility = View.GONE
            binding.etUrl.setText(url.orEmpty())
            hideKeyboard()
            updateNavigationState()
        }
    }

    private inner class BrowserWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            binding.progressBar.progress = newProgress
            binding.progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
        }
    }

    companion object {
        private const val MENU_REFRESH = 1
        private const val MENU_NEW_TAB = 2
        private const val MENU_BOOKMARKS = 3
        private const val MENU_SHARE = 4
        private const val MENU_DESKTOP_SITE = 5
    }
}
