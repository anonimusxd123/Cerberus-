package com.example.adblock.filtering

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Downloads public, community-maintained blocklists in "hosts file" format and merges the
 * domains into [BlocklistManager]. Manual whitelist/blacklist entries are never touched.
 *
 * Sources are widely used, openly licensed hosts lists (no account, no API key). If a source is
 * unreachable it's skipped; the merge only replaces the remote set once at least one source
 * succeeds, so a single failed fetch never wipes existing protection.
 */
object RemoteBlocklistUpdater {

    /**
     * Fuentes públicas de listas negras. `hosts` = formato clásico "0.0.0.0 dominio"
     * (StevenBlack, AdAway: publicidad/tracking genérico). `wildcardDomains` = formato
     * "*.dominio" de HaGeZi (una línea por dominio, sin comentarios de IP): su lista "Pop-Up
     * Ads" está mantenida a diario y se centra exactamente en las redes de popunders y
     * redirecciones forzadas típicas de portales de streaming/descargas, que es lo que la
     * categoría "Anti-redirección / popunder" de Ajustes activa o desactiva.
     */
    private val hostsSources = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://adaway.org/hosts.txt"
    )
    private val wildcardDomainSources = listOf(
        "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/popupads.txt"
    )

    suspend fun refreshNow(context: Context): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val manager = BlocklistManager(context)
        val merged = mutableSetOf<String>()
        var successfulSources = 0
        for (source in hostsSources) {
            try {
                val domains = fetchHostsList(source)
                if (domains.isNotEmpty()) { merged += domains; successfulSources++ }
            } catch (_: Exception) {
                // Fuente no disponible: se ignora y se continúa con las siguientes.
            }
        }
        if (manager.isCategoryEnabled("cat_redirects", true)) {
            for (source in wildcardDomainSources) {
                try {
                    val domains = fetchWildcardDomainList(source)
                    if (domains.isNotEmpty()) { merged += domains; successfulSources++ }
                } catch (_: Exception) {
                    // Fuente no disponible: se ignora y se continúa con las siguientes.
                }
            }
        }
        if (successfulSources > 0) {
            manager.replaceRemoteRules(merged, successfulSources)
            Result(success = true, domainCount = merged.size, sourcesFetched = successfulSources)
        } else {
            Result(success = false, domainCount = manager.remoteRules().size, sourcesFetched = 0)
        }
    }

    private fun fetchHostsList(url: String): Set<String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
        }
        connection.inputStream.bufferedReader().use { reader ->
            val domains = mutableSetOf<String>()
            reader.forEachLine { rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachLine
                val parts = line.split(Regex("\\s+"))
                if (parts.size < 2) return@forEachLine
                val ip = parts[0]
                if (ip != "0.0.0.0" && ip != "127.0.0.1") return@forEachLine
                val domain = FilterEngine.normalizeDomain(parts[1]) ?: return@forEachLine
                if (domain == "localhost" || domain.endsWith(".local")) return@forEachLine
                domains += domain
            }
            return domains
        }
    }

    /**
     * Lee el formato "Domains Wildcard" de HaGeZi: una línea por dominio, con un prefijo "*."
     * que ya indica "este dominio y sus subdominios" (el mismo criterio de coincidencia que usa
     * [FilterEngine]), y líneas de cabecera que empiezan por "#" como comentarios.
     */
    private fun fetchWildcardDomainList(url: String): Set<String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            requestMethod = "GET"
        }
        connection.inputStream.bufferedReader().use { reader ->
            val domains = mutableSetOf<String>()
            reader.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val candidate = line.removePrefix("*.")
                val domain = FilterEngine.normalizeDomain(candidate) ?: return@forEachLine
                domains += domain
            }
            return domains
        }
    }

    /** Programa una descarga periódica (~cada 12h, solo con red) mientras el ajuste esté activo. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }

    private const val WORK_NAME = "cerberus-blocklist-autoupdate"

    data class Result(val success: Boolean, val domainCount: Int, val sourcesFetched: Int)

    class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): androidx.work.ListenableWorker.Result {
            val result = refreshNow(applicationContext)
            return if (result.success) androidx.work.ListenableWorker.Result.success() else androidx.work.ListenableWorker.Result.retry()
        }
    }
}
