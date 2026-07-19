# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#指定压缩级别
-optimizationpasses 5

#不跳过非公共的库的类成员
-dontskipnonpubliclibraryclassmembers

#混淆时采用的算法
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

#指定不去忽略非公共的库的类
-dontskipnonpubliclibraryclasses

# 忽略警告（？）
-ignorewarnings

#混淆时不使用大小写混合，混淆后的类名为小写(大小写混淆容易导致class文件相互覆盖）
-dontusemixedcaseclassnames

#优化时允许访问并修改有修饰符的类和类的成员
-allowaccessmodification

#将文件来源重命名为“SourceFile”字符串
#-renamesourcefileattribute SourceFile
#保留行号
-keepattributes SourceFile,LineNumberTable
#保持泛型
-keepattributes Signature
# 保持注解
-keepattributes *Annotation*,InnerClasses

# 保持测试相关的代码
-dontnote junit.framework.**
-dontnote junit.runner.**
-dontwarn android.test.**
-dontwarn android.support.test.**
-dontwarn org.junit.**

# Parcelable
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
# Serializable
-keepnames class * implements java.io.Serializable
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留R下面的资源
-keep class **.R$* {*;}


# 保留四大组件，自定义的Application,Fragment等这些类不被混淆
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Fragment
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

## support
# Note: legacy android.support.* rules removed — the support library is gone
# post-Jetification (the project uses AndroidX), so these matched zero classes.

# 保留枚举类不被混淆
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留本地native方法不被混淆
-keepclasseswithmembers class * {
    native <methods>;
}

# 对于带有回调函数的onXXEvent、**On*Listener的，不能被混淆
-keepclassmembers class * {
    void *(**On*Event);
    void *(**On*Listener);
}

-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

#保留在Activity中的方法参数是view的方法，
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# For XML inflating, keep views' constructoricon.png    自定义view
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# androidx 混淆
# Note: do NOT blanket-keep androidx.** / material.** — they ship their own
# consumer rules, and the broad keep disabled R8 shrinking/obfuscation across
# all of AndroidX (the single biggest drag on the R8 score). Narrow rules for
# specific androidx classes that need it are defined further below.
-dontwarn com.google.android.material.**
-dontnote com.google.android.material.**
-dontwarn androidx.**
-printconfiguration
-keep,allowobfuscation @interface androidx.annotation.Keep

-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

-keep class **.*Binding {*;}
-keep class **.*BindingImpl {*;}

#-keepclassmembers class * {
#    @org.greenrobot.eventbus.Subscribe <methods>;
#}
#-keep enum org.greenrobot.eventbus.ThreadMode { *; }
#
## If using AsyncExecutord, keep required constructor of default event used.
## Adjust the class name if a custom failure event type is used.
#-keepclassmembers class org.greenrobot.eventbus.util.ThrowableFailureEvent {
#    <init>(java.lang.Throwable);
#}
#
## Accessed via reflection, avoid renaming or removal
#-keep class org.greenrobot.eventbus.android.AndroidComponentsImpl

-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener

############################# Retrofit For ketch ################################
# Note: the project uses Ktor (not Retrofit) for HTTP — there is no retrofit2
# dependency and no retrofit2 import anywhere in source or build files. The
# Retrofit-specific keep rules (@retrofit2.http.*, KotlinExtensions) were dead
# code and have been removed. The generic directives below were retained
# because they are not Retrofit-specific and are relied on by other libs:

# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep annotation default values (e.g., retrofit2.http.Field.encoded).
-keepattributes AnnotationDefault

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

############################# Retrofit For ketch ################################

