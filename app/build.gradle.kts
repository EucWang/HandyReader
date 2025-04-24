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

    id("com.mikepenz.aboutlibraries.plugin")
//    alias(libs.plugins.google.gms.google.services) //TODO
    alias(libs.plugins.google.firebase.crashlytics)

//    id("com.chaquo.python")

}


val apikeyPropertiesFile = rootProject.file("apikey.properties")
val apikeyProperties = Properties().apply {
    load(FileInputStream(apikeyPropertiesFile))
}


android {
    namespace = "com.wxn.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wxn.reader"
        minSdk = 23
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 20
        versionName = "1.2.3"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

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
//        kotlinCompilerExtensionVersion = "1.5.15"
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/schemas/**"
//            excludes += "META-INF/DEPENDENCIES"
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
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.firebase.crashlytics)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    //readium
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.navigator.media.tts)
    implementation(libs.readium.navigator.media.audio)
//    implementation(libs.readium.adapter.exoplayer)
//    implementation(libs.readium.opds)
//    implementation(libs.readium.lcp)


    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)







    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.coil.compose)       // for images
    implementation(libs.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)


    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.compose)



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



    // for in app reviews
    implementation(libs.play.review.ktx)


    // for in app purchases
    implementation(libs.billing.ktx)



    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose)


    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.1"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")




//    implementation(libs.ketch)
//    implementation(libs.retrofit)


//    implementation("com.google.android.gms:play-services-auth:21.3.0")
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

}