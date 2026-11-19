import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.n3k0chan.panelcasa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.n3k0chan.panelcasa"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Firma de publicación, fuera del repositorio, en la carpeta de credenciales del contenedor.
    // Las contraseñas y el alias salen de keystore.properties; la clave, siempre de la ruta del contenedor.
    // Si falta algo, el APK de release sale sin firmar.
    val signingProps = file("/root/credenciales/Android/keystore.properties")
    val store = file("/root/credenciales/app-key.jks")
    val releaseSigning = if (signingProps.isFile && store.isFile) {
        val p = Properties().apply { signingProps.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = store
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    } else null

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time en Android 7 (API 24).
        isCoreLibraryDesugaringEnabled = true
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/panelAssets"))

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    jvmToolchain(17)
}

// El panel web vive en /panel para poder probarlo en un navegador; aquí se copia a los assets sin los ficheros de pruebas.
val copyPanel = tasks.register<Sync>("copyPanel") {
    from(rootProject.file("panel")) {
        exclude("dev.html", "mock-data.js")
    }
    into(layout.buildDirectory.dir("generated/panelAssets/panel"))
}
tasks.named("preBuild") { dependsOn(copyPanel) }

dependencies {
    implementation(project(":renfe"))
    implementation(project(":meteo"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
}
