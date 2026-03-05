import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "dev.oneapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.oneapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "GITHUB_REPO",
            "\"${System.getenv("GITHUB_REPOSITORY") ?: localProps.getProperty("github.repo", "owner/oneapp")}\"")
        buildConfigField("String", "GITHUB_TOKEN",
            "\"${System.getenv("GH_READ_TOKEN") ?: localProps.getProperty("github.token", "")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
        ?: localProps.getProperty("signing.keystore.path")
    val keystoreAlias = System.getenv("SIGNING_KEY_ALIAS")
        ?: localProps.getProperty("signing.keystore.alias", "oneapp")
    val keystorePass = System.getenv("SIGNING_KEY_STORE_PASSWORD")
        ?: localProps.getProperty("signing.keystore.password", "")
    val keyPass = System.getenv("SIGNING_KEY_PASSWORD")
        ?: localProps.getProperty("signing.key.password", "")

    if (keystorePath != null && file(keystorePath).exists()) {
        signingConfigs {
            create("oneapp") {
                storeFile = file(keystorePath)
                storePassword = keystorePass
                keyAlias = keystoreAlias
                keyPassword = keyPass
            }
        }
        buildTypes {
            debug {
                signingConfig = signingConfigs.getByName("oneapp")
            }
            release {
                signingConfig = signingConfigs.getByName("oneapp")
                isMinifyEnabled = false
            }
        }
    }
}

tasks.register("printVersionCode") {
    doLast {
        println(android.defaultConfig.versionCode)
    }
}

tasks.register("printVersionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.activity)
    implementation(libs.compose.navigation)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.json)
    testImplementation(libs.kotlin.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test.junit4)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.compose.test.manifest)
}
