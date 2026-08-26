package com.example.adblock.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Carga `assets/ad_classifier.tflite` (generado por `ml/train.py`, ver
 * README_ML.md) con el runtime LiteRT y expone una única función:
 * la probabilidad (0..1) de que un nodo del árbol de accesibilidad sea
 * contenido publicitario.
 *
 * Si el modelo no está presente o falla al cargar, [isReady] queda en
 * `false` y el llamador (AdAccessibilityService) debe usar su heurística
 * de respaldo — la app nunca debe dejar de funcionar por falta de modelo.
 */
class AdClassifier private constructor(private val interpreter: Interpreter) {

    private val entrada = Array(1) { FloatArray(NodeFeatures.COUNT) }
    private val salida = Array(1) { FloatArray(1) }

    val isReady: Boolean get() = true

    /** Ejecuta la inferencia. NO es thread-safe: llamar siempre desde el mismo hilo. */
    @Synchronized
    fun predictProb(features: FloatArray): Float {
        features.copyInto(entrada[0])
        interpreter.run(entrada, salida)
        return salida[0][0]
    }

    fun close() = interpreter.close()

    companion object {
        private const val TAG = "AdClassifier"
        private const val NOMBRE_MODELO = "ad_classifier.tflite"

        /** Devuelve null si el modelo no existe o no se pudo cargar (fallo silencioso y seguro). */
        fun cargarSiExiste(context: Context): AdClassifier? {
            return try {
                val buffer = cargarModelMappedBuffer(context)
                val interpreter = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(1) })
                Log.d(TAG, "Modelo LiteRT cargado correctamente ($NOMBRE_MODELO)")
                AdClassifier(interpreter)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar el modelo LiteRT, se usará solo la heurística de reglas", e)
                null
            }
        }

        private fun cargarModelMappedBuffer(context: Context): MappedByteBuffer {
            val descriptor = context.assets.openFd(NOMBRE_MODELO)
            FileInputStream(descriptor.fileDescriptor).use { input ->
                return input.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength
                )
            }
        }
    }
}
