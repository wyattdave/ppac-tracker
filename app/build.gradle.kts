import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties
import javax.inject.Inject

abstract class GenerateReleaseAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configFiles: ConfigurableFileCollection

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val badgeDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun generate() {
        fileSystemOperations.sync {
            from(configFiles)
            from(badgeDirectory) {
                include("*.png")
                eachFile {
                    val baseName = name.substringBeforeLast(".")
                        .lowercase()
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                    path = "badges/$baseName.png"
                }
                includeEmptyDirs = false
            }
            into(outputDirectory)
        }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSigningProperty(propertyName: String, environmentVariableName: String): String? =
    localProperties.getProperty("release.$propertyName")
        ?: providers.gradleProperty("release.$propertyName").orNull
        ?: providers.environmentVariable(environmentVariableName).orNull

val releaseStoreFile = releaseSigningProperty("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningProperty("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.releaseplanner.tracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ppactracker.powerdevbox"
        minSdk = 26
        targetSdk = 37
        versionCode = 8
        versionName = "0.1.7"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    kotlin {
        jvmToolchain(17)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val prepareReleaseAssets by tasks.registering(GenerateReleaseAssetsTask::class) {
    configFiles.from(
        rootProject.layout.projectDirectory.file("api.json"),
        rootProject.layout.projectDirectory.file("rewards.json"),
    )
    badgeDirectory.set(rootProject.layout.projectDirectory.dir("badges"))
    outputDirectory.set(layout.buildDirectory.dir("generated/assets/release"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            prepareReleaseAssets,
            GenerateReleaseAssetsTask::outputDirectory,
        )
    }
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    doFirst {
        check(hasReleaseSigning) {
            "Release signing is not configured. Add release.storeFile, release.storePassword, release.keyAlias, and release.keyPassword to local.properties or provide RELEASE_* environment variables."
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    ksp("androidx.room:room-compiler:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
