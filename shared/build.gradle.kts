import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
}

kotlin {
    androidLibrary {
        namespace = "com.sencha.sencha.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(projects.shared.core.model)
            export(projects.shared.core.domain)
            export(projects.shared.core.data)
            export(projects.shared.core.jobs)
            export(projects.shared.core.security)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.model)
            api(projects.shared.core.domain)
            api(projects.shared.core.data)
            api(projects.shared.core.jobs)
            api(projects.shared.core.security)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
