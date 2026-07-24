# Trabajo relacionado: WiHear (MobiCom 2014) y viabilidad en VitalFi

**Fecha de análisis:** 24 de julio de 2026  
**Estado:** Documentación de investigación — **no implementado** en la app Android.

Este documento registra la revisión del paper *We Can Hear You with Wi-Fi!* (WiHear) y su relación con el alcance técnico de **VitalFi**.

---

## Referencia

Wang, G., Zou, Y., Zhou, Z., Wu, K., & Ni, L. M. (2014). *We Can Hear You with Wi-Fi!* In *Proceedings of the 20th Annual International Conference on Mobile Computing and Networking* (MobiCom ’14). ACM.  
https://doi.org/10.1145/2639108.2639112  

PDF del autor: https://guanhuawang.github.io/paper/WiHear_MobiCom14.pdf

```bibtex
@inproceedings{wang2014wihear,
  author    = {Wang, Guanhua and Zou, Yongpan and Zhou, Zimu and Wu, Kaishun and Ni, Lionel M.},
  title     = {{We Can Hear You with Wi-Fi!}},
  booktitle = {Proceedings of the 20th Annual International Conference on Mobile Computing and Networking},
  series    = {MobiCom '14},
  year      = {2014},
  pages     = {593--604},
  publisher = {ACM},
  doi       = {10.1145/2639108.2639112},
  url       = {https://guanhuawang.github.io/paper/WiHear_MobiCom14.pdf}
}
```

---

## Resumen del paper

WiHear explora si las señales Wi‑Fi pueden **“oír”** el habla **sin micrófono**, reconociendo patrones de movimiento de la boca (lip reading radiométrico) a partir de reflexiones de radio.

| Aspecto | WiHear |
|---------|--------|
| Señal | **CSI** (Channel State Information) a nivel PHY / OFDM |
| Técnica clave | Beamforming MIMO hacia la boca + *Mouth Motion Profile* + transformada wavelet de paquetes |
| Clasificación | Aprendizaje supervisado por persona (sílabas → palabras) + corrección por contexto |
| Vocabulario (paper) | ~14 sílabas, ~33 palabras entrenadas/probadas |
| Hardware | USRP N210 y Wi‑Fi comercial con acceso a CSI |
| Precisión reportada | ~91 % (1 persona, ≤6 palabras); ~74 % (hasta 3 personas); ~26–32 % a través de pared |

**Idea central:** el movimiento de labios/lengua/mandíbula es **no rígido**; WiHear conserva *parcialmente* el multipath (no lo elimina del todo) para capturar esas micro-variaciones, filtra ~1–5 Hz (banda del habla) y construye perfiles temporales por subportadora.

---

## Comparación con VitalFi

| | **WiHear** | **VitalFi** |
|---|------------|-------------|
| Objetivo | Reconocer sílabas/palabras por movimiento de boca | Detectar vida / respiración / actividad bajo escombros |
| Modalidad | CSI fino + beamforming | **RSSI** en teléfono Android (+ brújula) |
| Escala de movimiento | Centímetros / micro-movimiento facial | Tórax / actividad corporal |
| Escenario | Habla (usuario quieto, vocabulario cerrado) | Búsqueda y rescate experimental, NLOS / escombros |
| App de campo | No (SDR / NIC con CSI) | Sí (Kotlin, 100 % offline) |
| CSI en Python | — | Simulación y DSP auxiliar (`python/csi_processor.py`, etc.) |

Ambos trabajan en **device-free Wi‑Fi sensing** y escenarios **sin línea de visión**, pero apuntan a fenómenos físicos distintos (micro-movimiento oral vs. respiración/actividad).

---

## Viabilidad de implementación en VitalFi

### No viable en la app Android actual

1. **Android no expone CSI** de forma portable vía `WifiManager` (solo RSSI / escaneos).  
2. El movimiento de labios es **demasiado fino** para RSSI agregado: WiHear requiere amplitud/fase por subportadora.  
3. WiHear asume **beamforming / MIMO**, persona relativamente quieta y entrenamiento **por usuario**.  
4. El rendimiento a través de pared en el paper (~26–32 %) ya es bajo; bajo escombros reales sería aún más hostil para lip reading.

**Conclusión de producto:** no se implementará reconocimiento de habla estilo WiHear en el APK VitalFi.

### Sí viable como inspiración / líneas futuras

| Línea | Encaje en VitalFi | Notas |
|-------|-------------------|--------|
| Band-pass + análisis espectral | Ya usado (respiración ~0.1–0.5 Hz típico) | Misma familia DSP que WiHear (ellos: ~1–5 Hz habla) |
| Wavelets sobre ventanas RSSI | Mejora experimental del pipeline | Posible en Kotlin o en `python/` offline |
| CSI real (ESP32, Nexmon, SDR) | Rama de investigación aparte | Ver montaje CSI en `docs/`; no sustituye el modo RSSI de campo |
| Trabajo relacionado en papers | Citar WiHear | Micro-sensing Wi‑Fi; VitalFi se diferencia por SAR + RSSI |

---

## Decisión de alcance (registro)

| Ítem | Decisión |
|------|----------|
| Lip reading / “oír” palabras en la app | **Fuera de alcance** |
| Citar WiHear como trabajo relacionado | **Sí** (este documento + `vitalfi.bib`) |
| Reutilizar ideas DSP (filtros, wavelets) para respiración RSSI | **Opcional / futuro** |
| Prototipo CSI hardware acoplado a VitalFi | **Opcional / investigación**, no requisito del APK |

---

## Documentos relacionados en este repositorio

- [`README.md`](../README.md) — abstract y pipeline RSSI  
- [`Montaje_CSI_VitalFi.pdf`](Montaje_CSI_VitalFi.pdf) — montaje experimental CSI  
- [`Lista_Compra_CSI_2026.pdf`](Lista_Compra_CSI_2026.pdf) — material CSI  
- [`Formula_Deteccion_VitalFi.pdf`](Formula_Deteccion_VitalFi.pdf) — formulación de detección  
- [`../vitalfi.bib`](../vitalfi.bib) — entradas BibTeX del proyecto  

---

## Cómo citar este análisis

Si se menciona la evaluación de WiHear en el contexto de VitalFi:

> Mundaray, C. (2026). *Trabajo relacionado: WiHear (MobiCom 2014) y viabilidad en VitalFi*. Documentación del proyecto VitalFi. https://github.com/carlosmundaray/vitalfi
