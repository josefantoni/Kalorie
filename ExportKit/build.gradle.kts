import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform") version "2.4.10"
}

repositories {
    mavenCentral()
}

kotlin {
    val xcf = XCFramework("ExportKit")

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
