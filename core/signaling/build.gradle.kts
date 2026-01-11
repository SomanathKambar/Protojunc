plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm()
    
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-websockets:2.3.12")
            implementation("io.ktor:ktor-client-cio:2.3.12")
            implementation("io.ktor:ktor-client-logging:2.3.12")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
        }
    }
}

android {
    namespace = "com.tej.directo.core.signaling"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}
