plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

group = "com.tej.protojunc"
version = "1.0.0"
application {
    mainClass.set("com.tej.protojunc.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {

    implementation(project(":core:models"))

    implementation(libs.logback)

    implementation(libs.ktor.serverCore)

    implementation(libs.ktor.serverNetty)

    implementation("io.ktor:ktor-server-websockets-jvm:3.0.3")

    implementation("io.ktor:ktor-server-call-logging-jvm:3.0.3")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.0.3")
    
    implementation("org.jmdns:jmdns:3.5.9")

    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.3")

    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.3")

    testImplementation(libs.ktor.serverTestHost)

    testImplementation(libs.kotlin.testJunit)

}
