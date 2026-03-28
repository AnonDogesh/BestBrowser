package com.example.litebrowser

object FilterListParser {

    fun parse(raw: String): ParsedLists {
        val network = mutableListOf<FilterRule>()
        val cosmetic = mutableListOf<CosmeticRule>()

        raw.lineSequence().forEach { lineRaw ->
            var line = lineRaw.trim()
            if (line.isBlank()) return@forEach
            if (line.startsWith("!") || line.startsWith("[Adblock")) return@forEach

            val cosmeticRule = parseCosmeticRule(line)
            if (cosmeticRule != null) {
                cosmetic.add(cosmeticRule)
                return@forEach
            }

            val isException = line.startsWith("@@")
            if (isException) line = line.removePrefix("@@")

            val options = line.substringAfter('$', "")
            val base = line.substringBefore('$')
            if (base.isBlank()) return@forEach

            val domains = parseDomainsOption(options)
            val thirdParty = parseThirdPartyOption(options)
            val isDomainRule = base.startsWith("||")
            val isRegex = base.startsWith("/") && base.endsWith("/") && base.length > 2
            val compiled = if (isRegex) runCatching { Regex(base.removeSurrounding("/")) }.getOrNull() else null

            network.add(
                FilterRule(
                    pattern = base,
                    isException = isException,
                    isDomainRule = isDomainRule,
                    isRegex = isRegex,
                    domains = domains,
                    compiled = compiled,
                    requiresThirdParty = thirdParty
                )
            )
        }

        return ParsedLists(network, cosmetic)
    }

    private fun parseCosmeticRule(line: String): CosmeticRule? {
        val token = when {
            line.contains("#?#") -> "#?#"
            line.contains("##") -> "##"
            else -> return null
        }

        val parts = line.split(token, limit = 2)
        if (parts.size != 2) return null

        val domains = parts[0]
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val selector = parts[1].trim()
        if (selector.isBlank()) return null
        return CosmeticRule(domains, selector)
    }

    private fun parseDomainsOption(options: String): List<String> {
        if (options.isBlank()) return emptyList()
        val domainOpt = options.split(',').firstOrNull { it.startsWith("domain=") } ?: return emptyList()
        return domainOpt.removePrefix("domain=")
            .split('|')
            .map { it.trim().removePrefix("~") }
            .filter { it.isNotBlank() }
    }

    private fun parseThirdPartyOption(options: String): Boolean? {
        if (options.isBlank()) return null
        val opts = options.split(',').map { it.trim() }
        return when {
            "third-party" in opts -> true
            "~third-party" in opts -> false
            else -> null
        }
    }
}
