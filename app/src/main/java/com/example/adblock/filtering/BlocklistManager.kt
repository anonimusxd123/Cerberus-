package com.example.adblock.filtering

import android.content.Context

/** Local-only rule storage. Network sources are intentionally not bundled yet. */
class BlocklistManager(context: Context) {
    private val preferences = context.getSharedPreferences("blocklists", Context.MODE_PRIVATE)
    val defaultRules = setOf("ads.example.com", "tracker.example.com", "doubleclick.net", "googlesyndication.com")

    fun blockedDomains(): Set<String> = defaultRules + preferences.getStringSet("manual_block", emptySet()).orEmpty()
    fun whitelist(): Set<String> = preferences.getStringSet("whitelist", emptySet()).orEmpty()
    fun addBlocked(domain: String): Boolean = add("manual_block", domain)
    fun addAllowed(domain: String): Boolean = add("whitelist", domain)
    fun lastUpdated(): Long = preferences.getLong("updated", 0)

    private fun add(key: String, domain: String): Boolean {
        val normalized = FilterEngine.normalizeDomain(domain) ?: return false
        val next = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        next += normalized
        preferences.edit().putStringSet(key, next).putLong("updated", System.currentTimeMillis()).apply()
        return true
    }
}
