// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
//    id("com.google.devtools.ksp") version "2.0.20-1.0.25" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("androidx.room") version "2.7.2" apply false

    id("com.mikepenz.aboutlibraries.plugin") version "11.2.3" apply false
    alias(libs.plugins.google.gms.google.services) apply false
//    alias(libs.plugins.google.firebase.crashlytics) apply false
    // Add the dependency for the Crashlytics Gradle plugin
    id("com.google.firebase.crashlytics") version "3.0.4" apply false
    alias(libs.plugins.android.library) apply false

//    id("com.chaquo.python") version "15.0.1" apply false

    id("org.jetbrains.kotlin.jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"

}

// ─────────────────────────────────────────────────────────────────────────────
// AGP 9.1 原生库合并链增量缓存缺陷的防御
// ─────────────────────────────────────────────────────────────────────────────
// 故障：原生库从 library 模块（mobi/jp2forandroid 的 CMake 产物）合并进 app 的
// 整条链路在 AGP 9.1 增量构建下不可靠。表现为 merged_native_libs 只保留构建机
// ABI（x86_64）的 .so，丢弃 arm64-v8a / armeabi-v7a / x86，导致 APK 缺失
// libappmobi.so / libmobi.so / libopenjpeg.so / libcssparser.so 等一方库。
// 运行时报：
//   dlopen failed: library "libappmobi.so" not found
//
// 验证（2026-07-16）：在脏增量状态下，app/build/intermediates/merged_native_libs
// 里 arm64-v8a / armeabi-v7a / x86 仅 11 个三方库，一方库全无；仅 x86_64 齐全。
// 执行 ./gradlew clean :app:assembleDebug 后，4 个 ABI 均恢复为 17 个库齐全。
// 即为纯粹的增量缓存污染，与 ABI splits / 旧包残留 / 模拟器时钟无关。
//
// 防御策略：强制整条 native 合并链每次重新执行（不命中 up-to-date 缓存）：
//   - copyDebugJniLibsProjectOnly / copyReleaseJniLibsProjectOnly
//       （各 library 模块把 CMake 产物导出到自身 intermediates）
//   - mergeDebugJniLibFolders / mergeReleaseJniLibFolders
//       （app 把所有依赖模块的 jniLibs 合并——被污染的主要环节）
//   - mergeDebugNativeLibs / mergeReleaseNativeLibs
//       （最终合并到打包输入）
//
// 代价：每次构建多拷贝/合并 ~25MB 的 .so（本地 SSD <2s），不触发 CMake 重编译。
// 移除时机：AGP 修复此缺陷后，或确认 android.newDsl=true 下不复现后，可移除。
subprojects {
    tasks.matching {
        it.name == "copyDebugJniLibsProjectOnly" ||
                it.name == "copyReleaseJniLibsProjectOnly" ||
                it.name == "mergeDebugJniLibFolders" ||
                it.name == "mergeReleaseJniLibFolders" ||
                it.name == "mergeDebugNativeLibs" ||
                it.name == "mergeReleaseNativeLibs"
    }.configureEach {
        outputs.upToDateWhen { false }
    }
}
