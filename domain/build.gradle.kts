plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.serialization)
}

val generateAppVersion by tasks.registering {
    val versionCode = providers.gradleProperty("app.versionCode").get().toInt()
    val versionName = providers.gradleProperty("app.versionName").get()
    val sourceDirectory = layout.buildDirectory.dir("generated/appVersion/kotlin")
    inputs.property("versionCode", versionCode)
    inputs.property("versionName", versionName)
    outputs.dir(sourceDirectory)
    doLast {
        require(versionCode > 0) { "app.versionCode must be positive" }
        val escapedName = versionName.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r")
        sourceDirectory.get().file("kio/chat/domain/AppBuildVersion.kt").asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                package kio.chat.domain

                object AppBuildVersion {
                    const val VERSION_CODE: Int = $versionCode
                    const val VERSION_NAME: String = "$escapedName"
                }
                """.trimIndent() + "\n"
            )
        }
    }
}

kotlin {
    jvm()
    linuxX64()
    android {
        namespace = "me.andannn.kio.chat.domain"
        compileSdk { version = release(37) }
    }

    sourceSets {
        commonMain { kotlin.srcDir(generateAppVersion) }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
