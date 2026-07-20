import org.gradle.kotlin.dsl.implementation
import java.text.SimpleDateFormat
import java.util.Date
import java.io.FileInputStream
import java.util.Properties


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("kotlin-parcelize")

    id("com.mikepenz.aboutlibraries.plugin")
    alias(libs.plugins.google.gms.google.services)
    id("kotlinx-serialization")
    // Add the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics")

    id("androidx.room")

    id("io.sentry.android.gradle") version "6.1.0"
}

val apikeyPropertiesFile = rootProject.file("key.properties")
val apikeyProperties = Properties().apply {
    if (apikeyPropertiesFile.exists()) {
        apikeyPropertiesFile.inputStream().use { load(it) }
    }
}

// 是否上传 ProGuard/源码映射到 Sentry 与 Firebase Crashlytics。
// 受限网络环境默认关闭，需要上传时用 -PuploadMapping=true 打开。
val uploadMapping = (project.findProperty("uploadMapping") as? String)?.equals("true", ignoreCase = true) ?: false

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.wxn.reader"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    // 与 mobi/jp2forandroid 模块保持同一 NDK，确保下方 strip 任务解析到正确的 llvm-objcopy。
    ndkVersion = "29.0.13599879 rc2"

    defaultConfig {
        applicationId = "com.wxn.reader"
        minSdk = 23
        targetSdk = 35
        versionCode = 24
        versionName = "1.20.260718"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "RELEASE_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"")
        buildConfigField("String", "FEEDBACK_API_URL", "\"https://handyreader.top\"")
        buildConfigField("String", "API_KEY", "\"${apikeyProperties.getProperty("apiKey", "")}\"")
        buildConfigField("String", "EDGE_TTS_KEY", "\"${apikeyProperties.getProperty("EDGE_TTS_API_KEY", "")}\"")
    }

    signingConfigs {
        create("release") {
            val storeFileProp = apikeyProperties.getProperty("storeFile", "")
            if (storeFileProp.isNotEmpty()) {
                storeFile = rootProject.file(
                    storeFileProp
                        .removePrefix("file(").removeSuffix(")")
                        .trim('"').removePrefix("./")
                )
            }
            storePassword = apikeyProperties.getProperty("storePassword", "")
            keyAlias = apikeyProperties.getProperty("keyAlias", "")
            keyPassword = apikeyProperties.getProperty("keyPassword", "")
            enableV1Signing = apikeyProperties.getProperty("enableV1Signing", "true").toBoolean()
            enableV2Signing = apikeyProperties.getProperty("enableV2Signing", "true").toBoolean()
        }
    }

    buildTypes {
        release {
            buildConfigField("String", "RELEASE_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"")

            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                // SYMBOL_TABLE: AGP's stripReleaseDebugSymbols now actually strips
                // the .debug_* sections (FULL was confirmed to make strip a no-op,
                // bloating the APK with ~20MB of debug info per ABI). The .symtab is
                // retained, so native crash stacks still show function names.
                // (Line numbers are lost for first-party C++ libs until a separate
                // full-debug symbol upload pipeline is wired up — see Crashlytics
                // block below + run -PuploadMapping=true to enable symbol upload.)
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            // Upload native symbols to Crashlytics so first-party C++ crashes
            // (libappmobi/libmobi/libcssparser/libopenjpeg) are symbolicated
            // with function names AND line numbers. Run with -PuploadMapping=true
            // to also enable the mapping/symbol upload tasks.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
                // "breakpad" is the default symbolGeneratorType in plugin 3.x;
                // stated explicitly for clarity. Breakpad reads the unstripped
                // .so (kept by debugSymbolLevel = FULL) to emit .sym files with
                // line numbers, uploaded separately to Firebase.
                symbolGeneratorType = "breakpad"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 添加后缀，使 debug 包的应用ID变成 com.your.app.debug
            applicationIdSuffix = ".debug"
            buildConfigField("String", "RELEASE_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"")

            isMinifyEnabled = false
            isShrinkResources = false
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
    // Note: the Compose compiler extension version is managed by the
    // org.jetbrains.kotlin.plugin.compose Gradle plugin (alias(libs.plugins.compose.compiler)).
    // The legacy composeOptions { kotlinCompilerExtensionVersion = ... } DSL is
    // ignored when that plugin is applied, so it was removed to avoid the stale
    // 1.5.17 value (which belonged to the Kotlin 1.9.x era) misleading readers.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/schemas/**"
//            excludes += "META-INF/DEPENDENCIES"
            excludes += "/META-INF/gradle/incremental.annotation.processors"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // androidTest assets：让 MigrationTestHelper 通过 classpath 读取 schema JSON
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    //aab 包需要配置 ，多语言情况下，部分包，否则会导致多语言切换失效问题
    bundle {
        language {
            enableSplit = false//language enableSplit = false代表aab不进行分包处理
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    // APK 分包：仅为 release 产物按 ABI 拆分，减小 GitHub Releases 直接发布的 APK 体积。
    // 保留 arm64-v8a（64 位 ARM，覆盖绝大多数现代设备）与 armeabi-v7a（32 位 ARM，覆盖旧设备）。
    // 裁掉 x86/x86_64（基本仅模拟器/极少数 Intel 设备使用）。
    // 注意：此 splits 只影响 assembleRelease / packageRelease 的 APK 输出，不影响 bundleRelease 的 AAB（AAB 由上方
    // bundle.abi.enableSplit 控制，Play Store 仍按设备 ABI 下发），也不影响 debug（debug 无需配置即可含全 ABI 供模拟器）。
    // 关键：bundleRelease 任务名同样包含 "Release" 字样，必须显式排除 bundle* 任务，否则构建 AAB 会报
    // "Multiple shrunk-resources files found ... Please disable building multiple APKs when building an Android app bundle"
    // 参见 https://issuetracker.google.com/402800800
    splits {
        abi {
            val taskNames = gradle.startParameter.taskNames.map { it.lowercase() }
            val isApkRelease = taskNames.any { task ->
                // 仅匹配产出 APK 的任务（assemble / package / install），避免误触 bundleRelease
                (task.contains("assemble") || task.contains("package") || task.contains("install")) &&
                    task.contains("release")
            }
            val isBundle = taskNames.any { it.contains("bundle") }

            isEnable = isApkRelease && !isBundle

            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    // 针对应用变体进行配置
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val versionName = variant.versionName ?: ""
                val versionCode = variant.versionCode
                val variantName = variant.name // 例如 "release" 或 "debug"
                // splits.abi 开启后，每个 ABI 产出一个独立 APK；将 ABI 标识写入文件名以便区分。
                // output.filters 在非分包构建时为空，此时回退到原命名（兼容 debug 单 APK）。
                val abiFilter = output.filters.firstOrNull { it.filterType == "ABI" }?.identifier
                val abiPart = if (abiFilter != null) "_${abiFilter}" else ""
                val newName = "handyreader_${variantName}${abiPart}_v${versionName}_${versionCode}.apk"
                output.outputFileName = newName
            }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("kotlin.RequiresOptIn")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 一方原生库调试符号剥离
// ─────────────────────────────────────────────────────────────────────────────
// 背景：AGP 9.x 的 stripReleaseDebugSymbols 任务对「来自 library 模块依赖的 .so」
// 不可靠地执行剥离（issuetracker 195318431 / 37120201）。本项目原生代码在 mobi 与
// jp2forandroid 两个 library 模块（CMake 产物），它们经 copyReleaseJniLibsProjectOnly
// 导出的仍是未剥离的 RelWithDebInfo 产物（带 .debug_info/.debug_line 等段），导致
// 最终 APK 每个架构多带 ~20MB 调试段。debugSymbolLevel = "SYMBOL_TABLE" 只绑定模块
// 自己的 externalNativeBuild，对 library 依赖的 .so 不生效，故在此显式补一刀。
//
// 做法：在 AGP 自己的 stripReleaseDebugSymbols 任务上挂 doLast——该任务已依赖
// mergeReleaseNativeLibs，且其输出目录（stripped_native_libs/.../out）正是打包任务的
// 输入。AGP 的 strip 对 library .so 是 no-op（原样拷贝），doLast 在其后对一方 CMake
// 产物原地跑 llvm-objcopy --strip-debug（只删 .debug_* 段，保留 .symtab/.dynsym →
// JNI 符号与函数名完整，仅丢失源码行号）。第三方预编译库不处理。
//
// 行号影响：会丢失 C++ 源码行号，但本项目 uploadCrashlytics* 任务默认禁用
// （见下方 uploadMapping 判断），当前发布配置下 native 行号本就不可得，不新增损害。
// ─────────────────────────────────────────────────────────────────────────────

// 需要剥离调试段的一方 CMake 产物（mobi + jp2forandroid 模块）。
// 第三方预编译库（libonnxruntime/libsherpa-onnx-jni/libcrashlytics*/libsentry*/
// libandroidx.graphics.path/libdatastore_shared_counter）不在此列——它们是上游发布
// 形态，--strip-debug 对它们几乎无收益且无源码符号。
val firstPartyNativeLibs = setOf(
    "libappmobi.so",
    "libmobi.so",
    "libcssparser.so",
    "libthreadlibs.so",
    "libopenjpeg.so",
    "libxml2.so"
)

// 解析 NDK 根目录并拼出 llvm-objcopy 路径。
// AGP 9.x（newDsl=true）用 androidComponents.sdkComponents.ndkDirectory（Provider<Directory>），
// 而非已废弃的 android.ndkDirectory（BaseExtension 在 newDsl 下不可用）。
// app 模块上方已声明 ndkVersion，故解析确定性指向与 mobi/jp2forandroid 相同的 NDK。
fun resolveLlvmObjcopy(): String {
    val ndkRoot = try {
        androidComponents.sdkComponents.ndkDirectory.get().asFile
    } catch (e: Exception) {
        System.getenv("ANDROID_NDK_HOME")?.let { File(it) }
            ?: error("NDK not found: set android.ndkVersion or ANDROID_NDK_HOME (${e.message})")
    }
    // prebuilt 下只有一个 host 目录（windows-x86_64 / linux-x86_64 / darwin-x86_64），取第一个。
    // 可执行文件后缀按宿主 OS 决定：Windows 是 llvm-objcopy.exe，Linux/macOS 无后缀。
    // 之前把 .exe 写死、并把 fallback 写成 windows-x86_64，导致 CI（ubuntu）找不到工具。
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val exeName = if (isWindows) "llvm-objcopy.exe" else "llvm-objcopy"
    val hostDir = ndkRoot.resolve("toolchains/llvm/prebuilt")
        .listFiles()?.firstOrNull { it.isDirectory }
        ?: error("llvm-objcopy prebuilt dir not found under toolchains/llvm/prebuilt (NDK root: $ndkRoot)")
    val exe = hostDir.resolve("bin/$exeName")
    check(exe.exists()) { "llvm-objcopy not found at $exe (NDK root: $ndkRoot)" }
    return exe.absolutePath
}

// 仅对 release 变体的 strip 任务挂 doLast（debug 保留调试信息便于 LLDB 调试一方 C++）。
// 用 afterEvaluate 等 AGP 创建 strip*DebugSymbols 任务后再配置。
afterEvaluate {
    val objcopy = resolveLlvmObjcopy()
    tasks.matching { it.name == "stripReleaseDebugSymbols" }.configureEach {
        doLast {
            // AGP 的 StripDebugSymbolsTask 输出目录通过 outputs 暴露。
            val outDir = outputs.files.files.firstOrNull { it.isDirectory }
                ?: error("[stripFirstPartySo] stripReleaseDebugSymbols has no output directory")
            var stripped = 0
            var skipped = 0
            outDir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { so ->
                if (so.name in firstPartyNativeLibs) {
                    // llvm-objcopy --strip-debug <input> <output>：写到临时文件再替换，
                    // 避免原地改写导致输入输出同路径的边界问题。
                    val tmp = File(so.parentFile, "${so.name}.stripped.tmp")
                    val pb = ProcessBuilder(objcopy, "--strip-debug", so.absolutePath, tmp.absolutePath)
                        .redirectErrorStream(true)
                    val proc = pb.start()
                    val out = proc.inputStream.bufferedReader().readText()
                    val code = proc.waitFor()
                    check(code == 0) { "llvm-objcopy failed (code=$code) on ${so.name}: $out" }
                    tmp.copyTo(so, overwrite = true)
                    tmp.delete()
                    stripped++
                } else {
                    skipped++
                }
            }
            logger.lifecycle("[stripFirstPartySo] stripped=$stripped skipped=$skipped (targets=${firstPartyNativeLibs.size}) in $outDir")
        }
    }
}

dependencies {
    implementation(fileTree("libs"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    //androidx.documentfile 是 Android 开发中用于访问和操作文件系统的一个库，它基于 Android 的 Storage Access Framework（SAF），
    // 允许应用程序在不直接访问系统权限的情况下，对设备上的文件和目录进行读写操作。
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.compose)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.androidx.compose.material3)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")


    implementation(libs.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.dagger.compiler)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.palette)
    implementation(libs.colorpicker.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.multiplatform.markdown.renderer.m3)

    // for in app reviews  应用内点赞
    implementation(libs.play.review.ktx)

    //这个库用于在 Android 应用中自动收集和展示项目的依赖信息，
    // 包括依赖项的名称、版本、许可证等信息。它提供了易于集成的 UI 组件，使得开发者可以轻松地在应用中展示这些信息 。
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose)

    implementation(libs.kotlinx.serialization.json)

    implementation(project(":bookparser"))
    implementation(project(":bookread"))
    implementation(project(":base"))
    implementation(project(":text2speech"))

    implementation(libs.ktor.client.okhttp.v341)            // Ktor 核心
    implementation(libs.ktor.client.content.negotiation)    // JSON 序列化
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.okhttp)// OkHttp（如果需要额外配置）
    implementation(libs.logging.interceptor)

    implementation(libs.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.timber)

    // MaterialKolor: runtime M3 ColorScheme generation from a seed color
    implementation(libs.material.kolor)

    // Unit tests: Compose color APIs + Robolectric (for ColorSchemeContrastTest)
    testImplementation(libs.androidx.ui.graphics)
    testImplementation(libs.androidx.compose.material3)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.material.kolor)
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)

    // ── androidTest（instrumentation 测试）──
    // P-CRASH-3：Room 迁移测试（MigrationTestHelper）
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

sentry {
    org.set("white-bear-studio")
    projectName.set("android")

    // this will upload your source code to Sentry to show it as part of the stack traces
    // disable if you don't want to expose your sources
    includeSourceContext.set(uploadMapping)
    autoUploadProguardMapping.set(uploadMapping)
}

// 受限网络环境默认禁用映射/源码上传任务，避免构建因连不上
// sentry.io / Firebase Crashlytics 接口而失败。
// 需要上传时用 -PuploadMapping=true 打开。
if (!uploadMapping) {
    tasks.matching {
        it.name.startsWith("uploadSentry") ||
                it.name.startsWith("sentryUpload") ||
                it.name.startsWith("sentryBundle") ||
                it.name.startsWith("uploadCrashlytics")
    }.configureEach {
        enabled = false
    }
}