package com.example.litebrowser

import android.net.Uri
import java.security.MessageDigest

class FilterMatcher(rules: List<FilterRule>) {

    companion object {
        val REDIRECT_HOSTS = setOf(
            "googleadservices.com", "doubleclick.net", "ad.doubleclick.net",
            "googleads.g.doubleclick.net", "pagead2.googlesyndication.com",
            "adclick.g.doubleclick.net", "aclk", "redirect.viglink.com",
            "go.redirectingat.com", "track.adform.net", "clk.tradedoubler.com",
            "click.linksynergy.com", "shareasale.com/r.cfm",
            "prf.hn", "skim.gs", "goto.target.com"
        )
    }

    private val allRules = rules
    private val buckets = HashMap<String, MutableList<FilterRule>>()

    init {
        rules.forEach { rule ->
            val key = keyForRule(rule)
            buckets.getOrPut(key) { mutableListOf() }.add(rule)
        }
    }

    fun shouldBlock(requestUrl: String, pageUrl: String): Boolean = runCatching {
        val requestHost = hostOf(requestUrl) ?: return@runCatching false
        val pageHost = hostOf(pageUrl).orEmpty()
        val key = hash6(requestHost)

        val candidates = buildList {
            addAll(buckets[key].orEmpty())
            addAll(buckets["global"].orEmpty())
        }

        val exceptions = candidates.filter { it.isException }
        if (exceptions.any { matchesRule(it, requestUrl, requestHost, pageHost) }) return@runCatching false

        val blockRules = candidates.filterNot { it.isException }
        blockRules.any { matchesRule(it, requestUrl, requestHost, pageHost) }
    }.getOrDefault(false)

    private fun matchesRule(rule: FilterRule, requestUrl: String, requestHost: String, pageHost: String): Boolean {
        if (rule.domains.isNotEmpty() && rule.domains.none { pageHost == it || pageHost.endsWith(".$it") }) {
            return false
        }

        rule.requiresThirdParty?.let { needThird ->
            val isThirdParty = requestHost != pageHost && !requestHost.endsWith(".$pageHost")
            if (needThird != isThirdParty) return false
        }

        return when {
            rule.isDomainRule -> {
                val domain = rule.pattern.removePrefix("||").substringBefore('^').substringBefore('/').removePrefix("*.")
                requestHost == domain || requestHost.endsWith(".$domain")
            }
            rule.isRegex -> rule.compiled?.containsMatchIn(requestUrl) == true
            else -> wildcardMatch(rule.pattern, requestUrl)
        }
    }

    private fun keyForRule(rule: FilterRule): String {
        return when {
            rule.isDomainRule -> {
                val host = rule.pattern.removePrefix("||").substringBefore('^').substringBefore('/').removePrefix("*.")
                if (host.isBlank()) "global" else hash6(host)
            }
            else -> "global"
        }
    }

    private fun wildcardMatch(pattern: String, value: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("^", "[^A-Za-z0-9_\\-.%]")
            .replace("*", ".*")
        return runCatching { Regex(regex).containsMatchIn(value) }.getOrDefault(false)
    }

    private fun hostOf(url: String): String? = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()

    private fun hash6(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.take(3).joinToString("") { "%02x".format(it) }
    }
}
