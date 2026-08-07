plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.nachidel"
version = "0.1.0-SNAPSHOT"

application {
    //mainClass.set("com.nachidel.bambu.MainKt")
    mainClass.set("com.nachidel.bambu.explorer.MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`

    val bambuEmail = providers.gradleProperty("bambu.email")
    val bambuPassword = providers.gradleProperty("bambu.password")

    environment(
        "BAMBU_EMAIL",
        bambuEmail.getOrElse("")
    )

    environment(
        "BAMBU_PASSWORD",
        bambuPassword.getOrElse("")
    )

    environment(
        "BAMBU_TOKEN",
        providers.gradleProperty("bambu.token").getOrElse("")
    )
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.coroutines.core)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    //implementation(libs.serialization.json)

    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    implementation(libs.paho.client)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}