### your config ....

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
   static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
   static **$* *;
}
-keepclassmembers class <2>$<3> {
   kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
   public static ** INSTANCE;
}
-keepclassmembers class <1> {
   public static <1> INSTANCE;
   kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault


# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.auto.service.AutoService
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.AnnotationMirror
-dontwarn javax.lang.model.element.AnnotationValue
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.element.ElementVisitor
-dontwarn javax.lang.model.element.ExecutableElement
-dontwarn javax.lang.model.element.Name
-dontwarn javax.lang.model.element.PackageElement
-dontwarn javax.lang.model.element.TypeElement
-dontwarn javax.lang.model.element.TypeParameterElement
-dontwarn javax.lang.model.element.VariableElement
-dontwarn javax.lang.model.type.ArrayType
-dontwarn javax.lang.model.type.DeclaredType
-dontwarn javax.lang.model.type.ExecutableType
-dontwarn javax.lang.model.type.TypeKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVariable
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.AbstractAnnotationValueVisitor8
-dontwarn javax.lang.model.util.AbstractTypeVisitor8
-dontwarn javax.lang.model.util.ElementFilter
-dontwarn javax.lang.model.util.Elements
-dontwarn javax.lang.model.util.SimpleElementVisitor8
-dontwarn javax.lang.model.util.SimpleTypeVisitor7
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
-dontwarn javax.lang.model.util.Types
-dontwarn javax.tools.Diagnostic$Kind


#beans
# Note: com.wxngame.wordaily.* is a legacy package from a previous project
# (current namespace is com.wxn.reader). No such source exists, so these
# keep rules matched zero classes. Removed.

-keep class androidx.appcompat.widget.SearchView { <init>(...); }

-keepclassmembernames,allowobfuscation,allowshrinking class androidx.appcompat.widget.AppCompatTextViewAutoSizeHelper$Impl* {
  <methods>;
}
-keepclassmembers class * extends com.google.android.gms.internal.measurement.zzkf {
  <fields>;
}
-dontwarn com.google.firebase.platforminfo.KotlinDetector
#-dontwarn com.google.auto.value.AutoValue
#-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.facebook.ads.internal.**
-keeppackagenames com.facebook.*
-keep public class com.facebook.ads.** {
   public protected *;
}

-dontwarn android.security.NetworkSecurityPolicy
# non-required dependencies.
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
#-dontwarn org.jspecify.nullness.NullMarked
-keepclassmembers public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}
-keep class com.google.android.gms.common.internal.ReflectedParcelable
-keepnames class * implements com.google.android.gms.common.internal.ReflectedParcelable
-keepclassmembers class * implements android.os.Parcelable {
  public static final *** CREATOR;
}

# Keep the classes/members we need for client functionality.
#-keep @interface android.support.annotation.Keep
-keep @androidx.annotation.Keep class *
-keepclasseswithmembers class * {
  @androidx.annotation.Keep <fields>;
}
-keepclasseswithmembers class * {
  @androidx.annotation.Keep <methods>;
}

# Keep the names of classes/members we need for client functionality.
-keep @interface com.google.android.gms.common.annotation.KeepName
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
  @com.google.android.gms.common.annotation.KeepName *;
}

# Keep Dynamite API entry points
-keep @interface com.google.android.gms.common.util.DynamiteApi
-keep @com.google.android.gms.common.util.DynamiteApi public class * {
  public <fields>;
  public <methods>;
}
-if public class ** extends androidx.fragment.app.Fragment
-keepclasseswithmembers,allowobfuscation public class <1> {
    public <init>();
}
# Keep all native methods, their classes and any classes in their descriptors
-keepclasseswithmembers,includedescriptorclasses class com.tencent.mmkv.** {
    native <methods>;
    long nativeHandle;
    private static *** onMMKVCRCCheckFail(***);
    private static *** onMMKVFileLengthError(***);
    private static *** mmkvLogImp(...);
    private static *** onContentChangedByOuterProcess(***);
}
#-keepattributes *Annotation*
#-keepclassmembers class * {
#    @org.greenrobot.eventbus.Subscribe <methods>;
#}
#-keep enum org.greenrobot.eventbus.ThreadMode { *; }
#-keepclassmembers class org.greenrobot.eventbus.util.ThrowableFailureEvent {
#    <init>(java.lang.Throwable);
#}
#-keep public class * extends androidx.coordinatorlayout.widget.CoordinatorLayout$Behavior {
#    public <init>(android.content.Context, android.util.AttributeSet);
#    public <init>();
#}
-keepattributes *Annotation*
-keepclassmembers class androidx.vectordrawable.graphics.drawable.VectorDrawableCompat$* {
   void set*(***);
   *** get*();
}
#-keep public class * extends androidx.recyclerview.widget.RecyclerView$LayoutManager {
#    public <init>(android.content.Context, android.util.AttributeSet, int, int);
#    public <init>();
#}
#-keepclassmembers class androidx.recyclerview.widget.RecyclerView {
#    public void suppressLayout(boolean);
#    public boolean isLayoutSuppressed();
#}
#-keepclassmembers class androidx.transition.ChangeBounds$* extends android.animation.AnimatorListenerAdapter {
#  androidx.transition.ChangeBounds$ViewBounds mViewBounds;
#}
-keep class android.support.v4.media.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Prevent Parcelable objects from being removed or renamed.
-keep class androidx.media.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
-keepclassmembernames,allowobfuscation,allowshrinking class androidx.core.view.ViewCompat$Api* {
  <methods>;
}
-keepclassmembernames,allowobfuscation,allowshrinking class androidx.core.view.WindowInsetsCompat$*Impl* {
  <methods>;
}
-keepclassmembernames,allowobfuscation,allowshrinking class androidx.core.app.NotificationCompat$*$Api*Impl {
  <methods>;
}
-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.ViewModel {
    <init>(androidx.lifecycle.SavedStateHandle);
}

