package com.example.adblock.filtering

import android.content.Context

/**
 * Per-app include/exclude list for the local VPN tunnel.
 *
 * When an app is left ON (the default) its DNS traffic keeps flowing through Cerberus and is
 * subject to the domain filter. When the user switches an app OFF, that package is added to
 * Android's "disallowed applications" list for the tunnel the next time protection starts, so its
 * traffic bypasses the VPN interface completely (Android only lets a VpnService change this list
 * while the tunnel is being established, not while it is already running).
 */
class AppRuleManager(context: Context) {
    private val prefs = context.getSharedPreferences("app_rules", Context.MODE_PRIVATE)

    fun isProtected(packageName: String): Boolean = prefs.getBoolean(key(packageName), true)

    fun setProtected(packageName: String, protected: Boolean) {
        prefs.edit().putBoolean(key(packageName), protected).apply()
    }

    /** Of [installedPackages], the ones the user explicitly turned OFF. */
    fun excludedPackages(installedPackages: Collection<String>): Set<String> =
        installedPackages.filterNot(::isProtected).toSet()

    private fun key(packageName: String) = "app_$packageName"
}
