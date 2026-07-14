# VitalFi — Detección de Vida bajo Escombros vía Wi-Fi

VitalFi es un proyecto experimental de código abierto diseñado para asistir en la localización de personas atrapadas bajo escombros en zonas de desastre (como terremotos o derrumbes). Utiliza variaciones en la fuerza de la señal de red Wi-Fi (RSSI) para detectar movimientos respiratorios y estimar la ubicación/rumbo de personas sin necesidad de conexión a internet.

## Estructura del Repositorio

El proyecto se divide en dos componentes principales:

1. **[Android](file:///c:/xampp/htdocs/vitalfi/android/README.md)**: Una aplicación móvil nativa (Kotlin + Jetpack Compose) que corre 100% offline. Permite realizar escaneos pasivos y activos, calibrar la señal y visualizar un radar 2D/3D con el heatmap de detección de vida.
2. **[Python](file:///c:/xampp/htdocs/vitalfi/python)**: Scripts y utilidades para PC que incluyen simuladores, procesamiento digital de señales (filtros Hampel, bandpass, FFT, autocorrelación) y entrenamiento de modelos.

---

## Licencia

Este proyecto está bajo la Licencia **GNU GPL v3 (General Public License v3)**. 

Esto significa que:
* **Libertad de uso y modificación:** Puedes usar, estudiar, modificar y adaptar este software de forma gratuita para cualquier propósito (incluyendo el ámbito académico, civil o gubernamental).
* **Copyleft obligatorio:** Si decides distribuir versiones modificadas de esta aplicación o sus scripts, **debes liberar públicamente el código fuente modificado** bajo esta misma licencia (GPL v3).
* **Sin garantías:** Este es un software experimental y educativo. Se distribuye con la esperanza de que sea útil, pero **sin ninguna garantía** implícita o explícita.

Para más detalles, consulta el archivo completo de la licencia en **[LICENSE](file:///c:/xampp/htdocs/vitalfi/LICENSE)**.

---

## Términos de Uso y Privacidad

Para la integración académica con la API de **ORCID** y el cumplimiento de las normativas de privacidad y exención de responsabilidad durante búsquedas y pruebas científicas, consulta el documento de **[Términos y Condiciones de Uso y Política de Privacidad](file:///c:/xampp/htdocs/vitalfi/docs/TERMINOS_Y_CONDICIONES.md)**.