-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application,androidx.lifecycle.SavedStateHandle);
}

-keepclassmembers,allowobfuscation class * implements androidx.savedstate.SavedStateRegistry$AutoRecreated {
    <init>();
}
-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.ViewModel {
    <init>();
}

-keepclassmembers,allowobfuscation class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
#-dontwarn com.google.firebase.components.Component$Instantiation
#-dontwarn com.google.firebase.components.Component$ComponentType

-keep class * implements com.google.firebase.components.ComponentRegistrar
-keep,allowshrinking interface com.google.firebase.components.ComponentRegistrar
-keep class * implements androidx.versionedparcelable.VersionedParcelable
-keep public class android.support.**Parcelizer { *; }
-keep public class androidx.**Parcelizer { *; }
-keep public class androidx.versionedparcelable.ParcelImpl
#-keep class * extends androidx.startup.Initializer {
#    # Keep the public no-argument constructor while allowing other methods to be optimized.
#    <init>();
#}
#
#-assumenosideeffects class androidx.startup.StartupLogger
#-keepattributes AnnotationDefault,
#                RuntimeVisibleAnnotations,
#                RuntimeVisibleParameterAnnotations,
#                RuntimeVisibleTypeAnnotations

-keepclassmembers enum androidx.lifecycle.Lifecycle$Event {
    <fields>;
}

#-keepclassmembers class * extends androidx.lifecycle.EmptyActivityLifecycleCallbacks { *; }
-keepclassmembernames,allowobfuscation,allowshrinking class androidx.core.os.UserHandleCompat$Api*Impl {
  <methods>;
}
-keepclassmembernames,allowobfuscation,allowshrinking class androidx.core.widget.EdgeEffectCompat$Api*Impl {
  <methods>;
}
# Accessed via reflection, avoid renaming or removal
#-keep class org.greenrobot.eventbus.android.AndroidComponentsImpl


# Needed when building against Marshmallow SDK.
-dontwarn android.app.Notification
-dontwarn com.google.android.apps.common.proguard.UsedBy*
# Protobuf has references not on the Android boot classpath
#-dontwarn sun.misc.Unsafe
#-dontwarn libcore.io.Memory
-keepclassmembers class * extends com.google.android.gms.internal.measurement.zzkf {
  <fields>;
}
#-keepclassmembers class com.google.android.gms.common.api.internal.BasePendingResult {
#  com.google.android.gms.common.api.internal.BasePendingResult$ReleasableResultGuardian mResultGuardian;
#}

##---------------Begin: proguard configuration for Gson  ----------
# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-dontwarn sun.misc.**
#-keep class com.google.gson.stream.** { *; }

# Application classes that will be serialized/deserialized over Gson
-keep class com.wxn.reader.data.dto.** { <fields>; }

