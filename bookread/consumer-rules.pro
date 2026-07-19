# Consumer ProGuard rules for the :bookread module.
# Applied automatically by consuming modules (the :app) at minification time.

# Custom Views (ContentTextView, PageView, ContentView) are inflated from XML
# or instantiated by name. They are already covered by the app's global rule
# (-keep public class * extends android.view.View { <init>(...) }), so no
# additional rule is needed here for them.

# DataStore Preferences wrappers in data.source.local are @Inject-constructed
# (Hilt). Keep only their @Inject constructors; members are statically resolved.
-keepclassmembers,allowobfuscation class com.wxn.bookread.data.source.local.** {
    @javax.inject.Inject <init>(...);
}
