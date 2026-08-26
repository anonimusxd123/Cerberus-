"""
Entrena el clasificador de anuncios y lo exporta a TFLite (LiteRT) listo
para copiar a app/src/main/assets/ad_classifier.tflite.

Uso:
    cd ml
    python3 generate_seed_dataset.py     # crea el dataset semilla (una vez)
    python3 train.py

Combina TODOS los .csv que encuentre en ml/data/ (el semilla + cualquier
dataset real que hayas etiquetado y colocado ahí). Cuantos más datos reales
agregues, menos peso relativo tiene el semilla sintético.
"""

import glob
import os

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split

from feature_schema import FEATURE_NAMES, N_FEATURES

DATA_DIR = "data"
OUTPUT_DIR = "output"
TFLITE_OUT = os.path.join(OUTPUT_DIR, "ad_classifier.tflite")
ANDROID_ASSETS = "../app/src/main/assets/ad_classifier.tflite"


def cargar_datos():
    archivos = sorted(glob.glob(os.path.join(DATA_DIR, "*.csv")))
    if not archivos:
        raise SystemExit(
            "No hay CSVs en ml/data/. Corre generate_seed_dataset.py primero, "
            "o coloca ahí tu dataset real etiquetado."
        )
    dfs = [pd.read_csv(a) for a in archivos]
    df = pd.concat(dfs, ignore_index=True)
    faltantes = [c for c in FEATURE_NAMES + ["label"] if c not in df.columns]
    if faltantes:
        raise SystemExit(f"Faltan columnas en el dataset: {faltantes}")
    df = df.dropna(subset=["label"])
    df["label"] = df["label"].astype(int)
    print(f"Cargadas {len(df)} filas desde {len(archivos)} archivo(s): {archivos}")
    print(df["label"].value_counts().rename({0: "no-ad", 1: "ad"}))
    return df


def construir_modelo():
    modelo = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(N_FEATURES,)),
        tf.keras.layers.Dense(16, activation="relu"),
        tf.keras.layers.Dense(8, activation="relu"),
        tf.keras.layers.Dense(1, activation="sigmoid"),
    ])
    modelo.compile(
        optimizer="adam",
        loss="binary_crossentropy",
        metrics=["accuracy", tf.keras.metrics.Precision(name="precision"), tf.keras.metrics.Recall(name="recall")],
    )
    return modelo


def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    df = cargar_datos()

    X = df[FEATURE_NAMES].to_numpy(dtype=np.float32)
    y = df["label"].to_numpy(dtype=np.float32)

    X_train, X_val, y_train, y_val = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    modelo = construir_modelo()
    modelo.summary()

    early_stop = tf.keras.callbacks.EarlyStopping(
        monitor="val_loss", patience=8, restore_best_weights=True
    )

    modelo.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=80,
        batch_size=32,
        callbacks=[early_stop],
        verbose=2,
    )

    resultados = modelo.evaluate(X_val, y_val, verbose=0)
    print("\n--- Métricas en validación ---")
    for nombre, valor in zip(modelo.metrics_names, resultados):
        print(f"{nombre}: {valor:.4f}")

    # --- Exportar a TFLite (LiteRT) ---
    converter = tf.lite.TFLiteConverter.from_keras_model(modelo)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(TFLITE_OUT, "wb") as f:
        f.write(tflite_model)
    print(f"\nModelo TFLite guardado en {TFLITE_OUT} ({len(tflite_model)} bytes)")

    os.makedirs(os.path.dirname(ANDROID_ASSETS), exist_ok=True)
    with open(ANDROID_ASSETS, "wb") as f:
        f.write(tflite_model)
    print(f"Copiado también a {ANDROID_ASSETS} (listo para compilar la app)")

    print(f"\nOrden de features esperado por el modelo: {FEATURE_NAMES}")


if __name__ == "__main__":
    main()