-keep class com.wxn.reader.data.mapper.annotation.** { <fields>; }
-keep class com.wxn.reader.data.mapper.book.** { <fields>; }
-keep class com.wxn.reader.data.mapper.bookmark.** { <fields>; }
-keep class com.wxn.reader.data.mapper.bookshelf.** { <fields>; }
-keep class com.wxn.reader.data.mapper.note.** { <fields>; }
-keep class com.wxn.reader.data.mapper.readingactive.** { <fields>; }
-keep class com.wxn.reader.data.mapper.shelf.** { <fields>; }

-keep class com.wxn.reader.data.model.** { <fields>; }
-keep class com.wxn.reader.data.repository.** { <fields>; }
-keep class com.wxn.reader.data.source.local.** { <fields>; }
-keep class com.wxn.reader.data.source.local.dao.** { <fields>; }

-keep class com.wxn.reader.di.** { <fields>; }
-keep class com.wxn.reader.domain.model.** { <fields>; }

-keep class com.wxn.reader.navigation.** { <fields>; }
-keep interface com.wxn.reader.domain.repository.** { <fields>; }

# Hilt ProGuard rules
# Note: dagger.hilt / dagger ships its own consumer rules; only the generated
# entry point needs an explicit keep here.
-keep class * implements dagger.hilt.internal.GeneratedEntryPoint { *; }

# 保留协程状态相关代码
-keepclassmembers class kotlin.coroutines.jvm.internal.** {
*;
}
# 保留挂起函数
-keepclassmembers class * {
kotlin.coroutines.Continuation suspend*(...);
}

# Keep all the functions created to throw an exception. We don't want these functions to be
# inlined in any way, which R8 will do by default. The whole point of these functions is to
# reduce the amount of code generated at the call site.
-keepclassmembers,allowshrinking,allowobfuscation class androidx.compose.**.* {
    static void throw*Exception(...);
    static void throw*ExceptionForNullCheck(...);
    # For methods returning Nothing
    static java.lang.Void throw*Exception(...);
    static java.lang.Void throw*ExceptionForNullCheck(...);
}
-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
    void sourceInformation(androidx.compose.runtime.Composer,java.lang.String);
    void sourceInformationMarkerStart(androidx.compose.runtime.Composer,int,java.lang.String);
    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
}
# Composer's class initializer doesn't do anything but create an EMPTY object. Marking the
# initializers as having no side effects can help encourage shrinkers to merge/devirtualize Composer
# with ComposerImpl.
-assumenosideeffects public class androidx.compose.runtime.Composer {
    void <clinit>();
}
-assumenosideeffects public class androidx.compose.runtime.ComposerImpl {
    void <clinit>();
}
# Keep all the functions created to throw an exception. We don't want these functions to be
# inlined in any way, which R8 will do by default. The whole point of these functions is to
# reduce the amount of code generated at the call site.
-keepclassmembers,allowshrinking,allowobfuscation class androidx.compose.runtime.** {
    # java.lang.Void == methods that return Nothing
    static void throw*Exception(...);
    static void throw*ExceptionForNullCheck(...);
    static java.lang.Void throw*Exception(...);
    static java.lang.Void throw*ExceptionForNullCheck(...);

    # For functions generating error messages
    static java.lang.String exceptionMessage*(...);
    java.lang.String exceptionMessage*(...);

    static void compose*RuntimeError(...);
    static java.lang.Void compose*RuntimeError(...);
}
#noinspection ShrinkerUnresolvedReference
-if public class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static *** getLocalLifecycleOwner();
}
-keep public class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt {
    public static *** getLocalLifecycleOwner();
}

-keepclassmembers class androidx.compose.ui.platform.ViewLayerContainer {
    protected void dispatchGetDisplayList();
}

-keepclassmembers class androidx.compose.ui.platform.AndroidComposeView {
    android.view.View findViewByAccessibilityIdTraversal(int);
}

-keepnames class androidx.compose.ui.input.pointer.PointerInputEventHandler {
    *;
}
-keepclasseswithmembers class androidx.graphics.path.** {
    native <methods>;
}

-keepattributes AnnotationDefault,
                RuntimeVisibleAnnotations,
                RuntimeVisibleParameterAnnotations,
                RuntimeVisibleTypeAnnotations

