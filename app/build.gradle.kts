plugins { alias(libs.plugins.android.application) }

android {
    namespace = "local.koenami.android"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "local.koenami.android"
        minSdk = 29
        targetSdk = 37
        versionCode = providers.gradleProperty("overrideVersionCode").orNull?.toInt() ?: 10200
        versionName = providers.gradleProperty("overrideVersionName").orNull ?: "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    val releaseKey = providers.environmentVariable("KOENAMI_KEYSTORE").orNull
    if (providers.gradleProperty("enableRelease").orNull == "true") {
        require(!releaseKey.isNullOrBlank() && file(releaseKey).isFile) { "A private KOENAMI_KEYSTORE is required for Release" }
        require(!providers.environmentVariable("KOENAMI_STORE_PASSWORD").orNull.isNullOrBlank()) { "KOENAMI_STORE_PASSWORD is required" }
    }
    if (releaseKey != null) signingConfigs.create("localRelease") {
        storeFile = file(releaseKey)
        storePassword = providers.environmentVariable("KOENAMI_STORE_PASSWORD").orNull
        keyAlias = "koenami"
        keyPassword = providers.environmentVariable("KOENAMI_STORE_PASSWORD").orNull
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev" }
        release {
            isMinifyEnabled = false
            if (releaseKey != null) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
    testBuildType = if (providers.gradleProperty("enableRelease").orNull == "true") "release" else "debug"
}
// Explicit opt-in prevents accidentally distributing an unsigned Release.
androidComponents { beforeVariants(selector().withBuildType("release")) {
    it.enable = providers.gradleProperty("enableRelease").orNull == "true"
}
}
dependencies {
    implementation(libs.webkit)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.work)
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.junit)
}
tasks.register("writeRuntimeInventory") {
    doLast {
        val variant = if (providers.gradleProperty("enableRelease").orNull == "true") "release" else "debug"
        val artifacts = configurations.getByName("${variant}RuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        val out = layout.buildDirectory.file("runtime-inventory.tsv").get().asFile
        out.parentFile.mkdirs()
        out.writeText(artifacts.sortedBy { it.moduleVersion.id.toString() }.joinToString("\n") {
            "${it.moduleVersion.id}\t${it.file.absolutePath}"
        } + "\n")
    }
}
