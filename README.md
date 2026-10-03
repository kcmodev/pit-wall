# pit-wall

**Pit Wall** is an event-driven telemetry platform for Formula 1 data. An ingest service replays historical session data from the OpenF1 API into Kafka, where a stream processor turns raw car and timing events into race metrics. The results are stored in Postgres, served through a REST API, and visualized in Grafana dashboards. The system runs on Kubernetes, with GitHub Actions handling builds, tests and deployments. I'm building it to get hands-on experience with containers, event streaming and orchestration, using real-world data I genuinely enjoy working with.

**Status**: Early development. Currently containerizing the ingest service with Docker.


`docker start <container_name>` - starts the container
`docker stop <container_name>` - stops the container, but retains its writable layer.
`docker ps` - lists running containers
`docker ps -a` - lists all containers
`docker rm <container_name>` - removes the container
`docker rm -f <container_name>` - forcefully removes the container
`docker rm -v <container_name>` - removes the container and its volumes
`docker restart <container_name>` - restarts the container
`docker compose up` - starts the containers defined with the compose file
`docker compose down` - stops and removes the containers defined with the compose file
`docker volume` - manages volumes
`docker volume ls` - lists volumes
`docker volume rm <volume_name>` - removes the volume
`docker volume prune` - removes anonymous unused volumes
`docker volume prune --all` - removes all unused volumes
`docker run` - creates and starts a new container
`docker run -d` - creates and starts a new container in detached mode
`docker run -it` - creates and starts a new container in interactive mode
`docker run -p` - creates and starts a new container with port mapping
`docker run --name <container_name>` - assigns a name to the container
`docker exec` - runs a command in a running container
`docker logs` - displays the logs of a container

