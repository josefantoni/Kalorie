-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class antoni.kalorie.**$$serializer { *; }
-keepclassmembers class antoni.kalorie.** {
    *** Companion;
}
-keepclasseswithmembers class antoni.kalorie.** {
    kotlinx.serialization.KSerializer serializer(...);
}