# (De-duplicated: Lifecycle$Event enum <fields> keep removed — identical rule at line ~415.)
-keep class * implements androidx.lifecycle.GeneratedAdapter {
    <init>(...);
}
-keepclassmembers class ** {
    @androidx.lifecycle.OnLifecycleEvent *;
}
# The deprecated `android.app.Fragment` creates `Fragment` instances using reflection.
# See: b/338958225, b/341537875
-keepclasseswithmembers,allowobfuscation public class androidx.lifecycle.ReportFragment {
    public <init>();
}
# this rule is need to work properly when app is compiled with api 28, see b/142778206
# Also this rule prevents registerIn from being inlined.
-keepclassmembers class androidx.lifecycle.ReportFragment$LifecycleCallbacks { *; }
# Allow R8 to optimize away the FastServiceLoader.
# Together with ServiceLoader optimization in R8
# this results in direct instantiation when loading Dispatchers.Main
-assumenosideeffects class kotlinx.coroutines.internal.MainDispatcherLoader {
    boolean FAST_SERVICE_LOADER_ENABLED return false;
}

-assumenosideeffects class kotlinx.coroutines.internal.FastServiceLoaderKt {
    boolean ANDROID_DETECTED return true;
}

# Disable support for "Missing Main Dispatcher", since we always have Android main dispatcher
-assumenosideeffects class kotlinx.coroutines.internal.MainDispatchersKt {
    boolean SUPPORT_MISSING return false;
}

# Statically turn off all debugging facilities and assertions
-assumenosideeffects class kotlinx.coroutines.DebugKt {
    boolean getASSERTIONS_ENABLED() return false;
    boolean getDEBUG() return false;
    boolean getRECOVER_STACK_TRACES() return false;
}
# Most of volatile fields are updated with AtomicFU and should not be mangled/removed
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

-keepclassmembernames class io.ktor.** {
    volatile <fields>;
}

# client engines are loaded using ServiceLoader so we need to keep them
-keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer
# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
# Same story for the standard library's SafeContinuation that also uses AtomicReferenceFieldUpdater
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}
# These classes are only required by kotlinx.coroutines.debug.internal.AgentPremain, which is only loaded when
# kotlinx-coroutines-core is used as a Java agent, so these are not needed in contexts where ProGuard is used.
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn sun.misc.SignalHandler
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal

# Only used in `kotlinx.coroutines.internal.ExceptionsConstructor`.
# The case when it is not available is hidden in a `try`-`catch`, as well as a check for Android.
-dontwarn java.lang.ClassValue

# An annotation used for build tooling, won't be directly accessed.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
# We keep all fields for every generated proto file as the runtime uses
# reflection over them that ProGuard cannot detect. Without this keep
# rule, fields may be removed that would cause runtime failures.
-keepclassmembers class * extends com.google.android.gms.internal.measurement.zzme {
  <fields>;
}

# Needed when building against pre-Marshmallow SDK.
-dontwarn android.security.NetworkSecurityPolicy

# Needed when building against Marshmallow SDK.
-dontwarn android.app.Notification

# Protobuf has references not on the Android boot classpath
-dontwarn sun.misc.Unsafe
-dontwarn libcore.io.Memory

# Annotations used during internal SDK shrinking.
-dontwarn com.google.android.apps.common.proguard.UsedBy*
-dontwarn com.google.android.apps.common.proguard.SideEffectFree
# Annotations referenced by the SDK but whose definitions are contained in
# non-required dependencies.
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.jspecify.annotations.NullMarked
# Annotations no longer exist. Suppression prevents ProGuard failures in
# SDKs which depend on earlier versions of play-services-basement.
-dontwarn com.google.android.gms.common.util.VisibleForTesting
# Keep SafeParcelable NULL value, needed for reflection by DowngradeableSafeParcel
-keepclassmembers public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
    public static final *** NULL;
}

# Needed for Parcelable/SafeParcelable classes \u0026 their creators to not get renamed, as they are
# found via reflection.
# (De-duplicated: the identical ReflectedParcelable/CREATOR/KeepName/DynamiteApi
# block that was pasted further down has been removed — keep the canonical copy above.)

