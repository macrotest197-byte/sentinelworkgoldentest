# Room / Compose usan metadatos reflexivos mínimos; reglas básicas.
-keepattributes *Annotation*
-keep class com.sentinel.recaudacion.data.** { *; }

# androidx.security.crypto usa Tink, que referencia anotaciones de errorprone no presentes en
# tiempo de ejecución. Son solo anotaciones de compilación: se ignora la advertencia.
-dontwarn com.google.errorprone.annotations.**

# WorkManager instancia los Worker por nombre: preservar el que envía Telegram.
-keep class com.sentinel.recaudacion.telegram.TelegramSendWorker { *; }

# El IME y el Application se referencian desde el manifest (R8 los conserva); lo explicitamos
# para robustez del candidato de producción.
-keep class com.sentinel.recaudacion.input.SentinelInputMethodService { *; }
-keep class com.sentinel.recaudacion.SentinelApp { *; }
