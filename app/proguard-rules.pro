# Swara release shrinking rules.
# Media3, Room, Glide and AndroidX ship their own consumer rules; only app-specific
# needs are listed here.

# Keep line numbers so release crash stack traces are readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room entities are read/written by generated code; keep their fields stable.
-keep class com.psthetech.swara.data.db.entity.** { *; }
