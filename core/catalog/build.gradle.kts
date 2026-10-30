plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:net"))
    api(libs.coroutines.core)
    implementation(libs.gson)
    testImplementation(libs.junit)
}
