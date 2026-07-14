# VitalFi — Detección de vida bajo escombros vía Wi‑Fi

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.3.20-green.svg)](releases/README.md)
[![Platform](https://img.shields.io/badge/Android-8.0%2B-brightgreen.svg)](android/README.md)

> **Software de investigación experimental** para asistir en la localización de personas atrapadas bajo escombros mediante variaciones de la señal Wi‑Fi (RSSI). Corre 100 % offline. **No sustituye** equipos ni protocolos profesionales de búsqueda y rescate.

| Campo | Valor |
|-------|--------|
| **Autor** | Carlos Mundaray — [Solvitco](https://solvitco.com) |
| **ORCID** | *(añade tu ORCID iD en [`CITATION.cff`](CITATION.cff) y aquí, p. ej. `https://orcid.org/0000-XXXX-XXXX-XXXX`)* |
| **Repositorio** | https://github.com/carlosmundaray/vitalfi |
| **Licencia** | [GNU GPL v3](LICENSE) |
| **Versión de software** | **1.3.20** |
| **APK firmada** | [VitalFi-v1.3.20-signed.apk](releases/VitalFi-v1.3.20-signed.apk) |
| **Cómo citar** | Ver sección [Cómo citar](#cómo-citar) y [`CITATION.cff`](CITATION.cff) |

---

## Abstract (EN)

**VitalFi** is an open-source experimental Android application and companion Python tooling that explores **device-free Wi‑Fi sensing** for search-and-rescue research. Using commodity smartphones and a local 2.4 GHz access point, the system monitors **RSSI fluctuations**, applies digital signal processing (outlier rejection, band-pass filtering, spectral analysis), and estimates **breathing-related activity** and a **bearing/distance hypothesis** relative to the rescuer. A 2D/3D radar UI supports offline field trials without internet connectivity. Results are heuristic estimates and must not be used as a sole decision tool in real emergencies.

**Keywords:** Wi‑Fi sensing, RSSI, through-rubble detection, search and rescue, Android, offline DSP, disaster response

## Resumen (ES)

**VitalFi** es un proyecto experimental de código abierto (app Android + utilidades Python) orientado a investigación en **detección de vida bajo escombros** a partir de variaciones de **RSSI Wi‑Fi**. El teléfono, sin internet, analiza la señal de un router local, estima actividad respiratoria y muestra un **radar 2D/3D** con rumbo, distancia y guía tipo búsqueda hacia el punto estimado. Es una prueba de concepto científica: las mediciones son aproximadas y **no reemplazan** geófonos, perros de rescate ni protocolos oficiales.

**Palabras clave:** sensado Wi‑Fi, RSSI, detección bajo escombros, búsqueda y rescate, Android, DSP offline, desastres

---

## Autores y afiliación

- **Carlos Mundaray** — Solvitco ([solvitco.com](https://solvitco.com))  
  - GitHub: [@carlosmundaray](https://github.com/carlosmundaray)  
  - ORCID: *completar en [`CITATION.cff`](CITATION.cff)*  

Contacto y aviso legal: [Términos y Condiciones / Privacidad](docs/TERMINOS_Y_CONDICIONES.md).

---

## Cómo citar

Si usas VitalFi en investigación, cita el software (versión concreta) así:

**APA (ejemplo)**

> Mundaray, C. (2026). *VitalFi: Wi‑Fi–based life detection under rubble* (Version 1.3.20) [Computer software]. https://github.com/carlosmundaray/vitalfi

**BibTeX**

```bibtex
@software{mundaray_vitalfi_2026,
  author       = {Mundaray, Carlos},
  title        = {{VitalFi}: Wi-Fi--based life detection under rubble},
  version      = {1.3.20},
  year         = {2026},
  publisher    = {GitHub},
  url          = {https://github.com/carlosmundaray/vitalfi},
  license      = {GPL-3.0-only}
}
```

Tras archivar en **Zenodo**, sustituye la URL por el **DOI** (concept DOI o DOI de la versión) y actualiza [`CITATION.cff`](CITATION.cff). En GitHub, el archivo `CITATION.cff` habilita el botón **Cite this repository**.

### Publicar en ORCID (pasos recomendados)

1. Completa tu **ORCID iD** en `CITATION.cff` (campo `orcid` del autor).
2. Crea una cuenta en [Zenodo](https://zenodo.org) e inicia sesión con GitHub u ORCID.
3. En Zenodo → GitHub, habilita el repositorio `carlosmundaray/vitalfi`.
4. Publica un **Release** en GitHub (p. ej. `v1.3.20`); Zenodo generará un **DOI**.
5. Vincula Zenodo con ORCID para que el *Software* aparezca automáticamente en tu perfil, o añade el trabajo manualmente en ORCID (tipo *Software*) con el DOI.

---

## Método (pipeline)

```text
Router Wi‑Fi 2.4 GHz (sin internet)
        │
        ▼
Teléfono Android — RSSI + brújula (offline)
        │
        ▼
DSP: limpieza / bandas de interés / espectral
        │
        ▼
Hipótesis: respiración / actividad / rumbo–distancia
        │
        ▼
Radar 2D/3D + guía de navegación + export CSV
```

- **Entrada:** intensidad RSSI del AP elegido y rumbo del magnetómetro.  
- **Procesamiento:** local en el dispositivo (y scripts Python en PC para análisis offline).  
- **Salida:** puntos en radar, etiquetas P1/P2…, profundidad estimada, CSV para reproducibilidad.

---

## Estructura del repositorio

| Componente | Descripción |
|------------|-------------|
| [`android/`](android/README.md) | App nativa (Kotlin + Jetpack Compose), 100 % offline, radar 2D/3D, capas, guía, CSV |
| [`python/`](python/) | Utilidades DSP/PC (filtros, FFT, simulación/entrenamiento auxiliares) |
| [`releases/`](releases/README.md) | APK firmadas listas para instalar |
| [`docs/`](docs/TERMINOS_Y_CONDICIONES.md) | Términos, privacidad y contexto académico |
| [`CITATION.cff`](CITATION.cff) | Metadatos de citación (GitHub / Zenodo) |
| [`LICENSE`](LICENSE) | GNU GPL v3 |

---

## Descarga e instalación (APK)

Versión actual: **1.3.20** — [VitalFi-v1.3.20-signed.apk](releases/VitalFi-v1.3.20-signed.apk)

Enlace directo:

https://github.com/carlosmundaray/vitalfi/raw/main/releases/VitalFi-v1.3.20-signed.apk

Instrucciones y historial de versiones: [releases/README.md](releases/README.md).  
Compilación desde fuente: [android/README.md](android/README.md).

---

## Cómo reproducir un ensayo de campo

1. Coloca un **router Wi‑Fi 2.4 GHz** cerca del montículo (no necesita cable de internet).  
2. En el teléfono: Wi‑Fi activo (puede usarse modo avión + Wi‑Fi).  
3. Abre VitalFi → **Escaneo pasivo** (o modo conectado) y elige la red del router.  
4. Concede permiso de **ubicación** (Android lo exige para leer Wi‑Fi; la app no envía GPS a internet).  
5. Camina/gira despacio alrededor del escombro; usa la guía del radar hacia el punto estimado.  
6. Exporta **CSV** para análisis posterior con las herramientas en `python/`.

---

## Limitaciones y disclaimer

- Es **investigación / prueba de concepto**, no un instrumento certificado de rescate.  
- Distancia, profundidad y “punto cero” son **estimaciones heurísticas** sujetas a multipath, ruido y hardware del teléfono.  
- **No reemplaza** perros de rescate, geófonos, cámaras térmicas ni protocolos de protección civil.  
- Uso bajo tu propia responsabilidad. Detalle legal: [Términos y Condiciones](docs/TERMINOS_Y_CONDICIONES.md).  
- Sin garantía (cláusula estándar GPL).

---

## Licencia

Código bajo **[GNU GPL v3](LICENSE)**.

- Puedes usar, estudiar y modificar el software.  
- Si redistribuyes versiones modificadas, debes publicar el código fuente bajo GPL v3.  
- Marcas y diseños de VitalFi / Solvitco no se ceden automáticamente con la licencia del código.

---

## Documentación relacionada

- [Términos y Condiciones / Privacidad](docs/TERMINOS_Y_CONDICIONES.md)  
- [App Android — guía técnica](android/README.md)  
- [APK firmadas](releases/README.md)  
- [Firma de release](android/SIGNING.md)
