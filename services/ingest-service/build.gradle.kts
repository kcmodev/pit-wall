plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.kcmodev"
version = "0.4.0"

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
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-restclient")

	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// MockRestServiceServer stands in for OpenF1, so tests don't depend on a public API
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	// Real Kafka and Postgres in Docker for integration tests; versions come from Spring Boot's dependency management
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-kafka")
	testImplementation("org.testcontainers:testcontainers-postgresql")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Used by the Dockerfile to download dependencies into their own image layer before the source is copied.
// Resolves only what compiling and packaging need; test dependencies stay out of the image build.
tasks.register("resolveDependencies") {
	group = "build setup"
	description = "Downloads the dependencies needed to compile and package the app."
	val classpaths = files(
		configurations.compileClasspath,
		configurations.annotationProcessor,
		configurations.named("productionRuntimeClasspath"),
	)
	doLast { classpaths.files }
}

tasks.bootJar {
	archiveFileName = "ingest-service.jar"
}

tasks.jar {
	enabled = false
}

tasks.withType<Test> {
	useJUnitPlatform()
	// Print each test's result, so a CI log shows what ran, not only failures
	testLogging {
		events("passed", "skipped", "failed")
	}
}
