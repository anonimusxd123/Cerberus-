package com.example.adblock.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Servicio de accesibilidad que detecta y cierra ventanas/overlays de
 * publicidad dentro de otras apps, en el propio dispositivo del usuario.
 *
 * IMPORTANTE: esto NO intercepta la red ni bloquea la carga del anuncio;
 * solo reacciona una vez que el anuncio ya está pintado en pantalla,
 * intentando pulsar su botón de cierre o, en su defecto, simulando "atrás".
 * Complementa (no reemplaza) el filtro DNS de AdBlockVpnService.
 */
class AdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AdAccessibilityService"

        // Umbral de puntuación para considerar que hay "suficientes señales" de anuncio.
        // Bajarlo = más agresivo (más falsos positivos posibles).
        private const val UMBRAL_BLOQUEO = 3

        // Palabras/frases visibles típicas de publicidad (con límites de palabra vía regex)
        private val PALABRAS_PUBLICIDAD = listOf(
            "publicidad", "anuncio", "anuncios", "patrocinado", "patrocinada",
            "advertisement", "advertising", "sponsored", "promoted", "promocionado",
            "\\bad\\b", "\\bads\\b", "install now", "instalar ahora",
            "learn more", "más información", "descargar ahora", "shop now"
        )

        private val REGEX_PUBLICIDAD = Regex(
            PALABRAS_PUBLICIDAD.joinToString("|"),
            RegexOption.IGNORE_CASE
        )

        // resource-id / paquetes típicos de SDKs de ads conocidos.
        // Esta señal es mucho más confiable que el texto visible.
        private val VIEW_ID_PATTERNS = listOf(
            "com.google.android.gms.ads",     // AdMob
            "com.google.ads.mediation",
            "adview", "ad_container", "ad_layout", "ad_frame", "native_ad",
            "com.facebook.ads",                 // Meta Audience Network
            "com.unity3d.ads",                  // Unity Ads
            "com.applovin",                     // AppLovin
            "com.mopub",                        // MoPub
            "com.ironsource",                   // ironSource
            "banner_container", "interstitial"
        )

        private val REGEX_BOTON_CIERRE = Regex(
            "cerrar|close|skip|omitir|saltar|dismiss|no gracias|✕|×",
            RegexOption.IGNORE_CASE
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Reforzamos en runtime los flags necesarios para leer overlays de otras apps.
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        serviceInfo = info
        Log.d(TAG, "Servicio de accesibilidad conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                revisarVentanasActivas()
            }
        }
    }

    /**
     * Revisa TODAS las ventanas visibles (no solo la activa), porque un
     * overlay publicitario suele aparecer como una ventana adicional
     * (TYPE_APPLICATION_OVERLAY) por encima del contenido normal.
     */
    private fun revisarVentanasActivas() {
        val ventanas = windows ?: return
        for (ventana in ventanas) {
            val root = ventana.root ?: continue
            val esOverlay = ventana.type == AccessibilityWindowInfo.TYPE_APPLICATION_OVERLAY
            val puntuacion = escanearNodo(root, profundidad = 0)

            // Si es una ventana overlay, bajamos el umbral (son casi siempre ads).
            val umbralEfectivo = if (esOverlay) 1 else UMBRAL_BLOQUEO

            if (puntuacion >= umbralEfectivo) {
                Log.d(TAG, "Señales de anuncio: $puntuacion (overlay=$esOverlay). Cerrando.")
                cerrarOverlay(root, ventana)
            }
        }
    }

    /** Recorre el árbol de nodos acumulando una puntuación de "señales de publicidad". */
    private fun escanearNodo(node: AccessibilityNodeInfo?, profundidad: Int): Int {
        if (node == null || profundidad > 40) return 0
        var puntuacion = 0

        val texto = node.text?.toString().orEmpty()
        val descripcion = node.contentDescription?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
        val clase = node.className?.toString().orEmpty()

        if (REGEX_PUBLICIDAD.containsMatchIn(texto)) puntuacion += 2
        if (REGEX_PUBLICIDAD.containsMatchIn(descripcion)) puntuacion += 2
        if (VIEW_ID_PATTERNS.any { viewId.contains(it, ignoreCase = true) }) puntuacion += 3
        if (clase.contains("WebView", ignoreCase = true) &&
            (texto.isNotBlank() || descripcion.isNotBlank())
        ) {
            puntuacion += 1
        }

        for (i in 0 until node.childCount) {
            puntuacion += escanearNodo(node.getChild(i), profundidad + 1)
            if (i > 200) break // salvaguarda ante árboles anómalos
        }
        return puntuacion
    }

    private fun cerrarOverlay(root: AccessibilityNodeInfo, ventana: AccessibilityWindowInfo) {
        val botonCierre = buscarBotonCierre(root)
        if (botonCierre != null) {
            botonCierre.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        // Fallback 1: la propia ventana soporta "dismiss" (API 33+)
        val dismissed = try {
            ventana.remove()
            true
        } catch (e: Exception) {
            false
        }
        if (dismissed) return

        // Fallback 2: simular "atrás" para descartar el overlay
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun buscarBotonCierre(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val etiqueta = (node.contentDescription?.toString() ?: node.text?.toString()).orEmpty()
        if (node.isClickable && REGEX_BOTON_CIERRE.containsMatchIn(etiqueta)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val encontrado = buscarBotonCierre(node.getChild(i))
            if (encontrado != null) return encontrado
        }
        return null
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio de accesibilidad interrumpido")
    }
}
