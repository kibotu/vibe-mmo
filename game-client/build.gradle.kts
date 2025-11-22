import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version libs.versions.kotlin.get()
}

group = "net.kibotu.ro"
version = "1.0.0"

kotlin {
    jvm()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.shared)
                
                // Compose
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                
                // Ktor Client
                implementation("io.ktor:ktor-client-core:${libs.versions.ktor.get()}")
                implementation("io.ktor:ktor-client-content-negotiation:${libs.versions.ktor.get()}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${libs.versions.ktor.get()}")
                implementation("io.ktor:ktor-client-websockets:${libs.versions.ktor.get()}")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.kotlinx.coroutines.get()}")
                
                // Settings/Preferences for local storage
                implementation("com.russhwolf:multiplatform-settings:1.1.1")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                
                // Ktor Client JVM
                implementation("io.ktor:ktor-client-cio:${libs.versions.ktor.get()}")
                
                // Logging
                implementation(libs.logback)
                
                // LibGDX for hardware-accelerated game rendering
                implementation("com.badlogicgames.gdx:gdx:1.12.1")
                implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.12.1")
                implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-desktop")
                // FreeType font generator for better font rendering
                implementation("com.badlogicgames.gdx:gdx-freetype:1.12.1")
                implementation("com.badlogicgames.gdx:gdx-freetype-platform:1.12.1:natives-desktop")
            }
        }
        
        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.testJunit)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "net.kibotu.ro.client.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Ragnarok Online Client"
            packageVersion = "1.0.0"
        }
    }
}

