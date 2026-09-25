# R8 rules for the release (and staging) build. Enabled 2026-09-25, Play-readiness audit.
#
# Most libraries ship their own consumer rules inside their AARs/JARs (Room, Hilt/Dagger,
# WorkManager, Glance, DataStore, Health Connect, CameraX, MapLibre, Coil, OkHttp and
# kotlinx.serialization all do), so this file only covers what those cannot know about.

# Keep file names and line numbers in stack traces. Play de-obfuscates class and method names from
# the mapping file inside the AAB; without these two lines every frame would read "Unknown Source".
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enum names are persisted: Room type converters and the backup DTOs store Enum.name and read it
# back with valueOf(). R8 keeps the string passed to each constant's constructor, so stored names
# survive renaming, but it must never unbox these enums to ints or drop valueOf/values.
-keepclassmembers enum com.enil.logez.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public final java.lang.String name();
}

# The licences screen and the exercise seed are decoded with kotlinx.serialization from JSON whose
# keys are the Kotlin property names. The library's bundled rules keep generated serializers; these
# lines keep the @Serializable model classes' property names intact as well, belt and braces for
# code decoded only by reflection-free generated serializers that R8 could otherwise inline away.
-keepclassmembers @kotlinx.serialization.Serializable class com.enil.logez.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
