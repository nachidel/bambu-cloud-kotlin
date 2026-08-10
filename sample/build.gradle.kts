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

    implementation(
        "org.slf4j:slf4j-api:2.0.17"
    )

    runtimeOnly(
        "ch.qos.logback:logback-classic:1.5.18"
    )
}

application {
    mainClass.set("com.nachidel.bambu.sample.MainKt")
}

tasks.named<JavaExec>("run") {

    standardInput = System.`in`

    providers.gradleProperty("bambu.token")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?.let {
            environment(
                "BAMBU_TOKEN",
                it
            )
        }

    providers.gradleProperty("bambu.email")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?.let {
            environment(
                "BAMBU_EMAIL",
                it
            )
        }

    providers.gradleProperty("bambu.password")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?.let {
            environment(
                "BAMBU_PASSWORD",
                it
            )
        }
}