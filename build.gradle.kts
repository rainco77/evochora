import java.io.FileOutputStream;

plugins {
    java
    application
    jacoco
}

group = "org.evochora"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.6")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("info.picocli:picocli:4.7.6")
    implementation("io.javalin:javalin:6.1.3")
    implementation("com.google.code.gson:gson:2.10.1")

}

// Definiert die Hauptklasse für den 'run'-Task
application {
    mainClass.set("org.evochora.app.Main")
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run the Evochora server CLI"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.evochora.server.CommandLineInterface")
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Duser.language=en", "-Duser.country=US")
    finalizedBy(tasks.jacocoTestReport)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    include("org/evochora/**")
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude("org/evochora/ui/**", "org/evochora/Main*")
            }
        })
    )
}