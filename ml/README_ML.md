# Detección de anuncios con IA (LiteRT)

Esta carpeta contiene el pipeline para entrenar el modelo que
`AdAccessibilityService` usa como segunda señal (además de la heurística de
reglas que ya existía) para decidir si un nodo de pantalla es publicidad.

**Todo corre on-device y offline.** No hay backend, no se sube nada a
ningún servidor; el CSV que genera la app se queda en el propio teléfono
hasta que tú lo compartes manualmente.

## Cómo encajan las piezas

```
AdAccessibilityService (Kotlin)
        │  1. extrae features de cada nodo (NodeFeatures.kt)
        ▼
NodeFeatures.extract(...)  ───────────────►  ml/feature_schema.py
        │  (misma lógica, dos lenguajes — deben ir siempre en paralelo)
        ▼
AdClassifier (LiteRT) ──── carga ────  app/src/main/assets/ad_classifier.tflite
        │                                       ▲
        │                                       │ generado por
        ▼                                       │
puntuación combinada                    ml/train.py
(heurística + modelo)                          ▲
                                                │ entrena con
                                        ml/data/*.csv
                                          ▲              ▲
                                  seed_dataset.csv   tu dataset real
                                  (sintético,        (exportado desde
                                   generado por       la app y
                                   generate_seed_     etiquetado por
                                   dataset.py)        ti a mano)
```

## Estado actual

Ya incluí un `ad_classifier.tflite` entrenado con el **dataset semilla
sintético** (`ml/data/seed_dataset.csv`, generado por
`generate_seed_dataset.py`). Funciona como punto de partida razonable
porque replica las mismas señales que ya usaba tu heurística de reglas,
pero **no vio ningún caso real todavía** — para que mejore de verdad
necesita datos reales de tu uso del teléfono.

## Recolectar datos reales (recomendado)

1. Compila e instala la app, activa el servicio de accesibilidad.
2. En **Ajustes → Detección con IA (LiteRT)**, activa "Recolectar datos de
   entrenamiento".
3. Usa el teléfono con normalidad, especialmente abre apps que sepas que
   muestran anuncios (juegos con intersticiales, apps con banners, etc.).
   Cada nodo con texto/id relevante que el servicio analice se va
   guardando en un CSV local (`.../Android/data/com.example.adblock/files/cerberus_dataset.csv`).
4. Vuelve a Ajustes y pulsa **Compartir** para exportar el CSV (por
   ejemplo, guárdalo en Drive o mándatelo a ti mismo).
5. Ábrelo en una hoja de cálculo. Vas a ver todas las columnas ya
   calculadas (`text_ad_keyword_hits`, `viewid_matches_ad_sdk`, etc.) más
   `paquete` y `contexto_texto` como referencia, y una columna `label`
   **vacía al final**. Para cada fila, escribe:
   - `1` si ese nodo realmente era parte de un anuncio
   - `0` si era contenido normal de la app
   No hace falta etiquetar todas las filas — borra las que no te queden
   claras.
6. Guarda el archivo como CSV y colócalo en `ml/data/` (cualquier nombre,
   con extensión `.csv`; puedes tener varios archivos ahí, uno por sesión
   de recolección).
7. Reentrena:
   ```bash
   cd ml
   pip install -r requirements.txt
   python3 train.py
   ```
   Esto combina TODOS los `.csv` de `ml/data/` (el semilla + los tuyos),
   entrena de nuevo y sobreescribe
   `app/src/main/assets/ad_classifier.tflite` automáticamente. Solo falta
   recompilar la app.

Cuantos más ejemplos reales agregues (idealmente cientos, de varias apps
distintas), menos peso relativo tiene el dataset semilla y mejor
generaliza el modelo a tu uso real.

## Arquitectura del modelo

Un MLP muy pequeño (Dense 16 → Dense 8 → Dense 1 sigmoid, ~300 parámetros,
el `.tflite` resultante pesa unos pocos KB) que recibe 9 features por nodo
(ver `feature_schema.py`) y devuelve una probabilidad de "es anuncio".
Se eligió deliberadamente pequeño porque corre una vez por nodo, potencialmente
cientos de veces por segundo mientras la pantalla cambia — tiene que ser
prácticamente instantáneo.

`AdAccessibilityService` NO reemplaza la heurística de reglas por el
modelo: los combina. Si el `.tflite` faltara o fallara al cargar
(`AdClassifier.cargarSiExiste` devuelve `null`), el servicio sigue
funcionando solo con las reglas, como antes.

## Modificar las features

Si quieres agregar/quitar una feature (por ejemplo, el tamaño del nodo en
pantalla, o si está dentro de los primeros N píxeles de la pantalla):

1. Edítalo en **ambos** lugares, en el mismo orden:
   - `ml/feature_schema.py` (`FEATURE_NAMES` + `extract_features`)
   - `app/src/main/java/com/example/adblock/ml/NodeFeatures.kt` (`extract`)
2. Borra `ml/data/seed_dataset.csv` y vuelve a correr
   `generate_seed_dataset.py` (el generador también usa `feature_schema.py`,
   así que tomará la nueva forma automáticamente).
3. Si tienes datasets reales ya etiquetados con el esquema viejo, tendrás
   que regenerarlos o descartarlos — el número/orden de columnas debe
   coincidir con `FEATURE_NAMES` actual.
4. Vuelve a correr `train.py`.

## Métricas del último entrenamiento (dataset semilla)

El semilla es sintético y "fácil" a propósito (para arrancar con una
heurística clara), así que el 100% de exactitud en validación es
esperable y **no** implica que el modelo ya generalice perfecto a casos
reales — es exactamente la razón por la que el paso de recolección real
de arriba importa.
