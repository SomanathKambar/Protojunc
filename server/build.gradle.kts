plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

group = "com.tej.directo"
version = "1.0.0"
application {
    mainClass.set("com.tej.directo.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {

    implementation(projects.shared)

    implementation(libs.logback)

    implementation(libs.ktor.serverCore)

    implementation(libs.ktor.serverNetty)

    implementation("io.ktor:ktor-server-websockets-jvm:2.3.12")

    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")

    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.12")

    testImplementation(libs.ktor.serverTestHost)

    testImplementation(libs.kotlin.testJunit)

}
