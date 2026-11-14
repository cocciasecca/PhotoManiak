plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "app.inphotomaniak"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.inphotomaniak"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.7.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "edition"
    productFlavors {
        create("foss") {
            dimension = "edition"
            resValue("string", "edition_name", "FOSS")
        }
        create("withCast") {
            dimension = "edition"
            resValue("string", "edition_name", "With Cast (non-FOSS)")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    "withCastImplementation"("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Keep proprietary Google SDKs out of every FOSS configuration, including tests.
configurations.configureEach {
    if (name.startsWith("foss", ignoreCase = true)) {
        resolutionStrategy.eachDependency {
            val group = requested.group
            if (group == "com.google.android.gms" || group == "com.google.firebase" ||
                group == "com.google.android.play" || group.startsWith("com.google.ads")) {
                throw GradleException("Proprietary dependency is not allowed in FOSS: $requested")
            }
        }
    }
}

tasks.register("fossDependencyReport") {
    description = "List the resolved libraries packaged in the FOSS debug APK."
    doLast {
        configurations.getByName("fossDebugRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id.toString() }.distinct().sorted().forEach { println(it) }
    }
}
