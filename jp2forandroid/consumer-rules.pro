# Consumer ProGuard rules for the :jp2forandroid module.
# Applied automatically by consuming modules at minification time.

# OpenJPEG JNI bridge: JP2Decoder declares private static native methods
# (decodeJP2File, decodeJP2ByteArray, readJP2HeaderFile, ...) bound by name to
# C++ symbols in src/main/cpp. Keep the package so JNI symbol lookup succeeds.
-keep class com.gemalto.jp2.** { *; }
