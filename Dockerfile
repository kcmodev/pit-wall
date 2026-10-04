# Base image: a Java *runtime* (not a JDK), matching the Java version from Initializr.
# Pin a specific tag. Check it supports your Mac's architecture.
FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555

# The directory inside the image where the following instructions operate.
# Pick a sensible path for your app.
WORKDIR /ingest-service

# Copy the boot jar from your Mac into the image.
# Source path is relative to the build context (the folder you run `docker build` in).
# Which jar? Give it a simple name at the destination.
COPY services/ingest-service/build/libs/ingest-service.jar ingest-service.jar

# Document the port the app listens on inside the container.
# (Remember: this doesn't publish anything by itself.)
EXPOSE 8080

# The command that starts the app when a container runs.
# Exec form (JSON array) or shell form? Decide, and know why.
# If it is run in shell form java becomes a child process and when docker stop is executed it will have to wait 10 seconds before everything closes since the shell does not pass on SIGTERM and java will never hear it.
# i accidentially fixed this by using exec due to the ide suggestion which replaces the shell process with java. my original entrypoint was 'exec java -jar ingest-service.jar'
# this final form is the correct implementation to run it as PID 1 so it receives SIGTERM when executing docker stop to shit down gracefully with the tradeoff being losing shell features like $VAR expansion
ENTRYPOINT ["java", "-jar", "ingest-service.jar"]