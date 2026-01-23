plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            implementation(project(":core:models"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
        }
        androidMain.dependencies {
             implementation("io.reactivex.rxjava3:rxjava:3.1.6")
             implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
             implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.8.0")
        }
    }
}

android {
    namespace = "com.tej.protojunc.core.ui"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = 24
    }
}
