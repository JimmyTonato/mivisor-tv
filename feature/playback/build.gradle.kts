plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.mivisor.tv.playback"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":core:catalog"))
    api(project(":core:reliability"))
    api(project(":engine:outpace"))
    api(libs.media3.exoplayer)
    api(libs.media3.hls)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
}
