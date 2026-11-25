import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.serialization)
}

val signingProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "me.andannn.kio.chat"
    defaultConfig {
        applicationId = "me.andannn.kio.chat"
        versionCode = providers.gradleProperty("app.versionCode").get().toInt()
        versionName = providers.gradleProperty("app.versionName").get()

        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        defaultConfig {
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            storeFile = signingProperties.getProperty("storeFile")?.let { rootProject.file(it) }
            storePassword = signingProperties.getProperty("storePassword")
            keyAlias = signingProperties.getProperty("keyAlias")
            keyPassword = signingProperties.getProperty("keyPassword")
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"http://127.0.0.1:7879\"")
            buildConfigField("String", "BASE_WS_URL", "\"ws://127.0.0.1:7879\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BASE_URL", "\"https://133.117.75.202:7879\"")
            buildConfigField("String", "BASE_WS_URL", "\"wss://133.117.75.202:7879\"")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.webrtc.android)

    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(project.dependencies.platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.retainedmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.androidx.core.splashscreen)
    implementation(libs.timber)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation3.ui)
    debugImplementation(libs.androidx.ui.tooling)
}

tasks.register<Copy>("prepareKchatDist") {
    group = "distribution"
    description = "Build and copy the signed release APK to build/dist/kchat."
    dependsOn("assembleProdRelease")

    val apk = layout.buildDirectory.file("outputs/apk/prod/release/android-prod-release.apk")
    doFirst {
        check(apk.get().asFile.isFile) { "Release APK not found: ${apk.get().asFile}" }
    }
    from(apk) {
        rename { "kio-chat.apk" }
    }
    into(layout.buildDirectory.dir("dist/kchat"))
}
