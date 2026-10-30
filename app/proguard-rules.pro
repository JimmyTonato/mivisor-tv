# Gson lee estos modelos por reflexión: sus campos son el formato (JSON de los proveedores y lo guardado).
-keep class com.mivisor.tv.model.** { *; }
# Fiabilidad: lo aprendido de cada fuente se guarda como JSON (Gson) en la base de datos.
-keep class com.mivisor.tv.reliability.Health { *; }
-keep class com.mivisor.tv.reliability.Event { *; }
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Registros detallados fuera en release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
