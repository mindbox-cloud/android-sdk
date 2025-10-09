plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../modulesCommon.gradle")

android {
    namespace = "cloud.mindbox.mindbox_sdk_starter_core"

    defaultConfig {
        consumerProguardFiles(
            "consumer-rules.pro"
        )
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Implementation dependencies
    implementation(libs.androidx.core.ktx)
    implementation(project(path = ":sdk"))
}
