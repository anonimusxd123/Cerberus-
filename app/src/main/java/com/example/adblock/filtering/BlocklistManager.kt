package com.example.adblock.filtering

import android.content.Context

/**
 * Local rule storage. `defaultRules` ships curated, hand-picked domains so the app is useful
 * offline out of the box. `remoteRules` is populated by [RemoteBlocklistUpdater] from public
 * blocklist sources and refreshed periodically when the user enables "Actualización automática".
 */
class BlocklistManager(context: Context) {
    private val preferences = context.getSharedPreferences("blocklists", Context.MODE_PRIVATE)

    /** Núcleo de publicidad y rastreo genérico (redes de anuncios y analítica más comunes). */
    private val coreAdsAndTrackers = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
        "adservice.google.com", "adnxs.com", "adsafeprotected.com", "moatads.com", "criteo.com",
        "criteo.net", "outbrain.com", "taboola.com", "scorecardresearch.com", "amazon-adsystem.com",
        "mopub.com", "applovin.com", "unityads.unity3d.com", "app-measurement.com", "crashlytics.com",
        "appsflyer.com", "adjust.com", "branch.io", "flurry.com", "mixpanel.com", "segment.io",
        "smartadserver.com", "pubmatic.com", "rubiconproject.com", "openx.net", "casalemedia.com"
    )

    /**
     * Endpoints de anuncios/telemetría de YouTube que son *dominios propios*, distintos del CDN
     * de vídeo (googlevideo.com). IMPORTANTE: los anuncios insertados dentro del propio stream de
     * vídeo se sirven desde el mismo dominio que el contenido, así que un bloqueo DNS no puede
     * eliminarlos sin romper la reproducción; esto es una limitación de cualquier bloqueador basado
     * en DNS, no solo de esta app.
     */
    private val youtubeAggressive = setOf(
        "googleads.g.doubleclick.net", "static.doubleclick.net", "pagead2.googlesyndication.com",
        "s.youtube.com"
    )

    /** Telemetría y red de anuncios de Meta que no forman parte del login/feed (no rompen la app). */
    private val facebookAggressive = setOf(
        "an.facebook.com", "connect.facebook.net", "graph.video.facebook.com"
    )

    /** Redes de anuncios habituales en apps de juegos y de streaming de vídeo/audio. */
    private val streamingAndGamingAggressive = setOf(
        "vungle.com", "chartboost.com", "ironsrc.com", "supersonicads.com", "tapjoy.com",
        "adcolony.com", "startapp.com", "inmobi.com", "smaato.com", "pangleglobal.com"
    )

    val defaultRules: Set<String>
        get() {
            val categories = mutableSetOf<String>()
            categories += coreAdsAndTrackers
            if (isCategoryEnabled("cat_youtube", true)) categories += youtubeAggressive
            if (isCategoryEnabled("cat_facebook", true)) categories += facebookAggressive
            if (isCategoryEnabled("cat_streaming", true)) categories += streamingAndGamingAggressive
            return categories
        }

    fun blockedDomains(): Set<String> =
        defaultRules + remoteRules() + preferences.getStringSet("manual_block", emptySet()).orEmpty()

    fun whitelist(): Set<String> = preferences.getStringSet("whitelist", emptySet()).orEmpty()
    fun addBlocked(domain: String): Boolean = add("manual_block", domain)
    fun addAllowed(domain: String): Boolean = add("whitelist", domain)
    fun lastUpdated(): Long = preferences.getLong("updated", 0)

    fun setCategoryEnabled(key: String, enabled: Boolean) { preferences.edit().putBoolean(key, enabled).apply() }
    fun isCategoryEnabled(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)

    /** Dominios descargados de listas públicas por [RemoteBlocklistUpdater]. */
    fun remoteRules(): Set<String> = preferences.getStringSet("remote_block", emptySet()).orEmpty()
    fun remoteRulesUpdatedAt(): Long = preferences.getLong("remote_updated", 0)
    fun remoteSourceCount(): Int = preferences.getInt("remote_sources", 0)

    /** Reemplaza el conjunto de reglas remotas tras una descarga exitosa. Nunca borra reglas manuales. */
    fun replaceRemoteRules(domains: Set<String>, sourcesFetched: Int) {
        preferences.edit()
            .putStringSet("remote_block", domains)
            .putLong("remote_updated", System.currentTimeMillis())
            .putInt("remote_sources", sourcesFetched)
            .apply()
    }

    private fun add(key: String, domain: String): Boolean {
        val normalized = FilterEngine.normalizeDomain(domain) ?: return false
        val next = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        next += normalized
        preferences.edit().putStringSet(key, next).putLong("updated", System.currentTimeMillis()).apply()
        return true
    }
}
