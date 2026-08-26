"""
Genera un dataset SEMILLA sintético (ml/data/seed_dataset.csv) para tener un
modelo funcional desde el día uno, mientras recolectas ejemplos reales desde
la app (ver README_ML.md, sección "Recolectar datos reales").

Este dataset NO reemplaza a los datos reales: solo evita que el modelo
arranque en blanco. En cuanto tengas un CSV real etiquetado, colócalo en
ml/data/ (cualquier nombre, terminado en .csv) y train.py lo combinará
automáticamente con este.
"""

import csv
import random

from feature_schema import FEATURE_NAMES, extract_features

random.seed(42)

# --- Ejemplos "positivos" (anuncio) ---
TEXTOS_AD = [
    "Publicidad", "Anuncio", "Sponsored", "Patrocinado", "Ad",
    "Install Now", "Instalar ahora", "Descargar ahora", "Shop Now",
    "Learn More", "Más información", "Toca para instalar la app",
]
DESC_AD = ["Advertisement", "Sponsored content", "Publicidad", "Ad choices"]
VIEWID_AD = [
    "com.google.android.gms.ads/ad_frame", "adview_container",
    "com.facebook.ads/native_ad_layout", "com.applovin.impl/interstitial",
    "banner_container", "com.unity3d.ads/ad_layout", "com.ironsource/interstitial",
]
CLASES = ["android.widget.TextView", "android.widget.FrameLayout", "android.webkit.WebView", "android.widget.Button", "android.widget.ImageView"]

filas = []

# Positivos: combinaciones con señales fuertes de ad
for _ in range(600):
    texto = random.choice(TEXTOS_AD) if random.random() < 0.7 else ""
    desc = random.choice(DESC_AD) if random.random() < 0.4 else ""
    view_id = random.choice(VIEWID_AD) if random.random() < 0.75 else ""
    clase = "android.webkit.WebView" if random.random() < 0.3 else random.choice(CLASES)
    clickable = random.random() < 0.6
    depth = random.randint(2, 25)
    children = random.randint(0, 6)
    feats = extract_features(texto, desc, view_id, clase, clickable, depth, children)
    filas.append(feats + [1])

# Positivos: overlay con botón de cierre típico de intersticial
for _ in range(200):
    texto = random.choice(["Cerrar", "Skip Ad", "Saltar anuncio", "×", "✕", "Omitir"])
    feats = extract_features(texto, "", random.choice(VIEWID_AD + [""]), "android.widget.Button", True, random.randint(1, 10), 0)
    filas.append(feats + [1])

# --- Ejemplos "negativos" (contenido normal de apps) ---
TEXTOS_NORMAL = [
    "Configuración", "Perfil", "Buscar", "Inicio", "Mensajes", "Notificaciones",
    "Guardar", "Cancelar", "Aceptar", "Siguiente", "Atrás", "12:45", "Juan Pérez",
    "Hoy hace un buen día", "Foto de perfil", "Comentarios (12)", "Me gusta",
    "Compartir", "Editar", "Eliminar", "", "Menú principal", "Ajustes de cuenta",
]
VIEWID_NORMAL = [
    "com.whatsapp/chat_list", "android:id/content", "com.instagram.android/feed_item",
    "toolbar_title", "recycler_view", "nav_bar", "com.spotify.music/track_row", "",
]

for _ in range(1000):
    texto = random.choice(TEXTOS_NORMAL)
    desc = random.choice(TEXTOS_NORMAL) if random.random() < 0.2 else ""
    view_id = random.choice(VIEWID_NORMAL)
    clase = random.choice(CLASES)
    clickable = random.random() < 0.3
    depth = random.randint(0, 30)
    children = random.randint(0, 15)
    feats = extract_features(texto, desc, view_id, clase, clickable, depth, children)
    filas.append(feats + [0])

# Negativos: WebView legítimo con contenido de la app (sin señales de ad)
for _ in range(150):
    texto = random.choice(["Artículo completo", "Términos y condiciones", "Ayuda"])
    feats = extract_features(texto, "", "", "android.webkit.WebView", False, random.randint(1, 20), random.randint(0, 10))
    filas.append(feats + [0])

random.shuffle(filas)

with open("data/seed_dataset.csv", "w", newline="", encoding="utf-8") as f:
    writer = csv.writer(f)
    writer.writerow(FEATURE_NAMES + ["label"])
    writer.writerows(filas)

print(f"Generadas {len(filas)} filas en ml/data/seed_dataset.csv")
