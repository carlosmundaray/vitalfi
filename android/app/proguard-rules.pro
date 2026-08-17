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
