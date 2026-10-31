import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The release workflow passes -PversionName/-PversionCode, derived from the git tag.
val appVersionName = providers.gradleProperty("versionName").getOrElse("0.2.0")
val appVersionCode = providers.gradleProperty("versionCode").getOrElse("200").toIntOrNull() ?: 200

// Release signing, optional: env vars win (CI), then android/keystore.properties (local).
// Without either, :app:assembleRelease stays unsigned and CI falls back to a debug APK.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(key: String): String? = System.getenv(key) ?: keystoreProps.getProperty(key)

val keystorePath = signingValue("KEYSTORE_FILE")

android {
    namespace = "id.iqbaldev.kartrid"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.iqbaldev.kartrid"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    androidResources {
        noCompress += listOf("ogg")
    }

    signingConfigs {
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed only when the keystore above was configured.
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // preview rendering (Roborazzi) — bikin PNG render UI asli di host, tanpa device
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.33.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.33.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
