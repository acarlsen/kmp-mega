import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

group = "dev.carlsen.mega"
version = "1.0.0-beta01"

kotlin {
    jvm()
    androidTarget {
        if(project.plugins.hasPlugin("com.vanniktech.maven.publish")) {
            publishLibraryVariants("release")
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
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
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.okio)
            }
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

android {
    namespace = "dev.carlsen.mega"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}
