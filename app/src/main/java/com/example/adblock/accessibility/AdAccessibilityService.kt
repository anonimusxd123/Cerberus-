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
        private const val UMBRAL_BLOQUEO = 2

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
     * por encima del contenido normal (no es la ventana principal de la app).
     */
    private fun revisarVentanasActivas() {
        val ventanas = windows ?: return
        for (ventana in ventanas) {
            val root = ventana.root ?: continue
            // AccessibilityWindowInfo no expone un tipo "overlay de anuncio" directo.
            // Cualquier ventana que NO sea la ventana principal de la app (TYPE_APPLICATION)
            // se trata como sospechosa de ser un overlay (ads, banners, pop-ups).
            val esOverlay = ventana.type != AccessibilityWindowInfo.TYPE_APPLICATION
            val puntuacion = escanearNodo(root, profundidad = 0)
            val paqueteVentana = root.packageName?.toString().orEmpty()

            // LOG DE DIAGNÓSTICO: se imprime SIEMPRE (aunque no dispare el bloqueo) para
            // poder ver con `adb logcat -s AdAccessibilityService` qué está detectando
            // realmente el servicio mientras un anuncio está en pantalla.
            Log.d(TAG, "Ventana [$paqueteVentana] tipo=${ventana.type} overlay=$esOverlay puntuacion=$puntuacion")

            // Si es una ventana overlay, bajamos el umbral (son casi siempre ads).
            val umbralEfectivo = if (esOverlay) 1 else UMBRAL_BLOQUEO

            if (puntuacion >= umbralEfectivo) {
                Log.d(TAG, "★ Señales suficientes: $puntuacion (overlay=$esOverlay). Cerrando.")
                cerrarOverlay(root, esOverlay)
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

    private fun cerrarOverlay(root: AccessibilityNodeInfo, esOverlay: Boolean) {
        val botonCierre = buscarBotonCierre(root)
        if (botonCierre != null) {
            botonCierre.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            mostrarAviso("Anuncio detectado: botón cerrado")
            return
        }

        // Sin botón de cierre encontrado: solo actuamos si es un overlay real
        // (una ventana aparte, tipo pop-up/interstitial). Si es contenido normal
        // de la app (ej. un post patrocinado en un feed), NO forzamos nada: no hay
        // manera segura de "cerrarlo" sin sacar al usuario de la pantalla actual.
        if (!esOverlay) {
            Log.d(TAG, "Señal de anuncio en contenido embebido sin botón de cierre: no se actúa (evita romper la navegación).")
            return
        }

        // Fallback 1: si el propio nodo raíz soporta "descartar" (ACTION_DISMISS)
        val dismissed = root.performAction(AccessibilityNodeInfo.ACTION_DISMISS)
        if (dismissed) {
            mostrarAviso("Anuncio detectado: overlay descartado")
            return
        }

        // Fallback 2: simular "atrás" para descartar el overlay
        performGlobalAction(GLOBAL_ACTION_BACK)
        mostrarAviso("Anuncio detectado: se simuló \"atrás\"")
    }

    private var ultimoAviso = 0L
    /** Toast simple para confirmar detección sin necesitar ADB/logcat. Con anti-spam de 2s. */
    private fun mostrarAviso(mensaje: String) {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoAviso < 2000) return
        ultimoAviso = ahora
        android.widget.Toast.makeText(applicationContext, "Cerberus: $mensaje", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun buscarBotonCierre(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val etiqueta = (node.contentDescription?.toString() ?: node.text?.toString()).orEmpty()

        if (REGEX_BOTON_CIERRE.containsMatchIn(etiqueta)) {
            // Caso simple: el propio nodo con el texto ya es clickeable.
            if (node.isClickable) return node
            // Caso real más común: el texto ("Omitir", "Saltar anuncio") está en un
            // TextView/ícono hijo, y el elemento clickeable de verdad es un padre
            // (el botón contenedor). Subimos hasta 5 niveles buscando ese padre.
            var actual = node.parent
            var niveles = 0
            while (actual != null && niveles < 5) {
                if (actual.isClickable) return actual
                actual = actual.parent
                niveles++
            }
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
