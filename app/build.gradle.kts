plugins { alias(libs.plugins.android.application) }

android {
    namespace = "local.koenami.android"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "local.koenami.android"
        minSdk = 29
        targetSdk = 37
        versionCode = if (providers.gradleProperty("finalRelease").orNull == "true") 10000 else 9999
        versionName = if (providers.gradleProperty("finalRelease").orNull == "true") "1.0" else "1.0-rc.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { buildConfig = true }
    signingConfigs.getByName("debug") {
        val localDebugKey = rootProject.file(".toolchain/poc-debug.keystore")
        if (localDebugKey.exists()) storeFile = localDebugKey
    }
    val releaseKey = providers.environmentVariable("KOENAMI_KEYSTORE").orNull
    if (releaseKey != null) signingConfigs.create("localRelease") {
        storeFile = file(releaseKey)
        storePassword = providers.environmentVariable("KOENAMI_STORE_PASSWORD").orNull
        keyAlias = "koenami"
        keyPassword = providers.environmentVariable("KOENAMI_STORE_PASSWORD").orNull
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-dev" }
        create("poc") { initWith(getByName("debug")); applicationIdSuffix = ".poc"; versionNameSuffix = "-poc"; matchingFallbacks += "debug" }
        release {
            isMinifyEnabled = false
            if (releaseKey != null) signingConfig = signingConfigs.getByName("localRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
// Release is an explicit local build. Public distribution additionally requires the user's review.
androidComponents { beforeVariants(selector().withBuildType("release")) {
    it.enable = providers.gradleProperty("enableRelease").orNull == "true"
}
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.outputs.forEach { it.versionCode.set(10000) }
    }
}
dependencies {
    implementation(libs.webkit)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.core)
    androidTestImplementation(libs.test.junit)
}
tasks.register("writeRuntimeInventory") {
    doLast {
        val artifacts = configurations.getByName("debugRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        val out = layout.buildDirectory.file("runtime-inventory.tsv").get().asFile
        out.parentFile.mkdirs()
        out.writeText(artifacts.sortedBy { it.moduleVersion.id.toString() }.joinToString("\n") {
            "${it.moduleVersion.id}\t${it.file.absolutePath}"
        } + "\n")
    }
}
