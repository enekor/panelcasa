# kotlinx.serialization: conservar los serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.n3k0chan.panelcasa.**$$serializer { *; }
-keepclassmembers class com.n3k0chan.panelcasa.** {
    *** Companion;
}
-keepclasseswithmembers class com.n3k0chan.panelcasa.** {
    kotlinx.serialization.KSerializer serializer(...);
}
