# ---- Stage 1: build the jar with the full JDK ----
FROM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:70898f0f893a6b772a0f29834d8b022e3ac20b6a0c33a922973cf66342ef56be AS build

WORKDIR /workspace

COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

COPY src src

# Cache mount keeps Gradle's distribution and dependency cache between builds (not stored in the image).
# No daemon: it's never reused in a one-off container build
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon

RUN java -Djarmode=tools -jar /workspace/build/libs/ingest-service.jar extract --layers --destination extracted


# ---- Stage 2: run the jar on a slim JRE ----
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:22138efd69393501fccd8176ae16b01791ed71ff801b28f0359415389b17c766

WORKDIR /app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ingest-service.jar"]
