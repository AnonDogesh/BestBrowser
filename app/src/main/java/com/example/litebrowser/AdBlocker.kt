package com.example.litebrowser

import android.content.Context
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AdBlocker {

    private lateinit var matcher: FilterMatcher
    private val baseNetworkRules = mutableListOf<FilterRule>()
    private val cosmeticSelectors = mutableListOf<String>()
    var enabled = true
    private val allowlist = mutableSetOf<String>()

    val REDIRECT_HOSTS: Set<String>
        get() = FilterMatcher.REDIRECT_HOSTS

    fun init(context: Context) {
        val bundled = runCatching {
            context.assets.open("easylists.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("")

        val cached = runCatching { context.filesDir.resolve("easylists_cache.txt").takeIf { it.exists() }?.readText() }
            .getOrNull()
            .orEmpty()

        val raw = if (cached.isNotBlank()) cached else bundled
        val parsed = FilterListParser.parse(raw)

        baseNetworkRules.clear()
        baseNetworkRules.addAll(parsed.networkRules)
        matcher = FilterMatcher(baseNetworkRules)
        cosmeticSelectors.clear()
        cosmeticSelectors.addAll(parsed.cosmeticRules.map { it.selector })

        loadAllowlist(context)
        loadCustomRules(context)
        enabled = context.prefs().getBoolean(KEY_ENABLED, true)
    }

    fun updateFromRemote(context: Context) {
        schedulePeriodic(context)
    }

    fun requestOneTimeUpdate(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<FilterUpdateWorker>().build())
    }

    fun schedulePeriodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "filter_update",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<FilterUpdateWorker>(3, TimeUnit.DAYS).build()
        )
    }

    fun shouldBlock(requestUrl: String, pageUrl: String): Boolean {
        if (!enabled || !::matcher.isInitialized) return false
        if (isAllowlisted(pageUrl)) return false
        return matcher.shouldBlock(requestUrl, pageUrl)
    }

    fun incrementBlockedCount(context: Context) {
        val prefs = context.prefs()
        prefs.edit().putInt(KEY_BLOCKED_COUNT, prefs.getInt(KEY_BLOCKED_COUNT, 0) + 1).apply()
    }

    fun blockedCount(context: Context): Int = context.prefs().getInt(KEY_BLOCKED_COUNT, 0)

    fun getCosmeticCSS(): String? {
        if (!enabled || cosmeticSelectors.isEmpty()) return null
        return cosmeticSelectors.joinToString(",\n") + " { display: none !important; }"
    }

    fun getJsHardening(): String = """
        (function() {
            const noop = () => {};
            const noopObj = new Proxy({}, { get: () => noop, set: () => true });
            ['googletag','adsbygoogle','__cmp','__tcfapi','_gaq','ga','gtag','fbq','_fbq','dataLayer','uetq','ttq','pintrk','twq']
              .forEach(k => { try { Object.defineProperty(window, k, { value: noopObj, writable: false }); } catch(e){} });
            let _userGesture = false;
            document.addEventListener('pointerdown', () => { _userGesture = true; setTimeout(() => _userGesture = false, 500); });
            const _origOpen = window.open.bind(window);
            window.open = function(url, name, features) {
                if (!_userGesture) return null;
                return _origOpen(url, name, features);
            };
            const OVERLAY_SELECTORS = ['[class*="interstitial"]','[class*="overlay"][style*="position: fixed"]','[id*="modal-ad"]','[class*="modal-ad"]','[class*="popup-ad"]','[class*="sticky-ad"]','[data-ad-unit]','[data-google-query-id]','.adsbygoogle','ins.adsbygoogle','#google_ads_iframe_0'];
            function removeOverlays() {
                OVERLAY_SELECTORS.forEach(sel => document.querySelectorAll(sel).forEach(el => el.remove()));
                document.documentElement.style.overflow = '';
                document.body.style.overflow = '';
            }
            document.addEventListener('DOMContentLoaded', removeOverlays);
            const obs = new MutationObserver(removeOverlays);
            document.addEventListener('DOMContentLoaded', () => { if (document.body) obs.observe(document.body, { childList: true, subtree: true }); });
        })();
    """.trimIndent()

    fun toggleAllowlist(context: Context, domain: String) {
        if (domain in allowlist) allowlist.remove(domain) else allowlist.add(domain)
        persistAllowlist(context)
    }

    fun isDomainAllowlisted(domain: String): Boolean = allowlist.any { domain == it || domain.endsWith(".$it") }

    private fun isAllowlisted(pageUrl: String): Boolean {
        val host = Uri.parse(pageUrl).host ?: return false
        return isDomainAllowlisted(host)
    }

    fun setEnabled(context: Context, isEnabled: Boolean) {
        enabled = isEnabled
        context.prefs().edit().putBoolean(KEY_ENABLED, isEnabled).apply()
    }

    fun updateCustomRules(context: Context, customRaw: String) {
        context.prefs().edit().putString(KEY_CUSTOM_RULES, customRaw).apply()
        loadCustomRules(context)
    }

    fun customRules(context: Context): String = context.prefs().getString(KEY_CUSTOM_RULES, "") ?: ""

    private fun loadCustomRules(context: Context) {
        if (!::matcher.isInitialized) return
        val raw = customRules(context)
        if (raw.isBlank()) {
            matcher = FilterMatcher(baseNetworkRules)
            return
        }
        val parsed = FilterListParser.parse(raw)
        matcher = FilterMatcher(baseNetworkRules + parsed.networkRules)
        cosmeticSelectors.addAll(parsed.cosmeticRules.map { it.selector })
    }

    private fun loadAllowlist(context: Context) {
        allowlist.clear()
        allowlist.addAll(context.prefs().getStringSet(KEY_ALLOWLIST, emptySet()) ?: emptySet())
    }

    private fun persistAllowlist(context: Context) {
        context.prefs().edit().putStringSet(KEY_ALLOWLIST, allowlist).apply()
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "adblock_prefs"
    private const val KEY_ALLOWLIST = "allowlist"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BLOCKED_COUNT = "blocked_count"
    private const val KEY_CUSTOM_RULES = "custom_rules"
}
