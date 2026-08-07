plugins {
    kotlin("jvm") version "2.3.20"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("com.nachidel.bambu.sample.MainKt")
}

tasks.named<JavaExec>("run") {

    standardInput = System.`in`

    val token =
        providers.gradleProperty("bambu.token").orNull

    if (!token.isNullOrBlank()) {
        environment(
            "BAMBU_TOKEN",
            token
        )
    }
}