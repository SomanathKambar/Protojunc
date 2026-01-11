import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.protobuf)
    // alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    cocoapods {
        version = "1.0.0"
        summary = "WebRTC P2P Shared Logic"
        homepage = "https://github.com/your-repo/protojunc"
        ios.deploymentTarget = "15.0"

        framework {
            baseName = "shared"
            isStatic = true
        }

        pod("GoogleWebRTC") {
            version = "1.1.31999"
            moduleName = "WebRTC"
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            api(project(":core:signaling"))
            api(project(":core:common"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.protobuf)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kable)
            api(libs.webrtc)
            implementation(libs.kermit)
            implementation(libs.protobuf.kotlin)
            implementation(libs.okio)
            implementation(libs.room.runtime)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.sqlite)
            implementation("io.ktor:ktor-client-core:2.3.12")
            implementation("io.ktor:ktor-client-websockets:2.3.12")
            implementation("io.ktor:ktor-client-cio:2.3.12")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
        }
        androidMain.dependencies {
            implementation(libs.webrtc.kmp.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.zxing.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    // add("kspCommonMainMetadata", libs.room.compiler)
    // add("kspAndroid", libs.room.compiler)
    // add("kspIosX64", libs.room.compiler)
    // add("kspIosArm64", libs.room.compiler)
    // add("kspIosSimulatorArm64", libs.room.compiler)
}

android {
    namespace = "com.tej.protojunc.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.1"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}
