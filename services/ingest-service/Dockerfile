# ---- Stage 1: build the jar with the full JDK ----
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:70898f0f893a6b772a0f29834d8b022e3ac20b6a0c33a922973cf66342ef56be AS build

WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY src src

# No daemon: it's never reused in a one-off container build
RUN ./gradlew bootJar --no-daemon


# ---- Stage 2: run the jar on a slim JRE ----
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:22138efd69393501fccd8176ae16b01791ed71ff801b28f0359415389b17c766

WORKDIR /app

COPY --from=build /workspace/build/libs/ingest-service.jar ingest-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ingest-service.jar"]
