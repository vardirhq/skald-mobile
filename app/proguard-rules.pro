# kotlinx.serialization keeps its generated serializers by annotation.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class no.vardir.skald.** {
    *** Companion;
}
-keepclasseswithmembers class no.vardir.skald.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp's optional platform integrations.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink references Error Prone annotations that are compile-time metadata only.
# They are intentionally absent from the Android runtime; suppress R8's missing-class check.
-dontwarn com.google.errorprone.annotations.**
