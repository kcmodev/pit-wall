# Gradle
## Files
- `build.gradle` - Configuration file that defines tasks, dependencies, and other instructions to tell gradle how to build a project
- `settings.gradle` - Settings to define root project and sub projects
- `Gradle/wrapper/ folder` - Contains gradle-wrapper.(jar/properties). The jar contains the gradle wrapper code responsible for downloading and installing gradle if necessary. The properties file contains configuration properties for the gradle wrapper such as distribution url and type

## Scripts
- `gradlew` - Gradle wrapper. Script that invokes a declared version of gradle and downloads it if necessary. Acts as a wrapper around gradle-wrapper.jar. used to execute gradle tasks on unix based systems without needing to manually install gradle.
- `gradlew build` - builds the project
- `gradlew test` - runs the tests
- `gradlew bootRun` - runs the Spring Boot application

# Spring Boot
## Files
- `src/main/resources/application.properties` - Spring Boot application properties file. Holds the app's default configuration packaged in the application jar file. These can be overridden by environment variables or command line arguments such as `SPRING_DATASOURCE_URL` overriding `spring.datasource.url`.