package com.example.adblock.statistics

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Statistics(val adsBlocked: Long = 0, val trackersBlocked: Long = 0, val allowed: Long = 0, val lastBlockedAt: Long = 0)

class StatisticsManager(context: Context) {
    private val prefs = context.getSharedPreferences("statistics", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    init { prefs.registerOnSharedPreferenceChangeListener { _, _ -> mutable.value = read() } }
    fun recordBlocked(domain: String) = update { it.copy(adsBlocked = it.adsBlocked + 1, trackersBlocked = it.trackersBlocked + if (domain.contains("track")) 1 else 0, lastBlockedAt = System.currentTimeMillis()) }
    fun recordAllowed() = update { it.copy(allowed = it.allowed + 1) }
    fun reset() = update { Statistics() }
    private fun update(transform: (Statistics) -> Statistics) { mutable.value = transform(mutable.value); save(mutable.value) }
    private fun read() = Statistics(prefs.getLong("ads", 0), prefs.getLong("trackers", 0), prefs.getLong("allowed", 0), prefs.getLong("last", 0))
    private fun save(s: Statistics) = prefs.edit().putLong("ads", s.adsBlocked).putLong("trackers", s.trackersBlocked).putLong("allowed", s.allowed).putLong("last", s.lastBlockedAt).apply()
}
