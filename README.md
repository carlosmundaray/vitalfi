# WhoFi / VitalFi: Detección de Vida bajo Escombros vía Wi-Fi

Este repositorio contiene la implementación del sistema **WhoFi / VitalFi**, diseñado para detectar personas atrapadas bajo escombros mediante el análisis de las variaciones en las señales Wi-Fi (RSSI) causadas por la respiración humana, y estimar su posición mediante triangulación basada en el rumbo del dispositivo.

---

## ⚠️ Aviso Importante (Uso Experimental)

**VitalFi es una herramienta experimental de apoyo basada en análisis de señales Wi-Fi.**  
Los resultados son estimaciones y **no son 100% confiables**.

VitalFi **no reemplaza** equipos profesionales de búsqueda y rescate, protocolos oficiales de emergencia, ni el criterio de personal técnico o médico especializado.

Al usar esta herramienta, aceptas que pueden existir falsos positivos/falsos negativos y que toda decisión crítica debe confirmarse con métodos profesionales adicionales.

---

## 📂 Estructura del Repositorio

El proyecto se divide en los siguientes componentes principales:

```
WhoFi/
├── 🤖 android/         # Aplicación Android nativa (VitalFi)
│   ├── app/           # Código fuente de la app (Kotlin, Jetpack Compose)
│   ├── gradle/        # Configuración del sistema de construcción
│   └── ...
├── 🐍 python/          # Scripts, simuladores y entrenamiento en Python
│   ├── main.py        # Punto de entrada principal para herramientas de PC
│   ├── train.py       # Entrenamiento del modelo de red neuronal
│   ├── simulator.py   # Generación de datos sintéticos
│   ├── requirements.txt # Dependencias de Python
│   └── ...
└── 📄 docs/            # Documentación y artículos de investigación
    └── whofi_paper.html # Documento científico explicativo del sistema WhoFi
```

---

## 🐍 Componente Python (`python/`)

El backend de Python permite generar conjuntos de datos sintéticos, entrenar modelos de aprendizaje profundo para clasificar patrones de respiración y realizar pruebas de detección en tiempo real en Windows.

### Requisitos Previos

- Python 3.10 o superior.
- Recomendado: Crear un entorno virtual (`.venv`).

### Configuración e Instalación

1. Navega al directorio de Python:
   ```bash
   cd python
   ```

2. Instala las dependencias necesarias:
   ```bash
   pip install -r requirements.txt
   ```

### Comandos Disponibles

Desde la carpeta `python/`, puedes ejecutar `main.py` con diferentes modos:

* **Generación de Datos Sintéticos**:
  ```bash
  python main.py --mode simulate --num-samples 1000 --output ./data
  ```

* **Entrenamiento de Modelo**:
  ```bash
  python main.py --mode train --dataset ./data/dataset.npz --epochs 20
  ```

* **Detección en Tiempo Real (Live Windows RSSI)**:
  ```bash
  python main.py --mode live
  ```

---

## 🤖 Componente Android (`android/`)

La aplicación Android nativa **VitalFi** implementa la adquisición de señales RSSI en tiempo real, filtros digitales para la extracción del ritmo respiratorio, brújula para triangulación de dirección y una interfaz interactiva con radar 2D y 3D.

### Requisitos

- Android 8.0 o superior (API 26+).
- Sensor de magnetómetro (brújula) en el dispositivo físico.
- Android Studio Jellyfish (2023.3) o superior.

### Instrucciones de Compilación y Uso

1. Abre **Android Studio**.
2. Selecciona **File → Open** y elige la carpeta `android/` en la raíz de este proyecto.
3. Espera a que se complete la sincronización de Gradle.
4. Conecta tu teléfono Android físico con la depuración USB activada.
5. Haz clic en **Run** (`Shift + F10`) para compilar e instalar la app.

> [!IMPORTANT]
> Para escanear redes Wi-Fi en Android, la app requiere permisos de **Ubicación**. Android exige este permiso para acceder a los identificadores y señales de red inalámbricas.

---

## 🔬 Metodología de Detección

El flujo de trabajo técnico consta de los siguientes pasos:
1. **Captura de Señal**: Se recolecta el RSSI de paquetes Wi-Fi a una tasa constante.
2. **Filtrado Hampel y Pasabanda**: Se eliminan valores atípicos y se aíslan las frecuencias de respiración humana típica (0.1 Hz a 0.7 Hz).
3. **Análisis de Espectro**: Se procesa la señal en el dominio de la frecuencia mediante Transformada Rápida de Fourier (FFT) para buscar picos de frecuencia dominantes.
4. **Triangulación y Rumbo**: Integrando lecturas del magnetómetro, se asocian picos de atenuación con rumbos específicos para estimar el vector de localización de la víctima.

---

## 📄 Licencia

**VitalFi / WhoFi** es **software libre** bajo la [Licencia MIT](LICENSE).

**Copyright © 2026 Carlos Mundaray — [Solvitco](https://solvitco.com)**

Puedes usar, copiar, modificar y distribuir este software siempre que conserves el aviso de copyright y la licencia. El software se proporciona «tal cual», sin garantías.
