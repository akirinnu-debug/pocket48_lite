# Pocket48 混淆规则

# ====== 通用 ======
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ====== kotlinx.serialization ======
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pocket48.app.**$$serializer { *; }
-keepclassmembers class com.pocket48.app.** {
    *** Companion;
}
-keep class com.pocket48.app.data.model.** { *; }

# ====== OkHttp ======
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ====== Media3 / ExoPlayer ======
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ====== Compose ======
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ====== Kotlinx Coroutines ======
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ====== DataStore ======
-keep class androidx.datastore.** { *; }
