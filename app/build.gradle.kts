import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "dev.oneapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.oneapp"
        minSdk = 26
        targetSdk = 35
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
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

    val keystorePath = localProps.getProperty("signing.keystore.path")
    val keystoreAlias = localProps.getProperty("signing.keystore.alias", "oneapp")
    val keystorePass = localProps.getProperty("signing.keystore.password", "")
    val keyPass = localProps.getProperty("signing.key.password", "")

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
