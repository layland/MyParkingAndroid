plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.my.parking"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.my.parking"
        minSdk = 33
        targetSdk = 37
        versionCode = System.getenv("MY_PARKING_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("MY_PARKING_VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = System.getenv("MY_PARKING_KEYSTORE_PATH")
    val releaseKeyAlias = System.getenv("MY_PARKING_KEY_ALIAS")
    val releaseStorePassword = System.getenv("MY_PARKING_STORE_PASSWORD")
    val releaseKeyPassword = System.getenv("MY_PARKING_KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseKeystorePath,
        releaseKeyAlias,
        releaseStorePassword,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}


// Package the same web UI that is served by Cloudflare, for first launch and offline use.
val syncHybridUi by tasks.registering(Copy::class) {
    from(rootProject.file("cloudflare/public"))
    into(layout.buildDirectory.dir("generated/hybridAssets/hybrid"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/hybridAssets").get().asFile)
tasks.named("preBuild").configure { dependsOn(syncHybridUi) }
