# Firma de la APK VitalFi

## Descargas publicadas

Las APK firmadas para usuarios estan en [`../releases/`](../releases/README.md).

Version actual: **1.3.50** — `releases/VitalFi-v1.3.50-signed.apk`

## Keystore (local)

Configura `android/keystore.properties` (no se sube a Git):

```properties
storePassword=***
keyPassword=***
keyAlias=release
storeFile=app/release-keystore.jks
```

| Campo | Valor |
|-------|-------|
| Archivo | `android/keystore/release-keystore.jks` |
| Alias | `release` |
| Propiedades | `android/keystore.properties` |

Coloca el archivo `release-keystore.jks` en `android/keystore/`.

```properties
storeFile=keystore/release-keystore.jks
```

## Generar APK / AAB firmada

```bash
cd android
./gradlew assembleRelease
# o
./gradlew bundleRelease
```

Salida APK: `android/app/build/outputs/apk/release/`
Salida AAB: `android/app/build/outputs/bundle/release/`

## Instalar

1. Copia el APK al telefono
2. Permite instalar apps de origenes desconocidos
3. Abre el APK e instala
4. Concede permiso de ubicacion al abrir VitalFi
