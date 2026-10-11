# pit-wall

**Pit Wall** is an event-driven telemetry platform for Formula 1 data that I'm building in stages. The target architecture: an ingest service replays historical session data from the OpenF1 API into Kafka, and a stream processor turns raw car and timing events into race metrics. The results will be stored in Postgres, served through a REST API and visualized in Grafana dashboards. The system will run on Kubernetes, with GitHub Actions handling builds, tests and deployments. I'm building it to get hands-on experience with containers, event streaming and orchestration, using real-world data I genuinely enjoy working with.

## Running locally

The current stack is two containers managed by Docker Compose: `ingest-service` (a Spring Boot app) and `postgres` (Postgres 18). The ingest service doesn't pull F1 data yet. For now, a healthy stack means the app is up, connected to Postgres and has applied its database migrations.

### Prerequisites

| Tool                    | Minimum version | Check with               |
|-------------------------|-----------------|--------------------------|
| Git                     | any             | `git --version`          |
| Docker Engine           | 25.0            | `docker version`         |
| Docker Compose (plugin) | 2.24.0          | `docker compose version` |

A current Docker Desktop includes both Docker requirements. The version floors come from the `start_interval` healthcheck option in `compose.yaml`, which older versions don't support.

You don't need a local JDK or Gradle, because the app is compiled inside Docker.

### Configuration

Clone the repo and create your `.env` from the template:

```sh
git clone https://github.com/kcmodev/pit-wall.git
cd pit-wall
cp .env.example .env
```

Open `.env` and replace each `<placeholder>` with a value of your choice. These values are for local use only, so pick anything:

| Variable              | What it's for                                                                 | Example     |
|-----------------------|-------------------------------------------------------------------------------|-------------|
| `DATASOURCE_DB_NAME`  | Name of the database Postgres creates on first start. The app connects to it. | `pitwall`   |
| `DATASOURCE_USERNAME` | Postgres user created on first start. The app logs in as this user.           | `pitwall`   |
| `DATASOURCE_PASSWORD` | Password for that user. Used by both containers.                              | `change-me` |

If any value is missing, `docker compose` stops with `set it in .env`. `.env` is gitignored, so don't commit real credentials anywhere else.

Postgres only reads these values when it initializes an empty data volume. If you change them later, you have to reset the database (see [Stop and reset](#stop-and-reset)) before they take effect.

### Start

```sh
docker compose up --build --wait
```

The first run builds the app image and initializes the database, so expect a few minutes. Later runs reuse the build cache. On startup, Flyway applies the database migrations. `--wait` runs the stack in the background and returns only after both containers are healthy. If either one doesn't become healthy, the command fails.

### Verify

```sh
docker compose ps
```

Both services should show `Up ... (healthy)`. Then query the app directly:

```sh
curl http://127.0.0.1:8080/actuator/health
```

Look for `"status":"UP"` at the top level, and `"db":{...,"status":"UP"}` inside `components`. That confirms the app is reachable from your machine and can query Postgres.

Optionally, run `docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'` to confirm the migrations ran. You should see `sessions` and `flyway_schema_history`.

### Stop and reset

| Command                  | Containers           | Database data         |
|--------------------------|----------------------|-----------------------|
| `docker compose stop`    | Stopped, kept        | Kept                  |
| `docker compose down`    | Removed              | Kept                  |
| `docker compose down -v` | Removed              | **Deleted**           |

- **To stop for the day,** use `docker compose stop` or `docker compose down`. Your data lives in the `postgres_data` volume, and both commands leave it alone. Run `docker compose up --wait` to start again.
- **To wipe the database,** use `docker compose down -v`. **This permanently deletes the `postgres_data` volume and everything in it.** The next `up` starts with an empty volume, so Postgres re-initializes from `.env` and Flyway runs every migration again. Use this after changing `.env` credentials or to get a clean slate.

After code changes, rebuild with `docker compose up --build --wait`. This doesn't touch your data.

### Ports

| Service          | Host address     | Container port | Reachable from         |
|------------------|------------------|----------------|------------------------|
| `ingest-service` | `127.0.0.1:8080` | `8080`         | Your machine only      |
| `postgres`       | `127.0.0.1:5433` | `5432`         | Your machine only      |

- **Why 5433?** It avoids clashing with a local Postgres on the default 5432. Point DB tools at `127.0.0.1:5433` with the credentials from `.env`.
- **Why `127.0.0.1`?** Without a host IP, Docker publishes on every network interface, so any device on your network could reach it.

### Troubleshooting

- **`up` fails with `port is already allocated`:** another process is using `8080` or `5433`. Stop it, or change the left side of the mapping in `compose.yaml` (for example, `"127.0.0.1:8081:8080"`).
- **The ingest service is in a restart loop, and `docker compose logs ingest-service` shows `password authentication failed`:** `.env` changed after the database was initialized. Run `docker compose down -v` (this **deletes DB data**), then [Start](#start) again.
- **The ingest service is in a restart loop, and `docker compose logs ingest-service` shows `UnknownHostException: postgres`:** the Postgres container isn't running. Start the whole stack with `docker compose up --wait` rather than starting a single service.