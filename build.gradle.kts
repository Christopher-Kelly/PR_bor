plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.anthropic:anthropic-java:2.60.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.google.cloud:google-cloud-pubsub:1.135.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

}

tasks.test {
    useJUnitPlatform()
}