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
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.cio)
    // implementation(libs.ktor.client.logging) // Check if this exists in catalog or add it
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

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

            implementation(libs.koin.android)
            implementation("io.ktor:ktor-client-logging:3.3.3")
        }
    }
}

android {
    namespace = "com.tej.protojunc.core.signaling"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