# FragmentTransition will reflectively lookup:
# androidx.transition.FragmentTransitionSupport
# We should ensure that we keep the constructor if the code using this is alive
-keep class androidx.transition.FragmentTransitionSupport {
    public <init>();
}
-dontwarn org.jetbrains.annotations.**
# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.

# Keep `serializer()` on companion objects (both default and named) of serializable classes.

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Don't print notes about potential mistakes or omissions in the configuration for kotlinx-serialization classes
# See also https://github.com/Kotlin/kotlinx.serialization/issues/1900
-dontnote kotlinx.serialization.**

# Serialization core uses `java.lang.ClassValue` for caching inside these specified classes.
# If there is no `java.lang.ClassValue` (for example, in Android), then R8/ProGuard will print a warning.
# However, since in this case they will not be used, we can disable these warnings
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# disable optimisation for descriptor field because in some versions of ProGuard, optimization generates incorrect bytecode that causes a verification error
# see https://github.com/Kotlin/kotlinx.serialization/issues/2719
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}

-keepnames class * extends androidx.startup.Initializer
-keep class * extends androidx.startup.Initializer {
    # Keep the public no-argument constructor while allowing other methods to be optimized.
    <init>();
}

-assumenosideeffects class androidx.startup.StartupLogger { public static <methods>; }
-keep class * implements androidx.versionedparcelable.VersionedParcelable
-keep public class android.support.**Parcelizer { *; }
-keep public class androidx.**Parcelizer { *; }
-keep public class androidx.versionedparcelable.ParcelImpl
-dontwarn com.google.firebase.components.Component$Instantiation
-dontwarn com.google.firebase.components.Component$ComponentType
# (De-duplicated: ComponentRegistrar keep removed — identical rules at lines ~398-399.)
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
# (De-duplicated: the @interface androidx.annotation.Keep annotation-interface keep
# above (line ~136) is identical; kept the strongest @Keep class rule `{*;}` here
# as the canonical member-coverage rule, which subsumes the narrower keeps at ~138-141
# and ~298-304. Those are left in place as they are harmless and additive.)
-keep @androidx.annotation.Keep class * {*;}

-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}

-keepclassmembers,allowobfuscation class * {
  @androidx.annotation.DoNotInline <methods>;
}
# Keep Metadata annotations so they can be parsed at runtime.
-keep class kotlin.Metadata { *; }
# Keep generic signatures and annotations at runtime.
# R8 requires InnerClasses and EnclosingMethod if you keepattributes Signature.
-keepattributes InnerClasses,Signature,RuntimeVisible*Annotations,EnclosingMethod
# Don't note on API calls from different JVM versions as they're gated properly at runtime.
-dontnote kotlin.internal.PlatformImplementationsKt

# Don't note on internal APIs, as there is some class relocating that shrinkers may unnecessarily find suspicious.
-dontwarn kotlin.reflect.jvm.internal.**

# Do not even execute try-catch block for ClassValue
-assumenosideeffects class kotlin.reflect.jvm.internal.CacheByClassKt {
    boolean useClassValue return false;
}
-identifiernamestring @dagger.internal.IdentifierNameString class ** {
    static java.lang.String *;
}
# PdfBox-Android does reflection to instantiate SecurityHandlers.
-keep,allowobfuscation class * extends com.tom_roush.pdfbox.pdmodel.encryption.SecurityHandler {
   public <init>(...);
}

-keep,allowobfuscation class com.tom_roush.pdfbox.pdmodel.documentinterchange.** { *; }
-keep,allowobfuscation,allowshrinking class com.wxn.reader.SplashViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.audioBookReader.AudiobookReaderViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.bookDetails.BookDetailsViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.gettingStarted.GettingStartedViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.home.HomeViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.mainReader.MainReadViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.notes.NotesViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.onlineBooks.WebViewScreenViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.pdfReader.PdfReaderViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.settings.SettingsViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.settings.viewmodels.AboutViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.settings.viewmodels.DeletedBooksViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.settings.viewmodels.SpeakerViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.settings.viewmodels.ThemeViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.sharedComponents.CustomNavigationViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.sharedComponents.PremiumViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.shelves.ShelvesViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.presentation.statistics.StatisticsViewModel
-keep,allowobfuscation,allowshrinking class com.wxn.reader.ui.theme.AppThemeViewModel



