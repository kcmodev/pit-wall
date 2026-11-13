# ---- Stage 1: build the jar with the full JDK ----
# --platform=$BUILDPLATFORM: this stage always runs on the build machine's own architecture, even when the
# image is built for another one. A jar is bytecode, the same on every platform, so it's built once, natively,
# and copied into each platform's runtime stage. Only stage 2 is per-platform.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.12.1_1-jdk-noble@sha256:70898f0f893a6b772a0f29834d8b022e3ac20b6a0c33a922973cf66342ef56be AS build

WORKDIR /workspace

COPY --chmod=0755 gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

# No daemon: it's never reused in a one-off container build
RUN ./gradlew resolveDependencies --no-daemon

COPY src src

# --offline: everything was downloaded above, so a missing dependency fails the build here
RUN ./gradlew bootJar --no-daemon --offline

RUN java -Djarmode=tools -jar /workspace/build/libs/stream-processor.jar extract --layers --destination extracted


# ---- Stage 2: run the jar on a slim JRE ----
# No --platform: Docker picks the JRE variant for the target platform (amd64 or arm64) from the pinned index digest.
FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:22138efd69393501fccd8176ae16b01791ed71ff801b28f0359415389b17c766

WORKDIR /app

RUN groupadd --gid 10001 app \
 && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001:10001

EXPOSE 8081

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD ["curl", "-fsS", "http://localhost:8081/actuator/health"]

ENTRYPOINT ["java", "-jar", "stream-processor.jar"]
