# Guía de Contribución a VitalFi 🚀

¡Gracias por tu interés en colaborar con **VitalFi**! VitalFi es un proyecto experimental de código abierto enfocado en la detección de signos vitales mediante señales inalámbricas Wi-Fi (RSSI / CSI) para escenarios de búsqueda y rescate en desastres.

Aceptamos contribuciones de desarrolladores Android (Kotlin / Jetpack Compose), ingenieros de procesamiento de señales, matemáticos, especialistas en UI/UX y entusiastas del hardware libre.

---

## 🛠️ ¿Cómo empezar?

### 1. Requisitos previos
- [Android Studio Ladybug / Koala](https://developer.android.com/studio) con JDK 17+
- Android SDK 34/35
- Dispositivo Android físico para pruebas con Wi-Fi (los emuladores tienen restricciones en escaneo de redes).

### 2. Configurar tu entorno local

```bash
# 1. Clona el repositorio:
git clone https://github.com/carlosmundaray/vitalfi.git
cd vitalfi

# 2. Cambia a la rama de desarrollo:
git checkout develop
```

---

## 🌿 Flujo de Trabajo con Ramas (Git Flow)

Para mantener la rama `main` limpia y libre de errores técnicos:

1. **`main` (Producción)**: Solo código validado, probado y listo para publicación en Play Store o releases oficiales.
2. **`develop` (Desarrollo y Pruebas)**: Rama activa de integración.
3. **Ramas de funcionalidad / corrección**:
   - `feature/nombre-de-la-funcion`: Nuevas capacidades creadas desde `develop`.
   - `fix/descripcion-del-bug`: Correcciones de bugs creadas desde `develop`.
   - `test/modulo-a-probar`: Suites de pruebas y verificación.

---

## ✍️ Convenciones de Commits (Conventional Commits)

Cada commit debe ser claro, humano y estructurado:

- `feat(radar): añadir calibración automática de suelo`
- `fix(dsp): corregir cálculo de varianza en HampelFilter`
- `docs: actualizar guía de instalación y arquitectura`
- `style(ui): mejorar contraste de botones en modo oscuro`
- `test(dsp): agregar pruebas unitarias para detector de pulso`
- `chore(gradle): actualizar dependencias y versión de APK`

---

## 💡 Áreas Prioritarias de Colaboración

Actualmente buscamos apoyo activo en los siguientes módulos:

1. **Procesamiento de Señal (Signal Processing - `dsp`):**
   - Algoritmos de filtrado (Bandpass, STFT, FFT, Hampel) para aislar frecuencias respiratorias humanas (~0.15 - 0.4 Hz / 9-24 RPM).
   - Calibración y reducción de ruido térmico/multipath en entornos con escombros.

2. **Renderizado de Radar y UI (`ui`):**
   - Visualizaciones tácticas 2D/3D con Jetpack Compose y Canvas.
   - Heatmaps de densidad de señal y vectores de proximidad.

3. **Captura de Sensores (`sensor`):**
   - Barómetro, acelerómetro y detección de pasos para navegación inercial.

---

## 📬 Proceso de Pull Request (PR)

1. Trabaja siempre en una rama derivada de `develop`.
2. Asegúrate de compilar y ejecutar las pruebas unitarias:
   ```bash
   cd android
   ./gradlew test
   ```
3. Abre un **Pull Request** apuntando hacia la rama `develop`.
4. Una vez validada la versión en `develop`, se realiza el merge a `main` con su respectiva etiqueta de versión (tag).

---

## 📄 Licencia

Al contribuir a VitalFi, aceptas que tus contribuciones se distribuyan bajo la licencia **GNU General Public License v3.0 (GPLv3)**.

