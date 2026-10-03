# Commands

- `docker start <container_name>` - starts the container
- `docker stop <container_name>` - stops the container, but retains its writable layer
- `docker ps` - lists running containers
- `docker ps -a` - lists all containers
- `docker rm <container_name>` - removes the container
- `docker rm -f <container_name>` - forcefully removes the container
- `docker rm -v <container_name>` - removes the container and its volumes
- `docker restart <container_name>` - restarts the container
- `docker compose up` - starts the containers defined with the compose file
- `docker compose down` - stops and removes the containers defined with the compose file
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

# Examples
- `docker run -d -p 5433:5432 --name pg-test -e POSTGRES_PASSWORD=testing postgres:18` - starts a new container in detached mode with port mapping, assigns a name, and sets the environment variable POSTGRES_PASSWORD
- `docker exec -it pg-test psql -U postgres` - runs a command in a running container
- `docker logs pg-test` - displays the logs of a container