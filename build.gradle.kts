plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
    `maven-publish`
}

group = "com.nachidel"
version = "0.1.4"

application {
    mainClass.set("com.nachidel.bambu.MainKt")
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

    testImplementation(kotlin("test"))

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}

java {
    withSourcesJar()
}

publishing {

    publications {

        create<MavenPublication>("github") {

            from(
                components["java"]
            )

            groupId = "com.nachidel"
            artifactId = "bambu-cloud-kotlin"
            version = project.version.toString()

            pom {
                name.set("bambu-cloud-kotlin")

                description.set(
                    "Modern Kotlin SDK for the Bambu Lab Cloud API and MQTT services."
                )

                url.set(
                    "https://github.com/nachidel/bambu-cloud-kotlin"
                )

                licenses {
                    license {
                        name.set(
                            "GNU Affero General Public License v3.0"
                        )

                        url.set(
                            "https://www.gnu.org/licenses/agpl-3.0.html"
                        )
                    }
                }

                developers {
                    developer {
                        id.set("nachidel")
                        name.set("Nachidel")
                    }
                }

                scm {
                    url.set(
                        "https://github.com/nachidel/bambu-cloud-kotlin"
                    )

                    connection.set(
                        "scm:git:https://github.com/nachidel/bambu-cloud-kotlin.git"
                    )
                }
            }
        }
    }

    repositories {

        maven {

            name = "GitHubPackages"

            url =
                uri(
                    "https://maven.pkg.github.com/nachidel/bambu-cloud-kotlin"
                )

            credentials {

                username =
                    findProperty("gpr.user") as String?
                        ?: System.getenv("GITHUB_ACTOR")

                password =
                    findProperty("gpr.key") as String?
                        ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}