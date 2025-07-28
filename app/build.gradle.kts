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

//    apply plugin: 'kotlin-kapt'
    id("androidx.room")
}

val apikeyPropertiesFile = rootProject.file("apikey.properties")
val apikeyProperties = Properties().apply {
    load(FileInputStream(apikeyPropertiesFile))
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.wxn.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wxn.reader"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.250728"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
//        ksp {
//            arg("room.schemaLocation", "$projectDir/schemas")
//        }

//        ndk {
//            abiFilters += listOf("arm64-v8a", "x86_64", "x86", "armeabi-v7a")
//        }


        buildConfigField("String", "RELEASE_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"")
        buildConfigField("String", "FULL_SCREEN_BOOK_READER_AD_UNIT", apikeyProperties["FULL_SCREEN_BOOK_READER_AD_UNIT"] as String)
        buildConfigField("String", "OPEN_BOOK_GRID_AD_UNIT", apikeyProperties["OPEN_BOOK_GRID_AD_UNIT"] as String)
        buildConfigField("String", "OPEN_BOOK_LIST_AD_UNIT", apikeyProperties["OPEN_BOOK_LIST_AD_UNIT"] as String)
        buildConfigField("String", "READER_SCREEN_AD_UNIT", apikeyProperties["READER_SCREEN_AD_UNIT"] as String)
        buildConfigField("String", "PRODUCT_ID", apikeyProperties["PRODUCT_ID"] as String)
        buildConfigField("Boolean", "ENABLE_AD", apikeyProperties["enableAd"] as String)
//        buildConfigField("String", "BASE_64_ENCODED_PUBLIC_KEY", apikeyProperties["BASE_64_ENCODED_PUBLIC_KEY"] as String)
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            buildConfigField("String", "RELEASE_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"")
            buildConfigField("String", "FULL_SCREEN_BOOK_READER_AD_UNIT", apikeyProperties["FULL_SCREEN_BOOK_READER_AD_UNIT"] as String)
            buildConfigField("String", "OPEN_BOOK_GRID_AD_UNIT", apikeyProperties["OPEN_BOOK_GRID_AD_UNIT"] as String)
            buildConfigField("String", "OPEN_BOOK_LIST_AD_UNIT", apikeyProperties["OPEN_BOOK_LIST_AD_UNIT"] as String)
            buildConfigField("String", "READER_SCREEN_AD_UNIT", apikeyProperties["READER_SCREEN_AD_UNIT"] as String)
            buildConfigField("String", "PRODUCT_ID", apikeyProperties["PRODUCT_ID"] as String)
            buildConfigField("Boolean", "ENABLE_AD", apikeyProperties["enableAd"] as String)
//            buildConfigField("String", "BASE_64_ENCODED_PUBLIC_KEY", apikeyProperties["BASE_64_ENCODED_PUBLIC_KEY"] as String)

            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("String", "RELEASE_DATE", "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"")
            buildConfigField("String", "FULL_SCREEN_BOOK_READER_AD_UNIT", apikeyProperties["FULL_SCREEN_BOOK_READER_AD_UNIT"] as String)
            buildConfigField("String", "OPEN_BOOK_GRID_AD_UNIT", apikeyProperties["OPEN_BOOK_GRID_AD_UNIT"] as String)
            buildConfigField("String", "OPEN_BOOK_LIST_AD_UNIT", apikeyProperties["OPEN_BOOK_LIST_AD_UNIT"] as String)
            buildConfigField("String", "READER_SCREEN_AD_UNIT", apikeyProperties["READER_SCREEN_AD_UNIT"] as String)
            buildConfigField("String", "PRODUCT_ID", apikeyProperties["PRODUCT_ID"] as String)
            buildConfigField("Boolean", "ENABLE_AD", apikeyProperties["enableAd"] as String)
//            buildConfigField("String", "BASE_64_ENCODED_PUBLIC_KEY", apikeyProperties["BASE_64_ENCODED_PUBLIC_KEY"] as String)

            isMinifyEnabled = false
            isShrinkResources = false
            ndk {
                debugSymbolLevel = "FULL"
            }
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17


        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
    }
    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
//        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/schemas/**"
//            excludes += "META-INF/DEPENDENCIES"
            excludes += "/META-INF/gradle/incremental.annotation.processors"
        }
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
}

//chaquopy {
//    defaultConfig {
//        version = "3.8"
//        pip {
//            install("ebooklib")
//        }
//        pyc {
//            src = false
//        }
//    }
//}

dependencies {
//    implementation (fileTree(dir: 'libs', include: ['*.aar']))
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

//    debugImplementation(libs.androidx.ui.tooling)
//    debugImplementation(libs.androidx.ui.test.manifest)
//    testImplementation(libs.junit)
//    androidTestImplementation(libs.androidx.junit)
//    androidTestImplementation(libs.androidx.espresso.core)
//    androidTestImplementation(platform(libs.androidx.compose.bom))
//    androidTestImplementation(libs.androidx.ui.test.junit4)

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
//    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    // Add the dependencies for the Crashlytics NDK and Analytics libraries
    // When using the BoM, you don't specify versions in Firebase library dependencies
    implementation(libs.firebase.crashlytics.ndk)


    coreLibraryDesugaring(libs.desugar.jdk.libs)
//    implementation(libs.coil.compose)       // for images
//    implementation(libs.coil.network.okhttp)
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

    // for ads TODO
//    implementation(libs.play.services.ads)

    // for in app reviews  应用内点赞
    implementation(libs.play.review.ktx)

    // for in app purchases
//    implementation(libs.billing.ktx)

    //这个库用于在 Android 应用中自动收集和展示项目的依赖信息，
    // 包括依赖项的名称、版本、许可证等信息。它提供了易于集成的 UI 组件，使得开发者可以轻松地在应用中展示这些信息 。
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose)

//    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.1"))
//    implementation("io.github.jan-tennert.supabase:postgrest-kt")
//    implementation("io.github.jan-tennert.supabase:auth-kt")
//    implementation("io.github.jan-tennert.supabase:realtime-kt")

//    implementation(libs.ketch)
//    implementation(libs.retrofit)

//    implementation("com.google.android.gms:play-services-auth:21.3F.0")
//    implementation("com.google.apis:google-api-services-drive:v3-rev197-1.25.0")
//    implementation("com.google.api-client:google-api-client-android:2.2.0")
//    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
//
//    implementation("androidx.credentials:credentials-play-services-auth:1.5.0-rc01")
//    implementation("androidx.credentials:credentials:1.5.0-rc01")
//
//    // optional - needed for credentials support from play services, for devices running
//    // Android 13 and below.
//    implementation("androidx.credentials:credentials-play-services-auth:1.5.0-rc01")
//
//    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
//    implementation(libs.speech)

    implementation(project(":bookparser"))
    implementation(project(":bookread"))
    implementation(project(":base"))
    implementation(project(":text2speech"))
}