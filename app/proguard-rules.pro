# kotlinx.serialization: generowane serializatory sa uzywane przez refleksje.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class pl.fwdrucik.sklep.** {
    *** Companion;
}
-keepclasseswithmembers class pl.fwdrucik.sklep.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pl.fwdrucik.sklep.**$$serializer { *; }

# Retrofit trzyma sygnatury metod interfejsu w metadanych.
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation interface retrofit2.Call
-keep,allowobfuscation class retrofit2.Response
