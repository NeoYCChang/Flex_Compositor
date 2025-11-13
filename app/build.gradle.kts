plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    signingConfigs {
        create("rcar_zdc") {
            storeFile = file("D:\\YuChen\\Git\\Android\\rcar_security\\rcar_zdc.keystore")
            storePassword = "123456"
            keyAlias = "platform"
            keyPassword = "123456"
        }
        create("sa8295") {
            storeFile =
                file("D:\\YuChen\\Git\\Android\\keytool-importkeypair-master\\sa8295.keystore")
            storePassword = "123456"
            keyAlias = "platform"
            keyPassword = "123456"
        }
    }
    namespace = "com.auo.flex_compositor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auo.flex_compositor"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "TARGET_PLATFORM", "\"DEBUG\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("rcar_zdc") {
            signingConfig = signingConfigs.getByName("rcar_zdc")
            buildConfigField("String", "TARGET_PLATFORM", "\"RCAR_ZDC\"")
        }
        create("sa8295") {
            signingConfig = signingConfigs.getByName("sa8295")
            buildConfigField("String", "TARGET_PLATFORM", "\"SA8295\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.java.websocket)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.commons.net)
    implementation(files("src\\main\\libs\\auoservice.jar"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}