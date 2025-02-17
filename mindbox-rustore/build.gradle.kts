plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../modulesCommon.gradle")

android {
    namespace = "cloud.mindbox.mindbox_rustore"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles(
            "consumer-rules.pro",
            "../proguard/proguard-gson.pro",
            "../proguard/proguard-rustore.pro",
            "../proguard/proguard-kotlin.pro"
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(path = ":sdk"))

    implementation(libs.rustore.pushclient)

    // Implementation dependencies
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)

    // Test dependencies
    testImplementation(libs.bundles.test)
}
