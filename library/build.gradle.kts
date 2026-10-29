import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

group = "dev.carlsen.mega"
version = "1.0.0-beta09"

kotlin {
    jvmToolchain(17)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    android {
        withHostTest {}
        namespace = "dev.carlsen.mega"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.cryptography.core)
            implementation(libs.bignum)
            implementation(libs.touchlab.stately)
            implementation(libs.okio)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            implementation(libs.cryptography.provider.jdk)
            implementation(libs.ktor.client.okhttp)
        }
        appleMain.dependencies {
            implementation(libs.cryptography.provider.apple)
            implementation(libs.ktor.client.darwin)
        }
    }
}
