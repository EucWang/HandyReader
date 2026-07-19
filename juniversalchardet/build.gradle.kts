plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "org.mozilla.universalchardet"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 23
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation("commons-codec:commons-codec:1.16.1")
    androidTestImplementation(libs.androidx.junit)
}
