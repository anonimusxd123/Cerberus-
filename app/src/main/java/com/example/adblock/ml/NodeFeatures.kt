package com.example.adblock.ml

/**
 * Extracción de features para el clasificador de anuncios (LiteRT).
 *
 * IMPORTANTE: debe reflejar EXACTAMENTE la misma lógica que
 * `ml/feature_schema.py` en el repo. Si cambias algo aquí, replica el
 * cambio allá y vuelve a entrenar — si no, el modelo verá en producción
 * datos con una distribución distinta a la que vio en entrenamiento.
 *
 * Cada instancia representa UN nodo del árbol de accesibilidad.
 */
object NodeFeatures {

    const val COUNT = 9

    // Mismas listas/regex que feature_schema.py y que las que ya usaba
    // AdAccessibilityService antes de tener el modelo.
    private val PALABRAS_PUBLICIDAD = listOf(
        "publicidad", "anuncio", "anuncios", "patrocinado", "patrocinada",
        "advertisement", "advertising", "sponsored", "promoted", "promocionado",
        "\\bad\\b", "\\bads\\b", "install now", "instalar ahora",
        "learn more", "más información", "descargar ahora", "shop now"
    )
    private val REGEX_PUBLICIDAD = Regex(PALABRAS_PUBLICIDAD.joinToString("|"), RegexOption.IGNORE_CASE)

    val VIEW_ID_PATTERNS = listOf(
        "com.google.android.gms.ads", "com.google.ads.mediation",
        "adview", "ad_container", "ad_layout", "ad_frame", "native_ad",
        "com.facebook.ads", "com.unity3d.ads", "com.applovin", "com.mopub",
        "com.ironsource", "banner_container", "interstitial"
    )

    private val REGEX_BOTON_CIERRE = Regex(
        "cerrar|close|skip|omitir|saltar|dismiss|no gracias|✕|×",
        RegexOption.IGNORE_CASE
    )

    private fun keywordHits(texto: String): Int =
        REGEX_PUBLICIDAD.findAll(texto).count().coerceAtMost(3)

    private fun textLengthBucket(texto: String): Int = when {
        texto.isEmpty() -> 0
        texto.length < 20 -> 1
        texto.length < 80 -> 2
        else -> 3
    }

    /**
     * Extrae el vector de features de un nodo. El orden DEBE coincidir con
     * `feature_schema.FEATURE_NAMES` en Python.
     */
    fun extract(
        texto: String,
        contentDescription: String,
        viewId: String,
        className: String,
        isClickable: Boolean,
        depth: Int,
        childCount: Int
    ): FloatArray {
        val viewIdMatches = VIEW_ID_PATTERNS.any { viewId.contains(it, ignoreCase = true) }
        val isWebviewContent = className.contains("WebView", ignoreCase = true) &&
            (texto.isNotBlank() || contentDescription.isNotBlank())
        val etiqueta = contentDescription.ifBlank { texto }
        val hasCloseLabel = REGEX_BOTON_CIERRE.containsMatchIn(etiqueta)

        return floatArrayOf(
            keywordHits(texto).toFloat(),
            keywordHits(contentDescription).toFloat(),
            if (viewIdMatches) 1f else 0f,
            if (isWebviewContent) 1f else 0f,
            if (isClickable) 1f else 0f,
            if (hasCloseLabel) 1f else 0f,
            textLengthBucket(texto).toFloat(),
            (depth / 40f).coerceAtMost(1f),
            (childCount / 20f).coerceAtMost(1f)
        )
    }
}
