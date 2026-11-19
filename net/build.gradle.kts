plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.register<JavaExec>("liveTls") {
    description = "Conexión real a Renfe y Met.no validando como Android 7.0: -Paosp=<dir .0> -Proots=<dir .pem>"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.n3k0chan.panelcasa.net.LiveTlsKt")
    args = listOf((project.findProperty("aosp") ?: "") as String, (project.findProperty("roots") ?: "") as String)
}
