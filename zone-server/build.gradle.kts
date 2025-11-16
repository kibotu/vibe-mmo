plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

group = "net.kibotu.ro"
version = "1.0.0"

application {
    mainClass.set("net.kibotu.ro.zone.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    
    // Content negotiation
    implementation("io.ktor:ktor-server-content-negotiation-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:${libs.versions.ktor.get()}")
    
    // WebSocket support
    implementation("io.ktor:ktor-server-websockets-jvm:${libs.versions.ktor.get()}")
    
    // HTTP client for account/character server communication
    implementation("io.ktor:ktor-client-core-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-cio-jvm:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:${libs.versions.ktor.get()}")
    
    // Kotlinx datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}

