plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

apply(from = "../modulesCommon.gradle")

android {
    namespace = "cloud.mindbox.mindbox_huawei_starter"

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
    api(project(path = ":mindbox-huawei"))
    implementation(project(path = ":sdk"))
    implementation(project(path = ":mindbox-sdk-starter-core"))

    // Implementation dependencies
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    // Firebase messaging
    implementation(libs.hms.push)
    implementation(libs.hms.ads.identifier)
}
