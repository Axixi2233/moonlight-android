# Keep the public Moonlight host stable while allowing private implementation
# packages and third-party internals to be renamed and optimized.
-keep,allowoptimization,allowshrinking class com.limelight.** { *; }

# Collapse obfuscated private implementation classes into a neutral package and
# avoid leaking their original source filenames.
-repackageclasses 'p'
-renamesourcefileattribute SourceFile
