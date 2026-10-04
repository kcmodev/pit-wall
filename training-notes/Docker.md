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
- `docker build` - builds an image from a Dockerfile
- `docker build .` - builds an image from a Dockerfile in the current directory
- `docker build -t <image_name>` - builds an image from a Dockerfile in the current directory and assigns a name to the image

## Examples
- `docker run -d -p 5433:5432 --name pg-test -e POSTGRES_PASSWORD=testing postgres:18` - starts a new container in detached mode with port mapping, assigns a name, and sets the environment variable POSTGRES_PASSWORD
- `docker exec -it pg-test psql -U postgres` - runs a command in a running container
- `docker logs pg-test` - displays the logs of a container
- `docker run --rm --entrypoint ls ingest-service -la /dockerfile-data` - what's in the image                                                                                                                                                                              
- `docker run --rm -it --entrypoint sh ingest-service` - poke around interactively 
  -  `--entrypoint` overrides the image's start command, so you can look inside even when the app won't start.   


## How to run javac on an image (JDK vs. JRE)
- Use the same --entrypoint trick on the base image:                                                                                                                                                                                                                        
    - `docker run --rm --entrypoint sh amazoncorretto:21-alpine3.24 -c 'which javac'`                                                                                                                                                                                             
    - Result: /usr/bin/javac. That means your image is a full JDK. Corretto only publishes JDK images on Docker Hub. 
A JDK image includes the compiler and dev tools, which your container never uses: that's extra size and extra attack surface. 

- For comparison, Eclipse Temurin publishes -jre variants:                                                                                                                                                                                                                  
  - `docker run --rm --entrypoint sh eclipse-temurin:21-jre-alpine -c 'which javac'`                                                                                                                                                                                          
  no javac                                                                                                                                                                                                                                                                  
  You compile on your Mac with Gradle, so the container only needs a runtime. Switching to a JRE image is what your comment was asking for.    

## Exec form vs. shell form

When you run docker stop, Docker sends SIGTERM to PID 1, the first process in the container. If PID 1 doesn't exit within 10 seconds, Docker sends SIGKILL.

- Shell form (ENTRYPOINT java -jar ingest-service.jar): Docker actually runs `/bin/sh -c "java -jar ingest-service.jar`. `sh` becomes PID 1 and Java is its child. `sh` doesn't pass SIGTERM on, so Java never hears it. After 10 seconds it gets killed, and Spring's graceful shutdown (finishing in-flight requests, closing DB connections) never runs.
- `exec java -jar ingest-service.jar` replaces the shell process with Java, so Java becomes PID 1 and receives the signal.
- Exec form (ENTRYPOINT ["java", "-jar", "ingest-service.jar"]): no shell is involved; Docker starts Java directly as PID 1. The tradeoff is that you lose shell features like $VAR expansion.
