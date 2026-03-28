package com.example.litebrowser

data class FilterRule(
    val pattern: String,
    val isException: Boolean,
    val isDomainRule: Boolean,
    val isRegex: Boolean,
    val domains: List<String>,
    val compiled: Regex?,
    val requiresThirdParty: Boolean? = null
)

data class CosmeticRule(
    val domains: List<String>,
    val selector: String
)

data class ParsedLists(
    val networkRules: List<FilterRule>,
    val cosmeticRules: List<CosmeticRule>
)
