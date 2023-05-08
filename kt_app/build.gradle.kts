plugins {
    kotlin("jvm") version "1.8.21"
    application
}

group = "io.github.dvgniele"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        setUrl("https://packages.confluent.io/maven/")
    }
}

dependencies {
    testImplementation(kotlin("test"))

    // https://mvnrepository.com/artifact/io.rest-assured/rest-assured
    implementation("io.rest-assured:rest-assured:5.3.0")
    // https://mvnrepository.com/artifact/io.rest-assured/json-path
    testImplementation("io.rest-assured:json-path:5.3.0")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    testImplementation("com.squareup.okhttp3:okhttp:4.11.0")

    // https://mvnrepository.com/artifact/junit/junit
    //testImplementation("junit:junit:4.13.2")

    // https://mvnrepository.com/artifact/org.junit.jupiter/junit-jupiter-api
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.3")


    // Jena Core
    // https://mvnrepository.com/artifact/org.apache.jena/jena-core
    implementation("org.apache.jena:jena-core:4.8.0")

    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    implementation("com.google.code.gson:gson:2.10.1")

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