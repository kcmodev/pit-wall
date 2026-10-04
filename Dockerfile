FROM eclipse-temurin:21.0.12.1_1-jre-alpine

WORKDIR /app

COPY services/ingest-service/build/libs/ingest-service.jar ingest-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "ingest-service.jar"]