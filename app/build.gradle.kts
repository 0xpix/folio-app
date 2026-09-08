plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pix.folio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pix.folio"
        minSdk = 26
        targetSdk = 36
        val ciVersionName = System.getenv("FOLIO_VERSION_NAME")
        val ciVersionCode = System.getenv("FOLIO_VERSION_CODE")?.toIntOrNull()
            ?: System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.let { 100_000 + it }
        versionCode = ciVersionCode ?: 501
        versionName = ciVersionName ?: "0.5.1"

        val commit = (System.getenv("GITHUB_SHA") ?: "local").take(7)
        buildConfigField("String", "GIT_COMMIT", "\"$commit\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("beta") {
            dimension = "distribution"
            applicationIdSuffix = ".beta"
            versionNameSuffix = if (System.getenv("FOLIO_VERSION_NAME")?.contains("beta", ignoreCase = true) == true) "" else ".beta"
            buildConfigField("boolean", "GITHUB_BETA_UPDATES", "true")
            buildConfigField("String", "UPDATE_CHANNEL", "\"GitHub beta\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "GITHUB_BETA_UPDATES", "false")
            buildConfigField("String", "UPDATE_CHANNEL", "\"Play\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    val releaseStorePath = System.getenv("FOLIO_KEYSTORE_FILE")
    val releaseStorePassword = System.getenv("FOLIO_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("FOLIO_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("FOLIO_KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseStorePath,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.9.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.glance:glance-material3:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.9.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
