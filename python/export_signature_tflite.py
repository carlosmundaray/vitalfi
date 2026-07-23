"""
Train a lightweight RSSI signature model and export signature_model.tflite
for the Android SignatureModel (window=64, 3 logits → sigmoid in app).

Outputs: [unused/presence_logit, activity_logit, breathing_logit]
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np

WINDOW = 64
FS = 2.0


def zscore(x: np.ndarray) -> np.ndarray:
    mean = float(np.mean(x))
    std = float(np.std(x))
    std = max(std, 1e-4)
    return ((x - mean) / std).astype(np.float32)


def make_empty(n: int, rng: np.random.Generator) -> np.ndarray:
    """Flat / near-static RSSI (empty scene)."""
    base = rng.uniform(-72, -48)
    noise = rng.normal(0, rng.uniform(0.02, 0.12), size=n)
    return (base + noise).astype(np.float32)


def make_activity(n: int, rng: np.random.Generator) -> np.ndarray:
    """Irregular motion / activity bursts."""
    base = rng.uniform(-70, -45)
    t = np.arange(n, dtype=np.float32)
    bursts = np.zeros(n, dtype=np.float32)
    for _ in range(rng.integers(2, 6)):
        center = rng.uniform(0, n)
        width = rng.uniform(2, 8)
        amp = rng.uniform(1.5, 5.0)
        bursts += amp * np.exp(-0.5 * ((t - center) / width) ** 2)
    noise = rng.normal(0, 0.35, size=n).astype(np.float32)
    walk = np.cumsum(rng.normal(0, 0.08, size=n)).astype(np.float32)
    return (base + bursts + noise + walk * 0.15).astype(np.float32)


def make_breathing(n: int, rng: np.random.Generator) -> np.ndarray:
    """Periodic respiratory modulation on RSSI."""
    base = rng.uniform(-68, -42)
    bpm = float(rng.uniform(8, 28))
    freq = bpm / 60.0
    amp = float(rng.uniform(1.2, 4.5))
    t = np.arange(n, dtype=np.float32) / FS
    phase = float(rng.uniform(0, 2 * np.pi))
    wave = amp * (
        np.sin(2 * np.pi * freq * t + phase)
        + 0.12 * np.sin(4 * np.pi * freq * t + phase)
    )
    noise = rng.normal(0, 0.25, size=n).astype(np.float32)
    return (base + wave + noise).astype(np.float32)


def build_dataset(samples_per_class: int, seed: int = 42):
    rng = np.random.default_rng(seed)
    xs, ys = [], []

    # labels: [presence, activity, breathing] as soft targets for BCE-with-logits
    for _ in range(samples_per_class):
        xs.append(zscore(make_empty(WINDOW, rng)))
        ys.append([0.0, 0.0, 0.0])

        xs.append(zscore(make_activity(WINDOW, rng)))
        ys.append([1.0, 1.0, 0.15])

        xs.append(zscore(make_breathing(WINDOW, rng)))
        ys.append([1.0, 0.55, 1.0])

        # breathing + mild activity mix
        mix = 0.7 * make_breathing(WINDOW, rng) + 0.3 * make_activity(WINDOW, rng)
        xs.append(zscore(mix))
        ys.append([1.0, 0.85, 0.95])

    x = np.stack(xs, axis=0).astype(np.float32)
    y = np.asarray(ys, dtype=np.float32)
    idx = rng.permutation(len(x))
    return x[idx], y[idx]


def train_and_export(out_path: Path, epochs: int = 25, samples: int = 900) -> Path:
    import tensorflow as tf

    x, y = build_dataset(samples)
    split = int(0.85 * len(x))
    x_train, y_train = x[:split], y[:split]
    x_val, y_val = x[split:], y[split:]

    model = tf.keras.Sequential(
        [
            tf.keras.Input(shape=(WINDOW,), name="rssi_window"),
            tf.keras.layers.Dense(48, activation="relu"),
            tf.keras.layers.Dropout(0.15),
            tf.keras.layers.Dense(24, activation="relu"),
            tf.keras.layers.Dense(3, name="logits"),
        ],
        name="vitalfi_signature",
    )
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=tf.keras.losses.BinaryCrossentropy(from_logits=True),
        metrics=[tf.keras.metrics.BinaryAccuracy(threshold=0.0)],
    )
    model.fit(
        x_train,
        y_train,
        validation_data=(x_val, y_val),
        epochs=epochs,
        batch_size=64,
        verbose=1,
    )

    # Quick sanity: breathing sample should yield high logit[2]
    rng = np.random.default_rng(0)
    breath = zscore(make_breathing(WINDOW, rng))[None, :]
    empty = zscore(make_empty(WINDOW, rng))[None, :]
    pb = model.predict(breath, verbose=0)[0]
    pe = model.predict(empty, verbose=0)[0]
    print(f"logits breathing={pb} empty={pe}")
    print(f"sigmoid breathing={1/(1+np.exp(-pb))} empty={1/(1+np.exp(-pe))}")

    # Keras 3 + TF 2.16: from_keras_model can crash in MLIR; use concrete function.
    @tf.function(input_signature=[tf.TensorSpec([1, WINDOW], tf.float32, name="rssi_window")])
    def serving(x):
        return {"logits": model(x, training=False)}

    concrete = serving.get_concrete_function()
    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete], serving)
    converter.optimizations = []
    tflite_model = converter.convert()

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(tflite_model)
    print(f"Wrote {out_path} ({len(tflite_model)} bytes)")
    return out_path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--out",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "android"
        / "app"
        / "src"
        / "main"
        / "assets"
        / "signature_model.tflite",
    )
    parser.add_argument("--epochs", type=int, default=25)
    parser.add_argument("--samples", type=int, default=900)
    args = parser.parse_args()
    train_and_export(args.out, epochs=args.epochs, samples=args.samples)


if __name__ == "__main__":
    main()
