plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.fractanomics.crosstraining"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fractanomics.crosstraining"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "3.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull?.ifBlank { null }
        ?: System.getenv("RELEASE_STORE_FILE")?.ifBlank { null }
    val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull?.ifBlank { null }
        ?: System.getenv("RELEASE_STORE_PASSWORD")?.ifBlank { null }
    val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull?.ifBlank { null }
        ?: System.getenv("RELEASE_KEY_ALIAS")?.ifBlank { null }
    val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull?.ifBlank { null }
        ?: System.getenv("RELEASE_KEY_PASSWORD")?.ifBlank { null }

    val hasReleaseSigningConfig = !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank() &&
        file(releaseStoreFile).exists()

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
                logger.lifecycle("Release signing: release keystore")
            } else {
                signingConfig = signingConfigs.getByName("debug")
                logger.lifecycle("Release signing: debug fallback (RELEASE_* not configured)")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "APP_ENV", "\"production\"")
        }
        debug {
            buildConfigField("String", "APP_ENV", "\"snapshot\"")
        }
        create("snapshot") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "APP_ENV", "\"snapshot\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Google Auth & Credential Manager
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Firebase BoM and SDKs
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    implementation(libs.androidx.media)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}
