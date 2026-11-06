plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}


// 3i · Modelos inmutables marcados como estables para Compose (menos redibujados al mover el foco).
composeCompiler {
    stabilityConfigurationFile.set(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}

android {
    namespace = "com.mivisor.tv.ui.design"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    testImplementation(libs.junit)
}
