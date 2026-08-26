package com.example.adblock.ml

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Vuelca a un CSV local (sin subir nada a ningún servidor) los features de
 * los nodos que el AccessibilityService va viendo durante el uso normal del
 * teléfono, con una columna "label" VACÍA para que el usuario la complete
 * a mano (1 = era anuncio, 0 = no lo era) y así arme un dataset REAL.
 *
 * Se activa solo si el usuario prende el interruptor "Recolectar datos de
 * entrenamiento" en Ajustes (ver SettingsManager). Con el interruptor
 * apagado, esta clase no escribe nada.
 *
 * Flujo recomendado (ver ml/README_ML.md):
 *   1. Activar recolección, usar el teléfono normalmente un rato (con apps
 *      que sepas que muestran ads).
 *   2. Exportar/compartir el CSV desde la pantalla de Ajustes.
 *   3. Abrirlo en una hoja de cálculo y llenar la columna "label" con 0/1.
 *   4. Copiarlo a ml/data/ en el repo y correr ml/train.py.
 */
class DatasetLogger(context: Context) {

    private val archivo = File(context.getExternalFilesDir(null), NOMBRE_ARCHIVO)

    private var filasEscritas = 0

    init {
        if (!archivo.exists()) {
            archivo.writeText(CABECERA + "\n")
        } else {
            // Cuenta líneas existentes (menos la cabecera) para respetar el tope.
            filasEscritas = (archivo.readLines().size - 1).coerceAtLeast(0)
        }
    }

    /**
     * Registra un nodo. `paquete` y `textoBruto` ayudan a poner contexto al
     * etiquetar, pero NO se usan como features del modelo — evita que el
     * usuario tenga que adivinar de qué nodo se trataba al etiquetarlo.
     */
    @Synchronized
    fun registrar(
        features: FloatArray,
        paquete: String,
        textoBruto: String,
        esOverlay: Boolean
    ) {
        if (filasEscritas >= MAX_FILAS) return // evita que el archivo crezca sin límite
        val textoCorto = textoBruto.replace("\"", "'").replace("\n", " ").take(60)
        val fila = buildString {
            features.forEach { append(it).append(',') }
            append(if (esOverlay) 1 else 0).append(',')
            append('"').append(paquete).append("\",")
            append('"').append(textoCorto).append('"').append(',') // contexto, no es feature
            append("") // columna label, vacía a propósito
        }
        try {
            archivo.appendText(fila + "\n")
            filasEscritas++
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir en el dataset local", e)
        }
    }

    fun archivoActual(): File = archivo

    companion object {
        private const val TAG = "DatasetLogger"
        private const val NOMBRE_ARCHIVO = "cerberus_dataset.csv"
        private const val MAX_FILAS = 20_000

        val CABECERA = NodeFeatures::class.let {
            // Mismo orden que feature_schema.FEATURE_NAMES, más columnas de contexto al final.
            "text_ad_keyword_hits,desc_ad_keyword_hits,viewid_matches_ad_sdk," +
                "is_webview_with_content,is_clickable,has_close_button_label," +
                "text_length_bucket,depth_norm,child_count_norm,is_overlay_window," +
                "paquete,contexto_texto,label"
        }
    }
}
