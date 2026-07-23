Modelo TensorFlow Lite (incluido):

- `signature_model.tflite` — generado con `python/export_signature_tflite.py`

Notas:
- Entrada: ventana RSSI 1D (64 samples) normalizada con z-score.
- Salida (logits; la app aplica sigmoid):
  - índice `1` → actividad
  - índice `2` → respiración
- Sin este archivo la app sigue funcionando solo con el detector DSP.
- Regenerar: `C:\vf-venv\Scripts\python.exe python/export_signature_tflite.py`
