plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../modulesCommon.gradle")

android {
    namespace = "cloud.mindbox.mindbox_firebase_starter"

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
    api(project(path = ":mindbox-firebase"))
    implementation(project(path = ":sdk"))
    implementation(project(path = ":mindbox-sdk-starter-core"))

    // Firebase messaging
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
