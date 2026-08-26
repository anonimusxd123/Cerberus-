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
     * de vídeo (googlevideo.com) y de la API real (youtubei.googleapis.com), que no se bloquean
     * porque rompen la reproducción y la app. IMPORTANTE: los anuncios insertados dentro del
     * propio stream de vídeo (server-side / "SSAI") se sirven desde el mismo dominio que el
     * contenido, así que ningún bloqueo DNS puede eliminarlos sin romper la reproducción; esto es
     * una limitación de cualquier bloqueador basado en DNS, no solo de esta app.
     */
    private val youtubeAggressive = setOf(
        "googleads.g.doubleclick.net", "static.doubleclick.net", "pagead2.googlesyndication.com",
        "s.youtube.com", "ad.doubleclick.net", "ad.youtube.com", "ads.youtube.com"
    )

    /** Telemetría y red de anuncios de Meta que no forman parte del login/feed (no rompen la app). */
    private val facebookAggressive = setOf(
        "an.facebook.com", "connect.facebook.net", "graph.video.facebook.com",
        "pixel.facebook.com", "ads.facebook.com", "atlassolutions.com",
        "adtrace.co", "adjudge.com", "ads-api.facebook.com"
    )

    /**
     * Redes de anuncios y "popunder"/redirección habituales en apps de streaming, juegos y sitios
     * de vídeo/audio: anuncios intersticiales, banners y los scripts que abren pestañas o
     * redirigen a otra página al tocar el reproductor.
     */
    private val streamingAndGamingAggressive = setOf(
        "vungle.com", "chartboost.com", "ironsrc.com", "supersonicads.com", "tapjoy.com",
        "adcolony.com", "startapp.com", "inmobi.com", "smaato.com", "pangleglobal.com",
        "applvn.com", "mobfox.com", "smartyads.com", "adcash.com", "bidvertiser.com"
    )

    /**
     * Redes de anuncios "popunder", redirección forzada y clickbait muy frecuentes en portales de
     * streaming/descargas: abren ventanas o pestañas nuevas y redirigen al pulsar el reproductor
     * en lugar de mostrar solo un banner. Bloquear estos dominios por DNS evita esas
     * redirecciones porque la petición nunca llega a resolverse.
     */
    private val redirectAndPopunderAggressive = setOf(
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "juicyads.com",
        "adskeeper.co.uk", "mgid.com", "revcontent.com", "adsterra.com", "hilltopads.net",
        "clickadu.com", "trafficjunky.net", "exosrv.com", "zeropark.com", "plugrush.com",
        "eroadvertising.com", "trafficfactory.biz", "adnium.com", "admedia.com",
        "onclickalgo.com", "coinzilla.com", "clkrev.com", "highrevenuenetwork.com",
        "poweredby.jads.co", "ad-maven.com", "adsyield.com", "yllix.com", "clickaine.com"
    )

    val defaultRules: Set<String>
        get() {
            val categories = mutableSetOf<String>()
            categories += coreAdsAndTrackers
            if (isCategoryEnabled("cat_youtube", true)) categories += youtubeAggressive
            if (isCategoryEnabled("cat_facebook", true)) categories += facebookAggressive
            if (isCategoryEnabled("cat_streaming", true)) categories += streamingAndGamingAggressive
            if (isCategoryEnabled("cat_redirects", true)) categories += redirectAndPopunderAggressive
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
