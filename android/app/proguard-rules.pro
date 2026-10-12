# Reglas de optimización y desofuscación R8 / ProGuard para VitalFi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Mantener todo el código y modelos de la aplicación VitalFi
-keep class com.vitalfi.** { *; }
-keep interface com.vitalfi.** { *; }
-dontwarn com.vitalfi.**

# Mantener Compose
-dontwarn androidx.compose.**

# Mantener Kotlinx Coroutines
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Mantener CameraX, Camera2 y Providers para Modo AR
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-keep class androidx.camera.camera2.** { *; }
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-keep class * implements androidx.camera.core.CameraXConfig$Provider { *; }
-keep class com.google.common.util.concurrent.** { *; }
-dontwarn com.google.common.util.concurrent.**
-keep class androidx.concurrent.futures.** { *; }
-dontwarn androidx.concurrent.futures.**

