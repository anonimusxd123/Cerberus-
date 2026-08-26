package com.example.adblock.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeFeaturesTest {

    @Test fun `nodo con texto de ad y sdk conocido da señales altas`() {
        val f = NodeFeatures.extract(
            texto = "Publicidad",
            contentDescription = "",
            viewId = "com.google.android.gms.ads/ad_frame",
            className = "android.widget.FrameLayout",
            isClickable = false,
            depth = 3,
            childCount = 2
        )
        assertEquals(NodeFeatures.COUNT, f.size)
        assertTrue("keyword hits en texto debe ser > 0", f[0] > 0f)
        assertEquals(1f, f[2]) // viewid_matches_ad_sdk
    }

    @Test fun `nodo de contenido normal no dispara señales`() {
        val f = NodeFeatures.extract(
            texto = "Configuración",
            contentDescription = "",
            viewId = "com.whatsapp/chat_list",
            className = "android.widget.TextView",
            isClickable = true,
            depth = 5,
            childCount = 0
        )
        assertEquals(0f, f[0]) // text_ad_keyword_hits
        assertEquals(0f, f[2]) // viewid_matches_ad_sdk
    }

    @Test fun `boton de cerrar activa has_close_button_label`() {
        val f = NodeFeatures.extract(
            texto = "Saltar anuncio",
            contentDescription = "",
            viewId = "",
            className = "android.widget.Button",
            isClickable = true,
            depth = 4,
            childCount = 0
        )
        assertEquals(1f, f[5]) // has_close_button_label
    }

    @Test fun `depth y childCount se normalizan y saturan a 1`() {
        val f = NodeFeatures.extract(
            texto = "", contentDescription = "", viewId = "", className = "",
            isClickable = false, depth = 999, childCount = 999
        )
        assertEquals(1f, f[7]) // depth_norm saturado
        assertEquals(1f, f[8]) // child_count_norm saturado
    }
}
