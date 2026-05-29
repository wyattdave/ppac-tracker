plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.releaseplanner.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.releaseplanner.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    val generatedConfigAssets = layout.buildDirectory.dir("generated/assets/releaseConfig")
    val generatedBadgeAssets = layout.buildDirectory.dir("generated/assets/releaseBadges")

    sourceSets {
        getByName("main") {
            assets.srcDir(generatedConfigAssets)
            assets.srcDir(generatedBadgeAssets)
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

val copyReleaseConfigAssets by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.file("api.json"))
    from(rootProject.layout.projectDirectory.file("rewards.json"))
    into(layout.buildDirectory.dir("generated/assets/releaseConfig"))
}

val copyBadgeAssets by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("badges")) {
        include("*.png")
        eachFile {
            val baseName = name.substringBeforeLast(".")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
            path = "badges/$baseName.png"
        }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("generated/assets/releaseBadges"))
}

tasks.named("preBuild") {
    dependsOn(copyReleaseConfigAssets, copyBadgeAssets)
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
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
