# Consumer ProGuard rules for the :bookparser module.
# Applied automatically by consuming modules (the :app) at minification time.

# Hilt @Module: ParserModule (object) declares 19 @Provides @Singleton functions
# that are referenced by Hilt-generated code. Keep the module intact.
-keep class com.wxn.bookparser.di.ParserModule { *; }

# @Inject-annotated constructors of parser implementations are instantiated
# reflectively by Hilt/Dagger generated factories. Keep only the constructors;
# the classes themselves may still be obfuscated/shrunk otherwise.
-keepclassmembers,allowobfuscation class com.wxn.bookparser.** {
    @javax.inject.Inject <init>(...);
}

# TextParser interface and its suspend methods (parseChapterInfo, parsedChapterData,
# getWordCount, close) are dispatched polymorphically at runtime. R8's aggressive
# inlining of these suspend-coroutine state machines caused EPUB/MOBI/FB2 to hang
# at the loading screen in release builds. Keep the interface and all implementations
# intact (no inlining/removal) until the exact R8 optimization trigger is identified.
-keep class com.wxn.bookparser.TextParser { *; }
-keep class com.wxn.bookparser.impl.** { *; }
-keep class com.wxn.bookparser.parser.epub.** { *; }
-keep class com.wxn.bookparser.parser.mobi.** { *; }
-keep class com.wxn.bookparser.parser.fb2.** { *; }
-keep class com.wxn.bookparser.parser.html.** { *; }
-keep class com.wxn.bookparser.domain.** { *; }
