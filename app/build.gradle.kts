plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// GitHub Actions: cada ejecución genera un versionCode mayor que el anterior.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toInt()

// FIRMA: el MISMO procedimiento que la APK v18.
//  - Con los secretos de GitHub: la CI decodifica SIGNING_KEYSTORE_BASE64 en un fichero temporal y exporta su ruta en
//    SIGNING_KEYSTORE_PATH; las contraseñas y el alias llegan como variables de entorno desde los secretos.
//  - Sin secretos: se firma con app/debug.keystore, la MISMA clave de desarrollo de la v18 (no se genera otra).
//    Esa clave no es la de WARP: la APK funciona en todo menos en el control de WARP (la CI lo avisa).
val releaseKeystore: String? = System.getenv("SIGNING_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

// Agenda y metadatos publicados en GitHub (mivisor-datos). Se puede cambiar sin tocar el código con
// -PagendaUrl=… o desde Ajustes de la app.
val agendaUrl = (findProperty("agendaUrl") as String?)
    ?: "https://raw.githubusercontent.com/JimmyTonato/mivisor-datos/main/agenda.json"

android {
    namespace = "com.mivisor.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mivisor.tv"
        // Android 6.0: el mínimo de Outpace (binario compilado contra la API 23).
        minSdk = 23
        targetSdk = 35
        versionCode = maxOf(1, runNumber)
        versionName = "1.0.$runNumber"

        resourceConfigurations += listOf("es", "en")
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }

        // Mismo dominio de proveedores que la v18 (necesario para leer los códigos; no es una credencial).
        buildConfigField("String", "STREAM_DOMAIN", "\"elcano.top\"")
        buildConfigField("String", "AGENDA_URL", "\"$agendaUrl\"")
        buildConfigField("String", "OUTPACE_COMMIT", "\"${System.getenv("OUTPACE_COMMIT") ?: "desconocido"}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Releases firmadas con la clave propia (la de MiVisor/WARP): declaran también el permiso de control de WARP,
    // exactamente como la v18, para que el orden de instalación no importe.
    if (releaseKeystore != null) {
        sourceSets.getByName("release").manifest.srcFile("src/warpSigned/AndroidManifest.xml")
    }

    signingConfigs {
        // Clave de desarrollo de la v18 (app/debug.keystore, contraseñas estándar de Android). No es la de WARP.
        create("dev") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Clave propia de MiVisor/WARP desde GitHub Secrets.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Como la v18: la clave de GitHub Secrets si está; si no, la de desarrollo (app/debug.keystore).
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "dev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Outpace (liboutpace.so) es un EJECUTABLE que se lanza desde nativeLibraryDir: debe extraerse al instalar.
        jniLibs { useLegacyPackaging = true }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/*.kotlin_module",
                "DebugProbesKt.bin", "kotlin-tooling-metadata.json"
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:net"))
    implementation(project(":core:catalog"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:reliability"))
    implementation(project(":core:engine"))
    implementation(project(":core:remote"))
    implementation(project(":core:services"))
    implementation(project(":core:storage"))
    implementation(project(":engine:outpace"))
    implementation(project(":feature:warp"))
    implementation(project(":feature:playback"))
    implementation(project(":ui:design"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.profileinstaller)
    implementation(libs.coroutines.android)
    implementation(libs.gson)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit)
}
