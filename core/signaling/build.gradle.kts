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

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            api(project(":core:models"))
            api(project(":core:common"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okio)
            implementation(libs.kermit)
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-websockets:2.3.12")
            implementation("io.ktor:ktor-client-cio:2.3.12")
            implementation("io.ktor:ktor-client-logging:2.3.12")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
            
            implementation(libs.koin.core)
            implementation(libs.webrtc)
            implementation(libs.kable)
        }
        
        androidMain.dependencies {
            implementation("org.igniterealtime.smack:smack-android:4.4.8") {
                exclude(group = "xpp3", module = "xpp3")
            }
            implementation("org.igniterealtime.smack:smack-tcp:4.4.8") {
                exclude(group = "xpp3", module = "xpp3")
            }
            implementation("org.igniterealtime.smack:smack-im:4.4.8") {
                exclude(group = "xpp3", module = "xpp3")
            }
        }
    }
}

android {
    namespace = "com.tej.protojunc.core.signaling"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 24
    }
}
