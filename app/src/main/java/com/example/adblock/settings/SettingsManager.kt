package com.example.adblock.settings

import android.content.Context
import com.example.adblock.filtering.RemoteBlocklistUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(val blockAds: Boolean = true, val blockTrackers: Boolean = true, val startAtBoot: Boolean = false, val autoUpdate: Boolean = false)
class SettingsManager(private val appContext: Context) {
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    fun set(transform: (Settings) -> Settings) {
        val previous = mutable.value
        mutable.value = transform(mutable.value)
        prefs.edit().putBoolean("ads", mutable.value.blockAds).putBoolean("trackers", mutable.value.blockTrackers).putBoolean("boot", mutable.value.startAtBoot).putBoolean("update", mutable.value.autoUpdate).apply()
        if (previous.autoUpdate != mutable.value.autoUpdate) {
            if (mutable.value.autoUpdate) RemoteBlocklistUpdater.schedule(appContext) else RemoteBlocklistUpdater.cancel(appContext)
        }
    }
    private fun read() = Settings(prefs.getBoolean("ads", true), prefs.getBoolean("trackers", true), prefs.getBoolean("boot", false), prefs.getBoolean("update", false))
}
