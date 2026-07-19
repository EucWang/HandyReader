# Consumer ProGuard rules for the :mobi module.
# Applied automatically by consuming modules (the :app) at minification time.

# JNI bridge: com.wxn.mobi.inative.NativeLib (object) declares 9 external fun
# methods (loadMobiNative, loadEpubNative, getChapterNative, ...) bound by name
# to C++ symbols in src/main/cpp. The class and its native methods must not be
# renamed or removed.
-keep class com.wxn.mobi.inative.NativeLib { *; }

# Data classes crossing the JNI boundary as parameters / return values.
# R8 must keep their field names so the C++ side can read/write them via JNI.
-keep class com.wxn.mobi.data.model.** { <fields>; }

# com.wxn.base.bean.BookChapter is passed across the mobi JNI boundary but lives
# in the :base module. Keep its fields for JNI field access.
-keep class com.wxn.base.bean.BookChapter { <fields>; }

# Format parsers (EpubParser/MobiParser/Fb2Parser/HtmlParser) are object singletons
# invoked from bookparser's TextParser impls. R8 aggressively inlines their methods
# and removes the classes (R8$$REMOVED$$CLASS), which caused EPUB/MOBI/FB2 books to
# hang at the loading screen in release builds (the inlined suspend-coroutine state
# machine for parsedChapterData breaks). Keeping the classes intact (not inlined)
# prevents this. Do NOT narrow further without a full release smoke test of all formats.
-keep class com.wxn.mobi.EpubParser { *; }
-keep class com.wxn.mobi.MobiParser { *; }
-keep class com.wxn.mobi.Fb2Parser { *; }
-keep class com.wxn.mobi.HtmlParser { *; }
