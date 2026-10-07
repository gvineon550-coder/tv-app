# ============================================================
# R8 / ProGuard правила для tv-app
# ============================================================

# ---------- Атрибуты (нужны для рефлексии) ----------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations
-keepattributes AnnotationDefault

# ---------- Модели данных (Gson читает через рефлексию) ----------
-keep class com.gvineon550coder.tvapp.data.** { *; }
-keepclassmembers class com.gvineon550coder.tvapp.data.** { *; }

# ---------- Gson ----------
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.Expose <fields>;
}

# ---------- Retrofit ----------
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ---------- OkHttp ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keep,allowobfuscation @interface dagger.hilt.InstallIn
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ---------- AndroidX / Compose ----------
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---------- Coroutines ----------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ---------- Coil ----------
-dontwarn coil.**

# ---------- Kotlin (базовые правила) ----------
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-dontwarn kotlin.**

# ---------- Media3 / ExoPlayer ----------
-dontwarn androidx.media3.**

# ---------- DataStore ----------
-dontwarn androidx.datastore.**

# ---------- TV Material ----------
-dontwarn androidx.tv.**
