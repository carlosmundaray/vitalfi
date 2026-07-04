Place your TensorFlow Lite model file here with this exact name:

- `signature_model.tflite`

Notes:
- Input expected by app: 1D RSSI window normalized to z-score.
- Output parsing is generic:
  - If output has 2+ values, index `1` is used as activity score.
  - If output has 3+ values, index `2` is used as breathing score.
  - Otherwise first value is used.
- The app keeps working without this file using the DSP detector only.
