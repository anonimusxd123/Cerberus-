package com.example.adblock.filtering

/** Fast, deterministic domain matcher. A rule also matches its subdomains. */
class FilterEngine(
    blocklist: Collection<String> = emptyList(),
    whitelist: Collection<String> = emptyList()
) {
    private val blocked = blocklist.mapNotNull(::normalizeDomain).toHashSet()
    private val allowed = whitelist.mapNotNull(::normalizeDomain).toHashSet()

    fun isBlocked(domain: String?): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        if (matches(normalized, allowed)) return false
        return matches(normalized, blocked)
    }

    fun replaceLists(blocklist: Collection<String>, whitelist: Collection<String>) {
        blocked.apply { clear(); addAll(blocklist.mapNotNull(::normalizeDomain)) }
        allowed.apply { clear(); addAll(whitelist.mapNotNull(::normalizeDomain)) }
    }

    private fun matches(domain: String, rules: Set<String>): Boolean {
        var candidate = domain
        while (true) {
            if (candidate in rules) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    companion object {
        fun normalizeDomain(value: String?): String? {
            val domain = value?.trim()?.trimEnd('.')?.lowercase() ?: return null
            if (domain.isBlank() || domain.length > 253 || domain.contains("..")) return null
            val labels = domain.split('.')
            if (labels.size < 2 || labels.any { it.isBlank() || it.length > 63 || !it.all { c -> c.isLetterOrDigit() || c == '-' } || it.first() == '-' || it.last() == '-' }) return null
            return domain
        }
    }
}