# ---------------------------------------------------------------------------
# Module-specific keep rules migrated to each module's consumer-rules.pro so
# they are applied automatically when the app consumes the module:
#   - :mobi          -> mobi/consumer-rules.pro        (JNI: NativeLib + data.model fields + BookChapter)
#   - :bookparser    -> bookparser/consumer-rules.pro  (Hilt @Inject ctors + ParserModule)
#   - :bookread      -> bookread/consumer-rules.pro    (DataStore @Inject ctors; custom Views covered by global View rule)
#   - :text2speech   -> text2speech/consumer-rules.pro (sherpa-onnx JNI + net.gotev.speech)
#   - :jp2forandroid -> jp2forandroid/consumer-rules.pro (OpenJPEG JNI)
#   - :base          -> base/consumer-rules.pro        (Toaster + bean fields)
# The broad com.wxn.<module>.** { *; } keeps that used to live here were removed
# so R8 can shrink/obfuscate non-reflective code inside those modules. Each
# module now declares only the minimal surface it needs (JNI classes, @Inject
# ctors, reflectively-loaded Views). Verify with a release build + smoke test.
# ---------------------------------------------------------------------------

# 保留TTS服务相关类
-keep class com.wxn.reader.service.TtsPlaybackService { *; }
-keep class * extends androidx.media3.session.MediaSessionService

# ---------------------------------------------------------------------------
# 一方模块 ProGuard keep 规则
# ---------------------------------------------------------------------------
# 原则：凡用于数据库(Room @Entity)、JSON 序列化(@Serializable)、JNI 数据传递、
# 网络传输(DTO)、Parcelable 跨进程传递的 bean/entity 实体类，保持原样不混淆/不缩减，
# 防止字段名/类名被 R8 改写导致反射访问失败。
#
# 另：阅读内容加载链路（ChapterProvider 分页引擎 + TextParser 协程状态机 +
# mobi format parsers）经实测对 R8 的 suspend-coroutine 内联优化敏感（EPUB/MOBI/FB2
# 卡 Loading），故一并保留。
# ---------------------------------------------------------------------------

# === A. Room 数据库实体（@Entity / @Embedded / DAO POJO）===
# app/data/dto 下的 25 个 @Entity 类 + BookListItemEntity + ModelWithSpeakers
-keep class com.wxn.reader.data.dto.** { *; }
# 内联定义在 SpeakerRepository 中的 Voice 实体
-keep class com.wxn.reader.util.tts.repository.Voice { *; }

# === B. @Serializable 类（kotlinx.serialization JSON 序列化）===
# 备份模型
-keep class com.wxn.reader.data.model.backup.** { *; }
# OPDS 目录模型（含 @Serializable 和 XML 解析的 data class）
-keep class com.wxn.reader.data.model.opds.** { *; }
# 字体目录 / 词典 / 依赖索引 / 阅读背景 / TTS 模型列表
-keep class com.wxn.reader.data.model.FontCatalog { *; }
-keep class com.wxn.reader.data.model.FontCatalogItem { *; }
-keep class com.wxn.reader.data.model.FontVariantItem { *; }
-keep class com.wxn.reader.data.model.WordResult { *; }
-keep class com.wxn.reader.data.model.PhoneticItem { *; }
-keep class com.wxn.reader.data.model.DictDefinition { *; }
-keep class com.wxn.reader.domain.model.DependencyIndex { *; }
-keep class com.wxn.reader.domain.model.DependencyMetadata { *; }
-keep class com.wxn.reader.domain.model.DownloadMetadata { *; }
-keep class com.wxn.reader.domain.model.ReadBgData { *; }
-keep class com.wxn.reader.domain.model.ReadBgList { *; }
-keep class com.wxn.reader.domain.model.TTModelBaseData { *; }
-keep class com.wxn.reader.domain.model.TTSModelData { *; }
-keep class com.wxn.reader.domain.model.TTSModelsList { *; }
# 网络请求/响应 DTO
-keep class com.wxn.reader.data.remote.auth.** { *; }
-keep class com.wxn.reader.data.remote.dto.** { *; }
# 同步规范化记录
-keep class com.wxn.reader.data.remote.sync.canonical.** { *; }
# TTS 数据
-keep class com.wxn.reader.util.tts.data.** { *; }

