import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform") version "2.4.10"
    id("com.android.kotlin.multiplatform.library") version "9.4.1"
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    val xcf = XCFramework("ExportKit")

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "antoni.Kalorie.ExportKit"
        compileSdk {
            version = release(37) { minorApiLevel = 0 }
        }
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ExportKit"
            binaryOption("bundleId", "antoni.Kalorie.ExportKit")
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.conamobiledev:pdfkmp:1.3.0")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
