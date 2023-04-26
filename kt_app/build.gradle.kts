plugins {
    kotlin("jvm") version "1.8.21"
    application
}

group = "io.github.dvgniele"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.apache.jena:jena-core:4.8.0")
    //implementation("com.github.wonderbird:spike:1.0.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}