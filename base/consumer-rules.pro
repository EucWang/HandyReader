# Consumer ProGuard rules for the :base module.
# Applied automatically by consuming modules at minification time.

# Toaster (com.hjq:toast) — kept here so it is actually consumed by the app
# (the previous copy in proguard-rules.pro was never applied because library
# modules do not run R8 on themselves).
-keep class com.hjq.toast.** {*;}

# base bean data classes (Book, BookChapter, Bookmark, TtsConfig, ...). These
# flow across module boundaries and BookChapter is also accessed across the
# :mobi JNI boundary. Keeping their fields is cheap insurance against any
# reflective access; the classes themselves may still be obfuscated.
-keep class com.wxn.base.bean.** { <fields>; }

# Logger: kept intact so release builds retain diagnostic logging. R8 was
# removing this class entirely (R8$$REMOVED$$CLASS), making release-only
# issues impossible to diagnose via logcat.
-keep class com.wxn.base.util.Logger { *; }
