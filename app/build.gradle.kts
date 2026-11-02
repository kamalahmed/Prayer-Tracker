import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing: read from a properties file OUTSIDE the repo (or env vars). Public clones without it
// still build; the release APK is then simply unsigned. Never commit keystores or passwords.
val signingProps = Properties().apply {
    val path = (findProperty("signingProps") as String?) ?: System.getenv("PRAYER_SIGNING_PROPS")
        ?: "${System.getProperty("user.home")}/secrets/prayer-app-signing.properties"
    val f = file(path)
    if (f.isFile) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps.getProperty(key)
val releaseStoreFile = signingValue("storeFile", "PRAYER_KEYSTORE")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).isFile &&
    signingValue("storePassword", "PRAYER_KEYSTORE_PASSWORD") != null

// Overridable for release cutting: ./gradlew assembleRelease -PversionName=0.2.0-beta.1 -PversionCode=3
val appVersionName = (findProperty("versionName") as String?) ?: "0.1.0-beta.1"
val appVersionCode = (findProperty("versionCode") as String?)?.toInt() ?: 2

android {
    namespace = "com.kamalahmed.prayerapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kamalahmed.prayerapp"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"kamalahmed/Prayer-Tracker\"")
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "PRAYER_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "PRAYER_KEY_ALIAS") ?: "prayerapp"
                keyPassword = signingValue("keyPassword", "PRAYER_KEY_PASSWORD") ?: signingValue("storePassword", "PRAYER_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)
    implementation(libs.play.location)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.play)
    implementation(libs.splashscreen)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation("org.json:json:20250517")
}
