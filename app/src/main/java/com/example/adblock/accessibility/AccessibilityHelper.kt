package com.example.adblock.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Utilidades para comprobar si el usuario ya activó el servicio de
 * accesibilidad de Cerberus, y para llevarlo directo a la pantalla
 * de Ajustes donde puede activarlo (no se puede activar sin acción
 * explícita del usuario; es una restricción de seguridad de Android).
 */
object AccessibilityHelper {

    fun estaServicioActivo(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val miServicio = ComponentName(context, AdAccessibilityService::class.java)
            .flattenToString()

        return enabledServices.split(':').any { it.equals(miServicio, ignoreCase = true) }
    }

    fun abrirAjustesAccesibilidad(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
