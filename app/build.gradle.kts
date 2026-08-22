plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStorePath = providers.environmentVariable("BHAIPAISA_SIGNING_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("BHAIPAISA_SIGNING_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("BHAIPAISA_SIGNING_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("BHAIPAISA_SIGNING_KEY_PASSWORD").orNull
val privateReleaseSigningAvailable = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.bhaipaisa.moneymanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bhaipaisa.moneymanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    signingConfigs {
        if (privateReleaseSigningAvailable) {
            create("privateRelease") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            signingConfig = signingConfigs.findByName("privateRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    constraints {
        implementation("org.bouncycastle:bcprov-jdk15to18:1.85") {
            because("PDFBox's older transitive crypto provider has published security advisories")
        }
        implementation("org.bouncycastle:bcpkix-jdk15to18:1.85") {
            because("PDFBox's older transitive PKIX library has published security advisories")
        }
    }
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
