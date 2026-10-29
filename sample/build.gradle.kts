import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractNativeMacApplicationPackageTask
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm()

    val macOsConfiguration: KotlinNativeTarget.() -> Unit = {
        binaries {
            executable {
                entryPoint = "main"
                freeCompilerArgs += listOf(
                    "-linker-option", "-framework", "-linker-option", "Metal"
                )
            }
        }
    }
    macosArm64(macOsConfiguration)

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "sample"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library"))

            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.kotlinx.datetime)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.carlsen.mega.sample.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "kmp-mega"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "kmp-mega"
                upgradeUuid = "92E66CE0-261E-4231-8463-AE9E245D6E50"
            }
        }
    }
}

compose.desktop.nativeApplication {
    targets(kotlin.targets.getByName("macosArm64"))
    distributions {
        targetFormats(TargetFormat.Dmg)
        packageName = "kmp-mega"
        packageVersion = "1.0.0"
    }
}

afterEvaluate {
    val baseTask = "createDistributableNative"
    listOf("debug", "release").forEach { it ->
        val createAppTaskName = baseTask + it.capitalize() + "macosArm64".capitalize()

        val createAppTask =
            tasks.findByName(createAppTaskName) as? AbstractNativeMacApplicationPackageTask?
                ?: return@forEach

        val destinationDir = createAppTask.destinationDir.get().asFile
        val packageName = createAppTask.packageName.get()

        tasks.create("runNative" + it.capitalize()) {
            group = createAppTask.group
            dependsOn(createAppTaskName)
            doLast {
                ProcessBuilder(
                    "open",
                    destinationDir.absolutePath + "/" + packageName + ".app"
                ).start().waitFor()
            }
        }
    }
}