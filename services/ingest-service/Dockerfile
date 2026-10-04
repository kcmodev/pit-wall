FROM eclipse-temurin:21.0.12.1_1-jre-noble@sha256:22138efd69393501fccd8176ae16b01791ed71ff801b28f0359415389b17c766

WORKDIR /app

COPY build/libs/ingest-service.jar ingest-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ingest-service.jar"]