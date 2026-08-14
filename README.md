<div align="center">

  <img src="docs/vitalfi_logo.png" alt="VitalFi Logo" width="160" />

  # VitalFi

  ### *Detección de Signos Vitales bajo Escombros mediante Análisis de Señales Wi-Fi*

  [![Google Play](https://img.shields.io/badge/Google_Play-Disponible-414141?logo=google-play&logoColor=white)](https://play.google.com/store/apps/details?id=com.vitalfi)
  [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
  [![ORCID](https://img.shields.io/badge/ORCID-0009--0002--6152--0256-green.svg)](https://orcid.org/0009-0002-6152-0256)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Android](https://img.shields.io/badge/Platform-Android%20%7C%20Compose-3DDC84?logo=android&logoColor=white)](android/)
  [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
  [![Citation](https://img.shields.io/badge/Cite-CITATION.cff-orange.svg)](CITATION.cff)

  ---

  <a href="https://play.google.com/store/apps/details?id=com.vitalfi">
    <img src="https://play.google.com/intl/en_us/badges/static/images/badges/es_badge_web_generic.png" alt="Disponible en Google Play" height="70" />
  </a>

  <br />

  <img src="docs/playstore/feature_graphic_1024x500.png" alt="VitalFi Feature Graphic" width="700" />

</div>

## 📲 Descarga en Google Play

La aplicación oficial de **VitalFi** ya está publicada y disponible para descargar e instalar directamente desde Google Play Store:

👉 **[Descargar VitalFi en Google Play Store](https://play.google.com/store/apps/details?id=com.vitalfi)**

---

## 📌 Visión General del Proyecto

**VitalFi** es una aplicación experimental de investigación y rescate de código abierto desarrollada de forma nativa para **Android con Kotlin y Jetpack Compose**. Su objetivo principal es explorar el uso de las perturbaciones en el canal radioeléctrico Wi-Fi (fluctuaciones de RSSI y CSI) provocadas por micro-movimientos corporales y la frecuencia respiratoria humana, permitiendo estimar la presencia de personas atrapadas o sin conocimiento en escenarios de desastre, colapso de estructuras o escombros.

La aplicación opera **100% offline** directamente en dispositivos Android convencionales sin requerir servidores externos ni conexión a Internet.

> ⚠️ **Aviso de Responsabilidad:** VitalFi es un proyecto experimental de investigación científica y desarrollo tecnológico. **No sustituye** equipos certificados de búsqueda y rescate (USAR) ni protocolos médicos oficiales. Su uso está orientado a pruebas de laboratorio, prototipado y validación de campo.

---

## ⚡ Características Principales

- 📡 **Sensado Inalámbrico Non-Invasivo:** Análisis de variaciones dinámicas del entorno Wi-Fi (2.4 GHz).
- 📴 **Operación Offline Nativa:** Cero dependencia de servicios en la nube o infraestructura de red externa.
- ⏱️ **Calibración Automática del Entorno:** Proceso inicial (~30 segundos) para establecer la línea base de ruido del área.
- 🎯 **Estimación Vital Heurística:** Cálculo de un índice de confianza vital y tasa respiratoria en respiraciones por minuto (RPM).
- 🧭 **Visualización Radar 2D/3D:** Interfaz gráfica táctil militarizada para inspeccionar detecciones, profundidad estimada y vectores de proximidad.
- 📊 **Exportación de Datos para Análisis:** Soporte para registro de señales para validación académica.

---

## 🔬 Fundamento Científico y Algoritmo

Cuando una persona respira o realiza micro-movimientos en un área alcanzada por una señal Wi-Fi, la reflexión y dispersión de las ondas (efectos de *multipath*) sufren alteraciones periódicas.

$$\Delta \text{RSSI}(t) = f(\text{Respiración}, \text{Distancia}, \text{Estructura})$$

VitalFi captura estas variaciones espectrales para:
1. **Filtrar el ruido de fondo:** Aislación de interferencias térmicas y de canales vecinos con filtros Hampel y Bandpass.
2. **Transformada de Frecuencia (FFT/STFT):** Detección de frecuencias en el rango del ritmo respiratorio humano ($0.15 \text{ Hz} - 0.40 \text{ Hz} \iff 9 - 24 \text{ RPM}$).
3. **Estimación de Profundidad Heurística:** Atenuación de señal según la densidad estimada de los escombros.

Para profundizar en la base conceptual, consulta nuestro documento de [Trabajo Relacionado: WiHear (MobiCom 2014) y viabilidad en VitalFi](docs/TRABAJO_RELACIONADO_WIHEAR.md).

---

## 🗺️ Hoja de Ruta (Roadmap) y Áreas de Colaboración 🤝

¡Buscamos científicos, ingenieros de firmware, matemáticos y desarrolladores Android!

```mermaid
graph LR
    A[Módulo RSSI/CSI Android] --> B[Filtros FFT / STFT en Kotlin]
    B --> C[Visualizador Radar Compose 2D/3D]
    D[Receptor Hardware CSI ESP32] --> B
    C --> E[Red Mesh BLE Multi-Nodo]
```

### 🎯 Tareas abiertas para colaboradores:
- [ ] **Integración de Hardware CSI (ESP32 / Wi-Fi CSI):** Implementar la recepción de matrices CSI completas (Amplitud y Fase) mediante microcontroladores ESP32 para superar la limitación del RSSI nativo de Android.
- [ ] **Optimización de Procesamiento en Kotlin:** Implementación eficiente de transformadas de Fourier y filtros pasabanda para procesamiento en tiempo real.
- [ ] **Renderizado de Radar 3D Avanzado:** Mejorar el motor gráfico del radar táctil con Canvas y shaders en Jetpack Compose.
- [ ] **Sincronización Mesh Multi-Dispositivo:** Red de escaneo coordinado entre varios teléfonos mediante Bluetooth Low Energy (BLE).
- [ ] **Recolección y Formateo de Datasets:** Pruebas en escenarios reales para crear un dataset abierto de variaciones Wi-Fi en colapsos.

Revisa nuestra [Guía de Contribución (CONTRIBUTING.md)](CONTRIBUTING.md) para sumarte al proyecto.

---

## 🛠️ Requisitos de Hardware y Entorno

### Hardware Recomendado
| Elemento | Especificación / Notas |
|----------|------------------------|
| **Dispositivo Móvil** | Smartphone Android (Android 8.0 / API 26 o superior) |
| **Emisor Wi-Fi** | Router o AP portátil convencional en la banda de **2.4 GHz** (mayor penetración de obstáculos) |
| **Opcional (Fase 2)** | Módulo ESP32 habilitado para extracción de CSI |

### Entorno de Desarrollo (Android Nativo)
- [Android Studio Ladybug / Koala](https://developer.android.com/studio) con JDK 17+
- Android SDK 34 / 35
- Kotlin 2.0+

---

## 🚀 Inicio Rápido

```bash
# 1. Clonar el repositorio
git clone https://github.com/carlosmundaray/vitalfi.git
cd vitalfi

# 2. Compilar y ejecutar pruebas unitarias
cd android
./gradlew testDebugUnitTest

# 3. Compilar APK de depuración o lanzamiento
./gradlew assembleDebug
# o para release:
./gradlew assembleRelease
```

---

## 📚 Índice de Documentación Académica y Recursos

Toda la documentación técnica del proyecto está disponible en la carpeta [`docs/`](docs/):

- 📜 [Términos, Condiciones y Privacidad](docs/TERMINOS_Y_CONDICIONES.md)
- 📖 [Guía para Añadir VitalFi en ORCID](docs/ORCID_ADD_WORK.md)
- 🔬 [Ficha BibTeX para Importación Académica (`docs/vitalfi.bib`)](docs/vitalfi.bib)
- 📐 [Fórmula y Fundamento de Detección Vital (PDF)](docs/Formula_Deteccion_VitalFi.pdf)
- 🛠️ [Guía de Montaje de Sensores CSI (PDF)](docs/Montaje_CSI_VitalFi.pdf)
- 🛒 [Lista de Componentes Hardware CSI 2026 (PDF)](docs/Lista_Compra_CSI_2026.pdf)
- 📄 [Análisis del Paper WiHear MobiCom 2014 (PDF)](docs/WiHear_MobiCom14.pdf)

---

## 🎓 Cómo Citar este Proyecto

Si utilizas VitalFi o sus conceptos en tu investigación académica o desarrollos derivados, por favor cítalo utilizando la información de [CITATION.cff](CITATION.cff) o el siguiente bloque BibTeX:

```bibtex
@misc{vitalfi2026,
  author       = {Mundaray, Carlos},
  title        = {VitalFi: Wi-Fi-based life detection under rubble},
  year         = {2026},
  publisher    = {GitHub},
  journal      = {GitHub repository},
  howpublished = {\url{https://github.com/carlosmundaray/vitalfi}},
  url          = {https://github.com/carlosmundaray/vitalfi},
  note         = {Software prototype for Wi-Fi-based life detection and RSSI variation analysis}
}
```

---

## 👥 Autores y Colaboradores

- **Carlos Mundaray** — Autor Principal & Investigador — ORCID: [0009-0002-6152-0256](https://orcid.org/0009-0002-6152-0256)

Consulta [AUTHORS.md](AUTHORS.md) para ver la lista completa de colaboradores.

Este proyecto se distribuye bajo la licencia **GNU General Public License v3.0** — Ver [LICENSE](LICENSE) para más detalles.

