import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    kotlin("multiplatform") version "2.1.0"
}

repositories {
    mavenCentral()
}

kotlin {
    val xcf = XCFramework("TextKit")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "TextKit"
            binaryOption("bundleId", "antoni.Kalorie.TextKit")
            xcf.add(this)
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
