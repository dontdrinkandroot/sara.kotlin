plugins {
    kotlin("multiplatform") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
}

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val arch = System.getProperty("os.arch")

    // Select the correct Kotlin/Native target for CI runners and local builds
    val nativeTarget = when {
        hostOs == "Mac OS X" && arch == "aarch64" -> macosArm64("native")
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        else -> throw GradleException("Host OS '$hostOs' (arch: $arch) is not supported by Kotlin/Native.")
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
                implementation("io.ktor:ktor-client-core:3.3.1")
                implementation("io.ktor:ktor-client-curl:3.3.1")
                implementation("io.ktor:ktor-client-content-negotiation:3.3.1")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.1")
                implementation("com.github.ajalt.mordant:mordant:3.0.2")
                implementation("com.github.ajalt.mordant:mordant-markdown:3.0.2")
                implementation("com.github.ajalt.mordant:mordant-coroutines:3.0.2")
            }
        }
        val nativeTest by getting
    }
}
