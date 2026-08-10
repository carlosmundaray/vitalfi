# VitalFi — Landing

Sitio de descarga de la app (APK / Play Store), manual y equipo.

## Estructura

```
landing/
├── index.html
├── README.md
├── assets/
│   ├── vitalfi_logo.png
│   └── equipo_vitalfi.jpg
├── docs/
│   ├── PLAYSTORE_FICHA.md          ← datos para Play Console
│   ├── Manual_Uso_VitalFi_Android.pdf
│   ├── TERMINOS_Y_CONDICIONES.md
│   └── playstore/
│       ├── icon_512.png
│       ├── icon_1024.png
│       └── icon_192.png
└── releases/
    ├── VitalFi-v1.3.20-signed.apk
    └── VitalFi-v1.3.22.aab         ← subir este a Play Store
```

## Cómo abrir

- Local: abre `landing/index.html` en el navegador
- XAMPP: `http://localhost/vitalfi/landing/`

## Notas

- El enlace de APK apunta a `releases/VitalFi-v1.3.20-signed.apk` (local en la landing).
- El enlace de Play Store usa `com.vitalfi` (ajusta la URL en `index.html` cuando la ficha esté publicada).
