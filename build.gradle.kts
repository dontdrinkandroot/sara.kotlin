plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("io.github.ben-manes.versions") version "0.62.0"
}

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")

    // Select the correct Kotlin/Native target for CI runners and local builds
    val nativeTarget = when {
        hostOs == "Linux" -> linuxX64("native")
        else -> throw GradleException("Host OS '$hostOs' is not supported. SARA only targets Linux.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "net.dontdrinkandroot.sara.main"
            }
        }
    }

    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
                implementation("io.ktor:ktor-client-core:3.5.2")
                implementation("io.ktor:ktor-client-curl:3.5.2")
                implementation("io.ktor:ktor-client-content-negotiation:3.5.2")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
                implementation("com.fleeksoft.ksoup:ksoup:0.2.6")
                implementation("com.github.ajalt.mordant:mordant:3.1.0")
                implementation("com.github.ajalt.mordant:mordant-markdown:3.1.0")
                implementation("com.github.ajalt.mordant:mordant-coroutines:3.1.0")
            }
        }
        val nativeTest by getting
    }
}
