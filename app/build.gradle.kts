plugins { alias(libs.plugins.android.application) }

android {
    namespace = "local.koenami.android"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "local.koenami.android"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.0.2-m0"
    }
    buildFeatures { buildConfig = true }
    signingConfigs.getByName("debug") {
        storeFile = rootProject.file(".toolchain/poc-debug.keystore")
    }
    buildTypes {
        debug { applicationIdSuffix = ".poc"; versionNameSuffix = "-poc" }
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
// M0 must pass on hardware before production implementation and signing are enabled.
androidComponents { beforeVariants(selector().withBuildType("release")) { it.enable = false } }
dependencies {
    implementation(libs.webkit)
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
}
