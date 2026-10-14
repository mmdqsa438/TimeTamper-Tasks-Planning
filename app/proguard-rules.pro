# ── kotlinx.serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.taskmanager.**$$serializer { *; }
-keepclassmembers class com.example.taskmanager.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.taskmanager.** {
    kotlinx.serialization.KSerializer serializer(...);
}
