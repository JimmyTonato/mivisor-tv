pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MiVisor-TV"

// Núcleo sin Android (se prueba con JUnit en la JVM).
include(":core:model", ":core:net", ":core:catalog", ":core:diagnostics", ":core:reliability", ":core:engine", ":core:remote", ":core:services")
// Android.
include(":core:storage", ":engine:outpace", ":feature:warp", ":feature:playback", ":ui:design", ":app")
