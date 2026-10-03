# Docker
## Commands
- `docker start <container_name>` - starts the container
- `docker stop <container_name>` - stops the container but retains its writable layer
- `docker ps` - lists running containers
- `docker ps -a` - lists all containers
- `docker rm <container_name>` - removes the container
- `docker rm -f <container_name>` - forcefully removes the container
- `docker rm -v <container_name>` - removes the container and its volumes
- `docker restart <container_name>` - restarts the container
- `docker compose up` - starts the containers defined with the associated compose file
- `docker compose down` - stops and removes the containers defined with the assocaited compose file
- `docker volume` - manages volumes
- `docker volume ls` - lists volumes
- `docker volume rm <volume_name>` - removes the volume
- `docker volume prune` - removes anonymous unused volumes
- `docker volume prune --all` - removes all unused volumes
- `docker run` - creates and starts a new container
- `docker run -d` - creates and starts a new container in detached mode
- `docker run -it` - creates and starts a new container in interactive mode
- `docker run -p` - creates and starts a new container with port mapping
- `docker run --name <container_name>` - assigns a name to the container
- `docker exec` - runs a command in a running container
- `docker logs` - displays the logs of a container

## Examples
- `docker run -d -p 5433:5432 --name pg-test -e POSTGRES_PASSWORD=testing postgres:18` - starts a new container in detached mode with port mapping, assigns a name, and sets the environment variable POSTGRES_PASSWORD
- `docker exec -it pg-test psql -U postgres` - runs a command in a running container
- `docker logs pg-test` - displays the logs of a container

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