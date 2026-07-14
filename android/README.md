# VitalFi Android — Detección de vida bajo escombros vía Wi-Fi

App Android que replica el experimento de **WhoFi/VitalFi** en el teléfono: monitorea variaciones de señal Wi-Fi (RSSI) del router para detectar respiración y triangular la posición girando el dispositivo.

## Funciona sin internet

**No necesitas conexión a internet ni datos móviles.** Toda la detección, el procesamiento de señal y la interfaz corren 100% en el teléfono.

Lo único que necesitas es:
- **Wi-Fi activado** en el teléfono (puede estar en modo avión con Wi-Fi encendido)
- Un **router Wi-Fi 2.4 GHz** cerca del escombro (el router **no necesita** cable de internet)
- Permiso de **ubicación** (Android lo exige para leer señal Wi-Fi)

### Modo recomendado sin internet: Escaneo pasivo

1. Activa Wi-Fi (puedes dejar datos móviles apagados o usar modo avión + Wi-Fi)
2. Pulsa **«Escaneo pasivo»** y elige la red del router
3. No hace falta conectar el teléfono al Wi-Fi del router

## Requisitos

- Android 8.0+ (API 26)
- Teléfono con Wi-Fi y brújula (magnetómetro)
- Router Wi-Fi **2.4 GHz** cerca del escombro (~0.5 m) — **sin internet**
- Android Studio Hedgehog (2023.1) o superior

## Cómo abrir el proyecto

1. Abre Android Studio
2. **File → Open** → selecciona la carpeta `android/VitalFi`
3. Espera a que Gradle sincronice
4. Conecta tu teléfono con depuración USB o usa un emulador (el experimento real requiere dispositivo físico)
5. Pulsa **Run**

## Nuevas funciones (v1.1)

### 1. Exportar CSV
- Botón **CSV** en pantalla principal
- Exporta todas las muestras RSSI + log de análisis (detección, posición, rumbo)
- Comparte por WhatsApp, Gmail, Drive, etc.
- Analiza en Excel o con el script Python de PC

### 2. Escaneo pasivo (sin conectar)
- Chip **«Escaneo pasivo»** → elige la red del router en la lista
- No necesitas conectar el teléfono al Wi-Fi del router
- Android limita barridos (~1 cada 8 s); la app rellena entre barridos
- Útil si quieres mantener datos móviles activos

### 3. Radar 3D
- Chip **«Radar 3D»** / **«Radar 2D»** para alternar vista
- Vista 3D con profundidad estimada bajo escombros
- Controles: **Q/E** rotar, **W/S** inclinar, **V** resetear vista
- Historial de posiciones (trail naranja)

## Controles

| Acción | Control |
|--------|---------|
| Modo conectado | Chip «Conectado» |
| Escaneo sin conectar | Chip «Escaneo pasivo» → elegir red |
| Alternar 2D / 3D | Chip «Radar 3D» |
| Rotar vista 3D | Q / E / W / S |
| Ajustar rumbo | ◀ ▶ |
| Reiniciar | Reiniciar |
| Exportar datos | CSV |

## Cómo hacer el experimento

### Montaje

```
        [Router Wi-Fi 2.4 GHz]
              |
         ~0.5 m
              |
    ====================  escombros
              |
         (persona aquí)
              |
         [Tu teléfono Android]
      conectado al Wi-Fi del router
```

1. Coloca el **router** lo más cerca posible del montículo de escombros (no necesita internet).
2. En el teléfono, activa **Wi-Fi** (modo conectado: únete al router; modo pasivo: no hace falta conectar).
3. Abre **VitalFi** y concede permiso de **ubicación** (Android lo exige para leer RSSI).
4. **Calibra 30 segundos** sin moverte ni hablar.
5. Gira el teléfono **360° lentamente** alrededor del escombro.
6. Si detecta respiración, el radar muestra la posición estimada y las instrucciones de navegación.

### Controles en pantalla

| Acción | Botón |
|--------|-------|
| Ajustar rumbo manualmente | ◀ ▶ (rotar izq/der) |
| Reiniciar mapa y calibración | Reiniciar |

## Qué hace la app (técnicamente)

| Módulo | Función |
|--------|---------|
| `WifiRssiCollector` | Lee RSSI cada 0.5 s vía `WifiManager` + broadcast `RSSI_CHANGED` |
| `VitalSignalDetector` | Mismo algoritmo que Python: Hampel, bandpass, FFT, autocorrelación |
| `BearingAccumulator` | Triangulación por rumbo al girar el teléfono |
| `CompassReader` | Brújula para orientación |
| `RadarView` | Mapa 2D con heatmap y objetivo |

## Diferencias vs la versión PC

| PC (Python) | Android |
|-------------|---------|
| Calidad Wi-Fi 0–255 (wlanapi) | RSSI dBm normalizado a 0–255 |
| Muestreo ~5–10 Hz | ~2 Hz (límite del sistema) |
| Hotspot multi-antena (varios celulares) | **No disponible** — un solo sensor |
| Radar 3D matplotlib | Radar 2D en Canvas |
| Modo router + hotspot PC | Modo **conectado** o **escaneo pasivo** (sin internet) |

## Limitaciones importantes

- Es un **experimento educativo**, no un equipo certificado de rescate.
- Android **no permite** leer RSSI de otros dispositivos conectados al hotspot del teléfono.
- La tasa de muestreo es menor que en PC; necesitas **más tiempo** de escaneo (60–90 s).
- Funciona mejor con router **2.4 GHz** y poca interferencia.
- La profundidad bajo escombros es una **estimación heurística**, no medición real.

## Estructura del proyecto

```
android/VitalFi/
├── app/src/main/java/com/whofi/vitalfi/
│   ├── MainActivity.kt
│   ├── VitalFiViewModel.kt
│   ├── wifi/WifiRssiCollector.kt
│   ├── sensor/CompassReader.kt
│   ├── dsp/          ← algoritmos portados de Python
│   └── ui/           ← pantalla Compose + radar
└── README.md
```

## Compilar desde línea de comandos

```bash
cd android/VitalFi
./gradlew assembleDebug
```

APK generado en: `app/build/outputs/apk/debug/app-debug.apk`

## Próximos pasos posibles

- Persistir sesiones localmente (sin servidor)
- Sincronizar varios teléfonos como antenas vía red local (requeriría servidor en campo)
- Radar 3D con SceneView / OpenGL

## Licencia

Este subproyecto de Android forma parte de VitalFi y está licenciado bajo la **GNU GPL v3**. Consulta el archivo [LICENSE](../LICENSE) en la raíz del repositorio para ver los términos completos.
