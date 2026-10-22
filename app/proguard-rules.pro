# Keep the names of the source files and the line numbers, so a stack trace
# of a release build is still readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room generates the database implementation, the entities are read by name.
-keep class dev.schrammel.brilliantlist.core_data.database.** { *; }

# Glance instantiates the widget receiver through the manifest.
-keep class dev.schrammel.brilliantlist.feature_widget.** { *; }

# SQLCipher loads its native library and uses JNI.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }

# Hilt generates the components, they are looked up by name.
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }

# The workers are instantiated by WorkManager through reflection.
-keep class * extends androidx.work.ListenableWorker { *; }
