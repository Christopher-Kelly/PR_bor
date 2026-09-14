plugins {
    id("java")
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

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

}

tasks.test {
    useJUnitPlatform()
}