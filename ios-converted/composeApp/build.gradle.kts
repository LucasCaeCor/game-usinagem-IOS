plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform 1.7.3 (versões explícitas para eliminar
            // as 4 deprecações 'val runtime/foundation/material3/ui: String').
            implementation("org.jetbrains.compose.runtime:runtime:1.7.3")
            implementation("org.jetbrains.compose.foundation:foundation:1.7.3")
            implementation("org.jetbrains.compose.material3:material3:1.7.3")
            implementation("org.jetbrains.compose.ui:ui:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
        }
        iosMain.dependencies {
            // Em iOS, kotlinx-coroutines-core é re-exportado, mas deixamos explícito
            // para quem for adicionar APIs nativas (Foundation/Combine/etc.).
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
}