# === C. JNI 数据传递类（C++ 通过 JNI 反射按字段名/类名/构造函数签名访问）===
# mobi 模块：MetaInfo / CountPair / ParagraphData（C++ appmobi.cpp FindClass + GetMethodID）
-keep class com.wxn.mobi.data.model.** { *; }
# base 模块：BookChapter（JNI 参数类型）+ TextTag（JNI 内嵌）
-keep class com.wxn.base.bean.BookChapter { *; }
-keep class com.wxn.base.bean.TextTag { *; }
# JNI 桥接 object（external fun 绑定 C++ 符号名）
-keep class com.wxn.mobi.inative.NativeLib { *; }

# === D. base.bean 数据类（跨模块共享 bean + ReaderText sealed 层级）===
-keep class com.wxn.base.bean.** { *; }
# base.bean.sync（HLC 时间戳，同步序列化）
-keep class com.wxn.base.bean.sync.** { *; }

# === E. bookread 数据/配置/偏好模型 ===
# TextChapter/TextPage/TextLine/TextChar 分页模型 + beans
-keep class com.wxn.bookread.data.model.** { *; }
-keep class com.wxn.bookread.data.beans.** { *; }
# 阅读偏好（DataStore 持久化）
-keep class com.wxn.bookread.data.model.preference.** { *; }

# === F. @Parcelize / Parcelable 类（跨进程传递，CREATOR 字段不可混淆）===
-keep @kotlinx.parcelize.Parcelize class * { *; }
-keepnames @kotlinx.parcelize.Parcelize class *
-keepclassmembers class * {
    public static final ** CREATOR;
}

# === G. kotlinx.serialization 补充（保留 $serializer 生成的序列化器）===
# 保留 @Serializable 类的 Companion 及其 serializer() 方法，防止 R8 移除导致
# 反序列化时找不到 KSerializer。
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
# 保留 @Serializable 类本身（类名 + 字段，防止反序列化失败）
-keep @kotlinx.serialization.Serializable class * { *; }

# === H. 阅读内容加载链路（功能性 keep——R8 suspend 协程优化敏感）===
# ChapterProvider：分页排版引擎（含 v5 双列逻辑），R8 内联其 LayoutBounds 工厂方法
# 后导致 EPUB/MOBI/FB2 卡 Loading
-keep class com.wxn.bookread.provider.ChapterProvider { *; }
-keep class com.wxn.bookread.provider.ChapterProvider$* { *; }
# ContentTextView / PageView：自定义阅读 View（反射实例化）
-keep class com.wxn.bookread.ui.** { *; }
# TextParser 接口及其 suspend 实现（协程状态机内联导致正文数据不渲染）
-keep class com.wxn.bookparser.TextParser { *; }
-keep class com.wxn.bookparser.impl.** { *; }
-keep class com.wxn.bookparser.parser.epub.** { *; }
-keep class com.wxn.bookparser.parser.mobi.** { *; }
-keep class com.wxn.bookparser.parser.fb2.** { *; }
-keep class com.wxn.bookparser.parser.html.** { *; }
-keep class com.wxn.bookparser.domain.** { *; }
# mobi 格式 parser object（parsedChapterData 转换逻辑）
-keep class com.wxn.mobi.EpubParser { *; }
-keep class com.wxn.mobi.MobiParser { *; }
-keep class com.wxn.mobi.Fb2Parser { *; }
-keep class com.wxn.mobi.HtmlParser { *; }


-keepattributes *Annotation*
-keep class kotlinx.serialization.json.** { *; }

# Fix for AndroidRuntime log spam/errors related to java.lang.Object methods
-keepclassmembers class java.lang.Object {
    *** internalClone();
    *** clone();
    *** equals(...);
    *** finalize();
    *** getClass();
}

-obfuscationdictionary bt-proguard.txt
-classobfuscationdictionary bt-proguard.txt
-packageobfuscationdictionary bt-proguard.txt