plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":net"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.register<JavaExec>("smoke") {
    description = "Prueba de extremo a extremo contra Renfe (necesita red): -Pstation=<stop_id>"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.n3k0chan.panelcasa.renfe.SmokeKt")
    maxHeapSize = "256m"
    args = listOf((project.findProperty("station") ?: "") as String)
}

tasks.register<JavaExec>("benchmark") {
    description = "Procesa un GTFS real y mide tiempo y memoria: -Pzip=<ruta> -Pstation=<stop_id>"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.n3k0chan.panelcasa.renfe.BenchmarkKt")
    maxHeapSize = "256m"
    args = listOf(
        (project.findProperty("zip") ?: "") as String,
        (project.findProperty("station") ?: "") as String,
    )
}
