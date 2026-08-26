"""
Esquema de features para el clasificador de anuncios de Cerberus.

IMPORTANTE: este archivo debe reflejar EXACTAMENTE la misma lógica que
`app/src/main/java/com/example/adblock/ml/NodeFeatures.kt`. Si cambias algo
aquí (una regex, un orden de columnas, una normalización), replica el cambio
allá también — si no, el modelo entrenado no coincidirá con lo que la app
le da en producción ("training/serving skew").

Cada fila representa UN nodo del árbol de accesibilidad (AccessibilityNodeInfo),
no una pantalla completa. La app ya recorre el árbol nodo por nodo; este
clasificador reemplaza el sistema de puntuación manual (+2, +3...) de
AdAccessibilityService por una puntuación aprendida.
"""

import re

FEATURE_NAMES = [
    "text_ad_keyword_hits",     # nº de coincidencias de palabras de ads en el texto (0-3, saturado)
    "desc_ad_keyword_hits",     # nº de coincidencias en contentDescription (0-3, saturado)
    "viewid_matches_ad_sdk",    # 1 si el resource-id contiene un patrón de SDK de ads conocido
    "is_webview_with_content",  # 1 si es WebView Y tiene texto/descripción no vacíos
    "is_clickable",             # 1 si el nodo es clickeable
    "has_close_button_label",   # 1 si el texto/desc parece un botón de cerrar/saltar
    "text_length_bucket",       # 0=vacío, 1=corto(<20), 2=medio(<80), 3=largo
    "depth_norm",               # profundidad en el árbol / 40, saturado a 1.0
    "child_count_norm",         # nº de hijos directos / 20, saturado a 1.0
]

N_FEATURES = len(FEATURE_NAMES)

# --- Mismas listas/regex que AdAccessibilityService.kt ---

_PALABRAS_PUBLICIDAD = [
    "publicidad", "anuncio", "anuncios", "patrocinado", "patrocinada",
    "advertisement", "advertising", "sponsored", "promoted", "promocionado",
    r"\bad\b", r"\bads\b", "install now", "instalar ahora",
    "learn more", "más información", "descargar ahora", "shop now",
]
REGEX_PUBLICIDAD = re.compile("|".join(_PALABRAS_PUBLICIDAD), re.IGNORECASE)

VIEW_ID_PATTERNS = [
    "com.google.android.gms.ads", "com.google.ads.mediation",
    "adview", "ad_container", "ad_layout", "ad_frame", "native_ad",
    "com.facebook.ads", "com.unity3d.ads", "com.applovin", "com.mopub",
    "com.ironsource", "banner_container", "interstitial",
]

REGEX_BOTON_CIERRE = re.compile(
    "cerrar|close|skip|omitir|saltar|dismiss|no gracias|✕|×", re.IGNORECASE
)


def _keyword_hits(text: str) -> int:
    return min(len(REGEX_PUBLICIDAD.findall(text or "")), 3)


def _text_length_bucket(text: str) -> int:
    n = len(text or "")
    if n == 0:
        return 0
    if n < 20:
        return 1
    if n < 80:
        return 2
    return 3


def extract_features(
    text: str,
    content_description: str,
    view_id: str,
    class_name: str,
    is_clickable: bool,
    depth: int,
    child_count: int,
) -> list:
    """Debe coincidir 1:1 con NodeFeatures.kt#extract()."""
    text = text or ""
    content_description = content_description or ""
    view_id = view_id or ""
    class_name = class_name or ""

    viewid_matches = 1.0 if any(p in view_id.lower() for p in VIEW_ID_PATTERNS) else 0.0
    is_webview_content = 1.0 if ("webview" in class_name.lower() and (text.strip() or content_description.strip())) else 0.0
    label = content_description or text
    has_close_label = 1.0 if REGEX_BOTON_CIERRE.search(label) else 0.0

    return [
        float(_keyword_hits(text)),
        float(_keyword_hits(content_description)),
        viewid_matches,
        is_webview_content,
        1.0 if is_clickable else 0.0,
        has_close_label,
        float(_text_length_bucket(text)),
        min(depth / 40.0, 1.0),
        min(child_count / 20.0, 1.0),
    ]
