plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.kcmodev"
version = "0.3.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
//    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")

//    runtimeOnly("org.postgresql:postgresql")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveFileName = "stream-processor.jar"
}

tasks.jar {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
}
