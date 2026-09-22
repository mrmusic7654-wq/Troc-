# Add project specific ProGuard rules here.
# Troc ships with minification disabled by default; these rules keep the
# reflection-heavy libraries safe if you choose to enable R8.

# --- Kotlinx serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.troc.**$$serializer { *; }
-keepclassmembers class com.example.troc.** { *** Companion; }
-keepclasseswithmembers class com.example.troc.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Retrofit ---
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# --- OkHttp ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Rhino (JavaScript sandbox) ---
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
