plugins {
    id("java")
}

group = "sdle"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
}

tasks.test {
    useJUnitPlatform()
}