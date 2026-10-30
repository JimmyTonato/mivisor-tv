plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.coroutines.core)
    implementation(libs.gson)
    testImplementation(libs.junit)
}
