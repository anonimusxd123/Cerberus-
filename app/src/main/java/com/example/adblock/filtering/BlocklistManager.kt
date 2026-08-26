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
     * porque rompen la reproducción y la app. Todos verificados contra fuentes públicas activas
     * (listas de Pi-hole/AdGuard para YouTube). IMPORTANTE: los anuncios insertados dentro del
     * propio stream de vídeo (server-side / "SSAI") se sirven desde el mismo dominio que el
     * contenido, así que ningún bloqueo DNS puede eliminarlos sin romper la reproducción; esto es
     * una limitación de cualquier bloqueador basado en DNS, no solo de esta app. Por el mismo
     * motivo NO incluimos aquí hosts sueltos de "googlevideo.com" (r1---sn-xxxx.googlevideo.com):
     * son los mismos servidores que entregan el vídeo real y bloquearlos deja el reproductor
     * cargando indefinidamente en cuanto aparece un anuncio, tal y como reportan varios usuarios
     * de Pi-hole que probaron esa técnica.
     */
    private val youtubeAggressive = setOf(
        "googleads.g.doubleclick.net", "static.doubleclick.net", "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com", "securepubads.g.doubleclick.net", "stats.g.doubleclick.net",
        "s.youtube.com", "ad.doubleclick.net", "www.googletagservices.com"
    )

    /** Telemetría y red de anuncios de Meta que no forman parte del login/feed (no rompen la app). */
    private val facebookAggressive = setOf(
        "an.facebook.com", "connect.facebook.net", "graph.video.facebook.com",
        "pixel.facebook.com", "ads.facebook.com", "atlassolutions.com"
    )

    /**
     * Plataformas de anuncios en vídeo ("video ad tech") usadas por apps de streaming, juegos y
     * reproductores de vídeo/audio de terceros, distintas de las redes puramente móviles.
     */
    private val streamingAndGamingAggressive = setOf(
        "vungle.com", "chartboost.com", "ironsrc.com", "supersonicads.com", "tapjoy.com",
        "adcolony.com", "startapp.com", "inmobi.com", "smaato.com", "pangleglobal.com",
        "mobfox.com", "adcash.com", "bidvertiser.com", "2mdn.net", "serving-sys.com",
        "tubemogul.com", "innovid.com"
    )

    /**
     * Redes de anuncios "popunder", redirección forzada y clickbait muy frecuentes en portales de
     * streaming/descargas: abren ventanas o pestañas nuevas y redirigen al pulsar el reproductor
     * en lugar de mostrar solo un banner. Bloquear estos dominios por DNS evita esas
     * redirecciones porque la petición nunca llega a resolverse. Este núcleo local se complementa
     * con la lista remota "HaGeZi Pop-Up Ads" (ver [RemoteBlocklistUpdater]), mantenida a diario y
     * con más de 50 000 dominios de esta categoría.
     */
    private val redirectAndPopunderAggressive = setOf(
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "juicyads.com",
        "adskeeper.co.uk", "mgid.com", "revcontent.com", "adsterra.com", "hilltopads.net",
        "clickadu.com", "trafficjunky.net", "exosrv.com", "zeropark.com", "plugrush.com",
        "eroadvertising.com", "trafficfactory.biz", "admedia.com", "yllix.com"
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
