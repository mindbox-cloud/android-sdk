plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../modulesCommon.gradle")

android {
    namespace = "cloud.mindbox.mindbox_rustore_starter"

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
    api(project(path = ":mindbox-rustore"))
    implementation(project(path = ":sdk"))
    implementation(project(path = ":mindbox-sdk-starter-core"))

    // Rustore dependencies
    implementation(libs.rustore.pushclient)
}
