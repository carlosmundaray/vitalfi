# Reglas de optimización y desofuscación R8 / ProGuard para VitalFi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Mantener clases de datos y modelos del radar/DSP
-keep class com.vitalfi.dsp.** { *; }
-keep class com.vitalfi.sensor.** { *; }
-keep class com.vitalfi.ui.** { *; }
-keep class com.vitalfi.update.** { *; }
-keep class com.vitalfi.settings.** { *; }
-keep class com.vitalfi.export.** { *; }

# Mantener Compose
-dontwarn androidx.compose.**

# Mantener Kotlinx Coroutines
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Mantener CameraX y Camera2 para Modo AR
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class com.google.common.util.concurrent.** { *; }
-dontwarn com.google.common.util.concurrent.**
-keep class androidx.concurrent.futures.** { *; }
-dontwarn androidx.concurrent.futures.**
-keep class androidx.camera.camera2.Camera2Config { *; }
-keep class androidx.camera.core.CameraXConfig { *; }
