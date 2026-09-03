# 📡 VitalFi ESP32-S3 Hardware CSI Firmware

Firmware oficial de **VitalFi** para microcontroladores **ESP32-S3** para captura de **CSI (Channel State Information: Amplitud y Fase por subportadora OFDM)** y transmisión en tiempo real a la aplicación Android de rescate.

---

## 🚀 Características

- 🎯 **Captura CSI por Hardware Nativo:** Lee directamente las matrices de subportadoras OFDM 802.11n (LLTF + HT-LTF) del chip ESP32-S3.
- 📴 **Modo Autónomo de Rescate (SoftAP):** El ESP32 genera su propia red Wi-Fi (`VitalFi-Rescue-AP`), permitiendo operar en zonas de desastre o escombros sin routers ni conexión a Internet.
- ⚡ **Protocolo VitalFi Enhanced UDP v2:** Empaquetado binario ultraligero con número mágico `0x5646` (`'VF'`), cabecera de metadatos de 24 bytes y pares $I/Q$ por subportadora.
- 🔄 **Inyección de Tráfico Adaptativa:** Estimulación periódica de paquetes a 30 Hz para garantizar flujo continuo de matrices CSI incluso en entornos silenciosos.
- 📊 **Telemetría USB Serie:** Monitor en tiempo real a 115200 baudios con indicador de tasa de muestreo (Hz), RSSI, piso de ruido y clientes conectados.

---

## 📁 Estructura del Proyecto PlatformIO

```
firmware/esp32s3/
├── platformio.ini              # Configuración de placa, CPU (240MHz) y flags
├── include/
│   ├── csi_engine.h            # Interfaz del motor CSI
│   ├── vitalfi_packet.h        # Protocolo binario de tramas VitalFi v2
│   └── wifi_config.h           # Configuración de Wi-Fi, puertos y modos
└── src/
    ├── csi_engine.cpp          # Configuración ESP-IDF CSI y callback PHY
    └── main.cpp                # Punto de entrada, telemetría y loop principal
```

---

## 🛠️ Instrucciones de Uso con PlatformIO IDE

### 1. Abrir en VS Code / PlatformIO
1. Abre **Visual Studio Code** con la extensión **PlatformIO IDE** instalada.
2. Ve a **File > Open Folder...** (o *Abrir Carpeta...*) y selecciona la carpeta:
   `c:\xampp\htdocs\vitalfi\firmware\esp32s3`

### 2. Configurar Modo de Red (Opcional)
Abre el archivo [`include/wifi_config.h`](include/wifi_config.h):
- **Modo Punto de Acceso (Recomendado para pruebas y campo):**
  `#define VITALFI_MODE_AP_DEFAULT 1`
  *(El ESP32 creará la red `VitalFi-Rescue-AP` con clave `vitalfi2026`)*.
- **Modo Estación (Si quieres que se conecte a tu router o al hotspot de tu móvil):**
  `#define VITALFI_MODE_AP_DEFAULT 0`
  *(Configura tu `VITALFI_STA_SSID` y `VITALFI_STA_PASSWORD`)*.

### 3. Conectar y Flashear el ESP32-S3
1. Conecta tu placa **ESP32-S3** a tu computadora mediante un cable USB (puerto **USB/UART** o **USB nativo**).
2. En la barra inferior de PlatformIO en VS Code, haz clic en el ícono de **Upload** (flecha hacia la derecha `→`) o ejecuta en la terminal:
   ```bash
   pio run -t upload
   ```
3. Para ver la telemetría en tiempo real, abre el monitor serie (ícono de enchufe o terminal):
   ```bash
   pio device monitor
   ```

---

## 📱 Conexión con la App VitalFi Android

1. **Conectar el Wi-Fi del Teléfono:**
   - En tu teléfono Android, busca y conéctate a la red Wi-Fi:
     - **SSID:** `VitalFi-Rescue-AP`
     - **Contraseña:** `vitalfi2026`
2. **Abrir VitalFi App:**
   - Abre la aplicación **VitalFi**.
   - En la barra superior o en Ajustes, el modo CSI se conectará automáticamente al puerto `5500`.
   - Verás el indicador verde de **CSI Hardware Activo** mostrando la tasa de paquetes (~30 Hz) y las subportadoras detectando micro-movimientos y respiración.
