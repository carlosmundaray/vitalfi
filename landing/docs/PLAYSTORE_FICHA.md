# VitalFi — Ficha completa para Google Play Store

Documento para copiar/pegar en [Google Play Console](https://play.google.com/console).  
App experimental · paquete `com.vitalfi` · versión **1.3.21** (versionCode **28**) · targetSdk **35**.

---

## 1. Cuenta y app

| Campo | Valor |
|--------|--------|
| Nombre de la app | VitalFi |
| Nombre del paquete (applicationId) | `com.vitalfi` |
| Idioma predeterminado | Español (España) o Español (Latinoamérica) |
| Tipo de app | App |
| Gratis / de pago | Gratis |
| Categoría | **Educación** o **Herramientas** (recomendado: Educación — investigación) |
| Etiquetas | investigación, Wi-Fi, experimental, Android, offline |

---

## 2. Archivo a subir (obligatorio AAB)

Play Store **no acepta APK** para apps nuevas: sube el **Android App Bundle (.aab)**.

| Archivo | Ruta |
|---------|------|
| AAB firmado | `android/app/build/outputs/bundle/release/app-release.aab` |
| Copia en landing | `landing/releases/VitalFi-v1.3.21.aab` |
| APK (solo sideload / web) | `releases/VitalFi-v1.3.21-signed.apk` |

**Firma**

| Campo | Valor |
|--------|--------|
| Keystore | `android/keystore/release-keystore.jks` |
| Alias | `release` |
| Config | `android/keystore.properties` (no subir a Git) |

En Play Console activa **Play App Signing** (recomendado) la primera vez que subas el AAB.

---

## 3. Ficha de Play (textos listos)

### Título (máx. 30 caracteres)
```
VitalFi
```

### Descripción breve (máx. 80 caracteres)
```
App experimental: estima patrones vitales con Wi-Fi (RSSI). Offline.
```

### Descripción completa
```
VitalFi es software experimental de investigación.

Analiza variaciones de la señal Wi-Fi (RSSI) de un router local para estimar posibles patrones relacionados con respiración o actividad, y muestra una hipótesis de rumbo en un radar 2D/3D. Funciona 100 % offline: no necesita internet ni datos móviles.

IMPORTANTE
• Es una prueba de concepto científica, no un equipo certificado de búsqueda y rescate.
• No sustituye geófonos, perros de rescate, cámaras térmicas ni protocolos oficiales.
• Distancia y profundidad son estimaciones heurísticas. Siempre verifica el sitio.
• El permiso de ubicación lo exige Android para leer escaneos Wi-Fi; VitalFi no envía GPS a internet.

Requisitos
• Android 8.0 o superior
• Router Wi-Fi 2.4 GHz cerca del área de prueba (sin internet)
• Brújula del teléfono

Desarrollado por Carlos Mundaray / Solvitco. Licencia GNU GPL v3.
```

### Lo nuevo en esta versión
```
Versión 1.3.21: targetSdk 35 (requisito Play), paquete com.vitalfi, detección experimental Wi-Fi RSSI, radar 2D/3D, exportación CSV y modo offline.
```

---

## 4. Recursos gráficos

| Recurso | Requisito Play | Archivo en el proyecto |
|---------|----------------|-------------------------|
| Icono de alta resolución | 512 × 512 PNG | `docs/playstore/icon_512.png` |
| Icono | 1024 × 1024 (opcional extra) | `docs/playstore/icon_1024.png` |
| Logo app | `docs/vitalfi_logo.png` / `landing/assets/vitalfi_logo.png` | |
| Capturas de pantalla | Mín. 2 (teléfono). Recomendado 4–8. JPEG/PNG 16:9 o 9:16 | **Debes tomarlas en el teléfono** |
| Gráfico de funciones | **1024 × 500** PNG/JPEG ≤15 MB | `docs/playstore/feature_graphic_1024x500.png` |
| Banner TV / tablet | Solo si aplica | No necesario |

**Capturas sugeridas (hazlas tú):**
1. Pantalla principal / radar 2D  
2. Radar 3D  
3. Detección / estado “Persona detectada — verifica el sitio”  
4. Configuración  
5. Aviso experimental (si aparece al abrir)

---

## 5. Clasificación de contenido (cuestionario IARC)

Completa el cuestionario en Play Console → **Política de la app** → **Clasificación del contenido**.  
VitalFi es una herramienta técnica offline: casi todo es **No**.

### Categoría de la app
| Pregunta | Respuesta |
|----------|-----------|
| Categoría / tipo | **Utilidad / Herramientas** o **Educación / Referencia** (elige la más cercana a “herramienta / investigación”) |
| ¿Es un juego? | **No** |

### Violencia
| Pregunta | Respuesta |
|----------|-----------|
| Violencia realista / fantástica | **No** |
| Sangre / mutilación | **No** |
| Violencia sexual | **No** |

### Contenido sexual
| Pregunta | Respuesta |
|----------|-----------|
| Desnudez / contenido sexual | **No** |
| Besos / caricias | **No** |

### Lenguaje
| Pregunta | Respuesta |
|----------|-----------|
| Lenguaje soez / grosero | **No** |

### Sustancias controladas
| Pregunta | Respuesta |
|----------|-----------|
| Alcohol / tabaco / drogas | **No** |

### Juegos de azar / apuestas
| Pregunta | Respuesta |
|----------|-----------|
| Apuestas / casino | **No** |
| Simulación de apuestas | **No** |

### Temas de terror / miedo
| Pregunta | Respuesta |
|----------|-----------|
| Contenido de terror | **No** |

### Interacción con usuarios / online
| Pregunta | Respuesta |
|----------|-----------|
| Intercambio de mensajes entre usuarios | **No** |
| Contenido generado por usuarios compartido | **No** |
| Chat / redes sociales | **No** |
| Usuarios pueden interactuar entre sí | **No** |
| La app comparte la ubicación del usuario con otros | **No** |
| Compras dentro de la app | **No** |
| Acceso a internet / contenidos web no moderados | **No** (app offline; no navega web dentro de la app) |

### Miscelánea / peligro
| Pregunta | Respuesta |
|----------|-----------|
| Instrucciones para fabricar armas / bombas / etc. | **No** |
| Contenido que promueve terrorismo o extremismo | **No** |
| Información que permita cometer delitos | **No** |

**Nota:** Aunque el contexto de la app es “rescate / escombros”, **no** es un juego de violencia ni enseña a hacer daño. Es software de medición/estimación experimental. Responde **No** a violencia y a “instrucciones peligrosas”.

### Resultado esperado
Tras enviar el cuestionario, Play suele asignar algo como **PEGI 3 / Everyone / All ages** (o equivalente IARC).  
Eso es independiente del **público objetivo** (sección 6): ahí puedes poner **18+** por el uso experimental en contextos de desastre.

---

## 6. Público objetivo y noticias

| Campo | Valor sugerido |
|--------|----------------|
| Público objetivo | 18 años o más |
| ¿Es una app de noticias? | No |
| ¿Diseñada para niños? | No |

---

## 7. Privacidad y seguridad de los datos

### URL de política de privacidad (obligatoria)
Debes publicar una URL **https** pública. Opciones:

1. Subir `docs/TERMINOS_Y_CONDICIONES.md` o HTML a tu web (`solvitco.com/...`)  
2. GitHub Pages: `https://carlosmundaray.github.io/vitalfi/docs/TERMINOS_Y_CONDICIONES.md` (mejor convertir a HTML)  
3. Landing: cuando esté online, p. ej. `https://TU-DOMINIO/landing/docs/TERMINOS_Y_CONDICIONES.md`

**Mientras no tengas URL https, Play no dejará publicar.**

### Data safety (respuestas sugeridas)

| Pregunta | Respuesta |
|----------|-----------|
| ¿Recopila o comparte datos de usuario? | **No** (procesamiento local offline) |
| ¿Ubicación se envía a servidores? | **No** |
| ¿Cifrado en tránsito? | N/A (no transmite) |
| ¿Los usuarios pueden solicitar borrado? | N/A / datos solo locales temporales |

Si Play obliga a declarar ubicación por el permiso:

- Recopilada: No almacenada / solo en dispositivo  
- Compartida: No  
- Propósito: Funcionalidad de la app (escaneo Wi-Fi)  
- Obligatoria: Sí  

---

## 8. Permisos — declaraciones

### Ubicación (ACCESS_FINE / COARSE)

**Texto para el formulario (ES):**
```
VitalFi solicita ubicación solo porque Android exige este permiso para escanear redes Wi-Fi (RSSI). Es software experimental. El procesamiento es local en el dispositivo. No rastrea al usuario, no guarda coordenadas GPS y no envía ubicación a servidores.
```

**Texto (EN):**
```
Location permission is required by Android to access Wi-Fi scans (RSSI). VitalFi is experimental research software. All processing is on-device. GPS coordinates are not stored or transmitted.
```

| Pregunta | Respuesta |
|----------|-----------|
| ¿Ubicación en segundo plano? | **No** (`ACCESS_BACKGROUND_LOCATION` no se usa) |
| ¿Publicidad / analytics? | **No** |

### Nearby Wi-Fi devices
```
Se usa solo para detectar redes Wi-Fi cercanas y medir RSSI. Marcado neverForLocation: no se usa para derivar la ubicación del usuario.
```

### Otros permisos (normales)
- Wi-Fi / red: leer estado y escanear  
- Vibración: alertas  

---

## 9. Contacto de la ficha

| Campo | Valor |
|--------|--------|
| Email de contacto | *(pon el email de Play Console / Solvitco)* |
| Web | https://solvitco.com |
| Política de privacidad | *(URL https pública — ver sección 7)* |
| Autor | Carlos Mundaray |
| ORCID | https://orcid.org/0009-0002-6152-0256 |

### Equipo (opcional en descripción)
- James Marín — Ingeniero en Telecomunicaciones · TikTok @James.secus  
- Carlos Mundaray — Ingeniero en Informática · TikTok @themunda24  
- Maryolie Nelson — Ejecución, Aplicación y Logística · TikTok @Espaciosdemitierra  

---

## 10. Países y precios

| Campo | Valor |
|--------|--------|
| Países | Todos (o solo los que elijas) |
| Precio | Gratis |
| Contiene anuncios | No |
| Compras in-app | No |

---

## 11. Checklist antes de publicar

- [ ] Cuenta Play Console activa (~25 USD una vez)  
- [ ] AAB firmado subido (prueba interna → cerrada → producción)  
- [ ] Icono 512×512  
- [ ] Mínimo 2 capturas de teléfono  
- [ ] Título + descripción breve + completa  
- [ ] URL de privacidad **https** pública  
- [ ] Data safety completado  
- [ ] Declaración de ubicación (sin background)  
- [ ] Clasificación de contenido  
- [ ] Email de contacto  
- [ ] Dejar claro en ficha: **experimental / investigación**  

---

## 12. Comandos útiles

```bash
cd android
./gradlew.bat bundleRelease
```

Salida AAB:
`android/app/build/outputs/bundle/release/app-release.aab`

---

## 13. Aviso legal corto (para ficha / soporte)

> VitalFi © 2026 Carlos Mundaray — Solvitco. Software experimental bajo GNU GPL v3. No es instrumento certificado de rescate. Uso bajo responsabilidad del usuario.
