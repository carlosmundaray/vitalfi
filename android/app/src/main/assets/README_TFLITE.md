Coloca aquí el modelo TensorFlow Lite con este nombre exacto:

- `signature_model.tflite`

Notas:
- Entrada esperada: ventana RSSI 1D normalizada con z-score.
- Salida genérica:
  - Si hay 2+ valores, el índice `1` es puntuación de actividad.
  - Si hay 3+ valores, el índice `2` es puntuación de respiración.
  - En otro caso se usa el primer valor.
- Sin este archivo la app sigue funcionando solo con el detector DSP.
