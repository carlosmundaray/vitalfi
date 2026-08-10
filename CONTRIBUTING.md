# Guía de Contribución a VitalFi 🚀

¡Gracias por tu interés en colaborar con **VitalFi**! VitalFi es un proyecto experimental de código abierto enfocado en la detección de signos vitales mediante señales inalámbricas Wi-Fi (RSSI / CSI) para escenarios de búsqueda y rescate en desastres.

Aceptamos contribuciones de desarrolladores móvil (Flutter/Android), ingenieros de procesamiento de señales, matemáticos, especialistas en UI/UX y entusiastas del hardware libre.

---

## 🛠️ ¿Cómo empezar?

### 1. Requisitos previos
- [Flutter SDK](https://docs.flutter.dev/get-started/install) (Canal `stable`)
- Android Studio / VS Code
- Dispositivo Android físico para pruebas con Wi-Fi (los emuladores tienen restricciones en escaneo de redes).

### 2. Configurar tu entorno local

```bash
# 1. Haz un fork del repositorio en GitHub
# 2. Clona tu fork localmente:
git clone https://github.com/TU_USUARIO/vitalfi_flutter.git
cd vitalfi_flutter

# 3. Agrega el repositorio principal como remoto 'upstream':
git remote add upstream https://github.com/carlosmundaray/vitalfi_flutter.git

# 4. Obtén las dependencias:
flutter pub get
```

---

## 🌿 Convenciones de Ramas y Commits

- **Ramas:** Crea una rama descriptiva para tu trabajo:
  - `feature/nombre-de-la-funcion`
  - `fix/descripcion-del-bug`
  - `docs/mejora-documentacion`
  - `research/algoritmo-csi`

- **Mensajes de Commit:** Seguimos la convención [Conventional Commits](https://www.conventionalcommits.org/):
  - `feat: añade filtro FFT para detección de respiración`
  - `fix: corrige error de permisos en escaneo Wi-Fi Android 13+`
  - `docs: actualiza instrucciones de montaje hardware`
  - `style: mejora interfaz del radar 3D`

---

## 💡 Áreas Prioritarias de Colaboración

Actualmente buscamos apoyo activo en los siguientes módulos:

1. **Procesamiento de Señal (Signal Processing):**
   - Implementación de algoritmos de filtrado (Pass-band filter, STFT, FFT) para aislar frecuencias respiratorias humanas (~0.15 - 0.4 Hz / 9-24 RPM).
   - Calibración y reducción de ruido térmico/multipath en entornos con escombros.

2. **Captura de Hardware y Datos CSI (Channel State Information):**
   - Integración de módulos ESP32 / Routers compatibles con extracción de matrices CSI para mayor precisión que el RSSI estándar.

3. **Renderizado de Radar (UI / Flutter):**
   - Visualizaciones interactivas 2D/3D con `CustomPainter` o `Three.js` / `Flame` en Flutter.
   - Heatmaps de densidad de señal y vectores de proximidad.

4. **Captura y Formateo de Datasets:**
   - Pruebas en entornos controlados y generación de datasets públicos para entrenamiento de modelos heurísticos o de aprendizaje automático.

---

## 📬 Proceso de Pull Request (PR)

1. Asegúrate de que el código pase el linter de Flutter:
   ```bash
   flutter analyze
   ```
2. Ejecuta las pruebas unitarias:
   ```bash
   flutter test
   ```
3. Realiza un push a tu rama y abre un **Pull Request** hacia la rama `main` del repositorio oficial.
4. Completa la plantilla de PR detallando los cambios realizados.

---

## 📄 Licencia

Al contribuir a VitalFi, aceptas que tus contribuciones se distribuyan bajo la licencia **GNU General Public License v3.0 (GPLv3)**.
