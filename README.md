# pit-wall

[![CI](https://github.com/kcmodev/pit-wall/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kcmodev/pit-wall/actions/workflows/ci.yml)

**Pit Wall** is an event-driven telemetry platform for Formula 1 data that I'm building in stages. The target architecture: an ingest service replays historical session data from the OpenF1 API into Kafka, and a stream processor turns raw car and timing events into race metrics. The results will be stored in Postgres, served through a REST API, and visualized in Grafana dashboards. The system will run on Kubernetes, with GitHub Actions handling builds, tests, and deployments. I'm building it to get hands-on experience with containers, event streaming, and orchestration, using real-world data I genuinely enjoy working with.

## Status

- [x] **Docker:** ingest-service and stream-processor containerized, running with PostgreSQL and Kafka via Docker Compose
- [x] **Kubernetes:** running on a local kind cluster, deployed with Kustomize
- [x] **Kafka:** ingest-service replays OpenF1 lap data into Kafka on request; stream-processor stores lap times in Postgres, dead-letters bad records, and can reprocess the topic
- [x] **CI:** GitHub Actions builds and tests both services, smoke-tests the deployment on a kind cluster, and publishes amd64 and arm64 images to GitHub Container Registry
- [ ] Stream processing, REST API, Grafana dashboards, continuous deployment

## Contents

- [Running locally (Docker Compose)](#running-locally)
- [Local Kubernetes cluster (kind)](#local-kubernetes-cluster-kind)
- [Deploy to the local cluster](#deploy-to-the-local-cluster)
- [Kafka (local)](#kafka-local)
- [Replay a session](#replay-a-session)
- [Stream processor](#stream-processor)
- [Continuous integration](#continuous-integration)

---

## Running locally

Runs ingest-service and stream-processor (Spring Boot), PostgreSQL 18, and Kafka with Docker Compose. A healthy stack means both apps are up, connected to Postgres, and have applied their database migrations. To pull F1 data into Kafka, [replay a session](#replay-a-session).

### Prerequisites

| Tool                    | Minimum version | Check with               |
|-------------------------|-----------------|--------------------------|
| Git                     | any             | `git --version`          |
| Docker Engine           | 25.0            | `docker version`         |
| Docker Compose (plugin) | 2.22.0          | `docker compose version` |

> [!NOTE]
> A current Docker Desktop covers both Docker requirements. The Docker Engine floor comes from the `start_interval` healthcheck option in `compose.yaml`. The Compose floor comes from `docker compose watch` (see [Develop](#develop)).
> You don't need a local JDK or Gradle. The app is compiled inside Docker. A JDK is only needed to [run the app outside Docker](#develop).

### 1. Configure

```bash
git clone https://github.com/kcmodev/pit-wall.git
cd pit-wall
cp .env.example .env
```

Replace each `<placeholder>` in `.env`. The values are for local use only, so pick anything:

| Variable              | What it's for                                                       | Example     |
|-----------------------|---------------------------------------------------------------------|-------------|
| `POSTGRES_DB`         | Database Postgres creates on first start. The app connects to it.   | `pitwall`   |
| `POSTGRES_USER`       | Postgres user created on first start. The app logs in as this user. | `pitwall`   |
| `POSTGRES_PASSWORD`   | Password for that user. Used by both containers.                    | `change-me` |

> [!NOTE]
> If a value is missing, `docker compose` stops with `set it in .env`. `.env` is gitignored, so credentials stay out of git.
> Postgres only reads these values when it initializes an empty data volume. To change them later, [reset the database](#stop-and-reset) first.

### 2. Start

```bash
docker compose up --build --wait
```

> [!NOTE]
> `--wait` runs the stack in the background and returns once every container is healthy, or fails if one doesn't get there. The first run builds the image and initializes the database, so expect a few minutes. Flyway applies each app's migrations on startup; ingest-service creates the `f1.laps` topic and stream-processor creates `f1.laps.DLT`.
> If it fails with `port is already allocated`, another process is using `8080`, `8081`, `5433` or `9092`. Stop it, or change the host side of the mapping in `compose.yaml` (e.g. `"127.0.0.1:8090:8080"`).
> If an app keeps restarting, check `docker compose logs ingest-service` (or `stream-processor`). `password authentication failed` means the database was initialized with a different password than `.env` now has (`.env` changed, or `POSTGRES_PASSWORD` is exported in your shell, which takes precedence over `.env`). Either set the database's password to the `.env` value, keeping the data:
>
> ```bash
> docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v u="$POSTGRES_USER" -v pw="$POSTGRES_PASSWORD" <<"SQL"
> ALTER USER :"u" PASSWORD :'\''pw'\'';
> SQL'
> ```
>
> or run `docker compose down -v` (**deletes DB data**) and start again. `UnknownHostException: postgres` means the Postgres container isn't running: run `docker compose up --wait`.

### 3. Verify

```bash
docker compose ps
curl -s http://127.0.0.1:8080/actuator/health
```

All four services should show `Up ... (healthy)`. In ingest-service's health response, expect `"status":"UP"` at the top level and `"db":{...,"status":"UP"}` inside `components`, which confirms the app can query Postgres.

To confirm the migrations ran (expect `sessions` and `flyway_schema_history` in `public`, and `lap_times` and `flyway_schema_history` in `stream_processor`):

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt" -c "\dt stream_processor.*"'
```

stream-processor has its own health endpoint at `http://127.0.0.1:8081/actuator/health`.

| Service            | Host address     | Container port |
|--------------------|------------------|----------------|
| `ingest-service`   | `127.0.0.1:8080` | `8080`         |
| `stream-processor` | `127.0.0.1:8081` | `8081`         |
| `postgres`         | `127.0.0.1:5433` | `5432`         |
| `kafka`            | `127.0.0.1:9092` | `9092`         |

> [!NOTE]
> Postgres is published on 5433 to avoid clashing with a local Postgres on 5432. Point DB tools at `127.0.0.1:5433` with the credentials from `.env`.
> All ports bind to `127.0.0.1`, so only your machine can reach them. Without a host IP, Docker publishes on every network interface.

### Develop

Each app is compiled into its image, so a plain `docker compose up` keeps running the previous build after code changes. There are two ways to pick up changes without rebuilding by hand.

**Rebuild on save with `watch`:**

```bash
docker compose watch
```

Starts the stack, then rebuilds and restarts a service whenever a file under its `src`, `build.gradle.kts` or `settings.gradle.kts` changes (both ingest-service and stream-processor). Postgres and Kafka keep running, and dependencies are cached in their own image layer, so a code change rebuilds in roughly the compile time. It runs in the foreground; stop watching with Ctrl-C, and use `docker compose ps` to see what's still running.

**Run the app on your machine** (fastest restarts, lets you attach a debugger; requires JDK 21):

```bash
docker compose up -d --wait postgres kafka
cd services/ingest-service
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/<POSTGRES_DB> \
SPRING_DATASOURCE_USERNAME=<POSTGRES_USER> \
SPRING_DATASOURCE_PASSWORD=<POSTGRES_PASSWORD> \
./gradlew bootRun
```

Use the values from `.env`. The app reaches Kafka on `127.0.0.1:9092` by default.

> [!NOTE]
> If ingest-service is already running in Compose, stop it first with `docker compose stop ingest-service`, or `bootRun` fails because port 8080 is taken.
> `docker compose up --build --wait` still works for a one-off rebuild.

**Run the tests** (requires JDK 21 and a running Docker daemon):

```bash
cd services/ingest-service && ./gradlew test
cd services/stream-processor && ./gradlew test
```

The integration tests use Testcontainers to start their own Kafka and PostgreSQL, from the same image digests as `compose.yaml`, so the Compose stack doesn't need to be running. OpenF1 is replaced by saved responses, so the tests don't call the public API. CI runs the same command for both services on every push and pull request.

### Stop and reset

| Command                  | Containers    | Database data |
|--------------------------|---------------|---------------|
| `docker compose stop`    | Stopped, kept | Kept          |
| `docker compose down`    | Removed       | Kept          |
| `docker compose down -v` | Removed       | **Deleted**   |

To start again after `stop` or `down`, run `docker compose up --wait`. After code changes, see [Develop](#develop). None of these touch your data.

> [!WARNING]
> `docker compose down -v` permanently deletes the `postgres_data` volume. The next `up` re-initializes Postgres from `.env` and Flyway runs every migration again. Use it after changing `.env` credentials or to get a clean slate.

---

## Local Kubernetes cluster (kind)

Creates a two-node [kind](https://kind.sigs.k8s.io/) cluster named `pit-wall` (one control-plane node, one worker), defined in `k8s/kind/cluster.yaml`. kind runs each node as a Docker container. For now, a healthy cluster means both nodes are `Ready` and the system pods are running.

### Prerequisites

| Tool          | Minimum version | Check with                 |
|---------------|-----------------|----------------------------|
| Docker Engine | 25.0            | `docker version`           |
| kind          | 0.33.0          | `kind version`             |
| kubectl       | 1.36            | `kubectl version --client` |

> [!NOTE]
> kind 0.33.0 is the first release built for the `kindest/node:v1.37.0` image pinned in `cluster.yaml`.
> kubectl supports clusters one minor version above or below its own, so 1.36 to 1.38 works with a 1.37 cluster.

### 1. Create

From the repo root:

```bash
kind create cluster --config k8s/kind/cluster.yaml
```

> [!NOTE]
> This starts the `pit-wall-control-plane` and `pit-wall-worker` containers, adds a `kind-pit-wall` context to your kubeconfig, and switches kubectl to it. The first run downloads the node image, so expect a few minutes.
> If it fails with `node(s) already exist for a cluster with the name "pit-wall"`, the cluster already exists. Use it as is, or [delete](#delete) it and create it again.

### 2. Verify

```bash
kubectl wait --for=condition=Ready nodes --all --timeout=120s
kubectl get pods -A
```

Every pod in `kube-system` and `local-path-storage` should show `Running`.

> [!NOTE]
> `kind create` doesn't wait for nodes to become `Ready`, so `kubectl wait` blocks until both are.
> If the output shows a different cluster, check `kubectl config current-context`. It should print `kind-pit-wall`. Switch with `kubectl config use-context kind-pit-wall`.
> If kubectl fails with `The connection to the server 127.0.0.1:<port> was refused`, the node containers are stopped, usually after Docker restarted. Run `docker start pit-wall-control-plane pit-wall-worker`, wait a minute, and verify again. If pods don't return to `Running`, delete the cluster and create it again.

### Delete

```bash
kind delete cluster --name pit-wall
```

> [!WARNING]
> Deletes the cluster and everything running in it, and removes the `kind-pit-wall` context. `--name` is required: `kind delete` doesn't read `cluster.yaml`, and otherwise targets a cluster named `kind`.

---

## Deploy to the local cluster

Deploys ingest-service, stream-processor, PostgreSQL 18 and Kafka into the `pit-wall` namespace, using the Kustomize manifests in `k8s/base`. Requires the kind cluster from the previous section. Inside the cluster, both services reach Kafka at `kafka:9092`.

### 1. Build and load the images

From the repo root:

```bash
docker build -t pit-wall/ingest-service:0.4.0 services/ingest-service
docker build -t pit-wall/stream-processor:0.4.0 services/stream-processor
kind load docker-image pit-wall/ingest-service:0.4.0 pit-wall/stream-processor:0.4.0 --name pit-wall
```

> [!NOTE]
> There's no registry, so `kind load` copies the image into each node, and the Deployment uses `imagePullPolicy: Never`.
> Image tags follow each app's version in its `build.gradle.kts`, and must match its `newTag` in `k8s/base/kustomization.yaml`. If the version is no longer `0.4.0`, use the current one in both commands. After code changes, bump the version, build with the new tag, and update `newTag`. Reusing a tag keeps the old image running.

### 2. Create the database secret

From the repo root:

```bash
kubectl create namespace pit-wall
kubectl create secret generic postgres-credentials -n pit-wall --from-env-file=.env
```

> [!NOTE]
> Uses the same `.env` as the Compose setup, so create it first (see [Configure](#1-configure)). Each line becomes a key in the secret, and credentials never land in git.

### 3. Deploy

```bash
kubectl apply -k k8s/base
```

> [!NOTE]
> The first apply may warn that `namespaces/pit-wall` is missing the `last-applied-configuration` annotation, because step 2 created it. kubectl adds the annotation itself, so the warning is harmless.
> On later runs, `statefulset.apps/postgres configured` may appear even when nothing changed (a known client-side apply quirk). `kubectl diff -k k8s/base` shows real changes; empty output means none.

### 4. Verify

```bash
kubectl rollout status -n pit-wall statefulset/postgres --timeout=180s
kubectl rollout status -n pit-wall statefulset/kafka --timeout=180s
kubectl rollout status -n pit-wall deployment/ingest-service --timeout=180s
kubectl rollout status -n pit-wall deployment/stream-processor --timeout=180s
kubectl get pods -n pit-wall
```

All four pods should show `1/1` under `READY`. Then check the health endpoint:

```bash
kubectl port-forward -n pit-wall deployment/ingest-service 8080:8080
# in a second terminal
curl -s http://127.0.0.1:8080/actuator/health
```

In the health response, expect `"status":"UP"` at the top level and `"db":{...,"status":"UP"}` inside `components`, which confirms the app can query Postgres. Stop `port-forward` with Ctrl-C.

To confirm the migrations ran (expect `sessions` and `flyway_schema_history` in `public`, and `lap_times` and `flyway_schema_history` in `stream_processor`):

```bash
kubectl exec -n pit-wall postgres-0 -- sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt" -c "\dt stream_processor.*"'
```

> [!NOTE]
> stream-processor's `f1.laps` topic is created by ingest-service on startup; until then the processor logs `UNKNOWN_TOPIC_OR_PARTITION` warnings and picks the topic up once it exists.
> Both services may restart a few times, or show `0/1`, until Postgres and Kafka are reachable. That's deliberate: Flyway needs the database at startup, and with `spring.kafka.admin.fail-fast=true` each service fails startup rather than run without the topics it declares. The database is also part of their readiness checks.
> If `port-forward` fails with "address already in use", stop whatever else is on port 8080 (e.g. `docker compose down`).

### Smoke test

Deploys, waits for every workload to be ready, sends one lap through `f1.laps` and checks that stream-processor stored it. CI runs it on a fresh kind cluster for every push and pull request, before any image is published. To run it yourself, build and load the images (step 1), then:

```bash
k8s/smoke-test.sh 0.4.0
```

Expect `OK: lap for session <n> stored in stream_processor.lap_times` within a minute or two. The argument is the image tag to deploy. If the namespace has no `postgres-credentials` secret, the script creates one with generated credentials, so it also works on a brand-new cluster.

### Remove

```bash
kubectl delete -k k8s/base
```

> [!WARNING]
> Deletes the `pit-wall` namespace and everything in it, **including the database volume and the secret**. To redeploy, start again from step 2.

---

## Kafka (local)

Pit Wall uses Apache Kafka (KRaft mode, single combined broker/controller) as the event backbone between ingest and processing. It starts with the rest of the stack:

    docker compose up -d --wait
    docker compose ps   # kafka should report "healthy"

| Client location | Bootstrap address |
|---|---|
| Other Compose services | `kafka:29092` |
| Host (IDE, local tools) | `127.0.0.1:9092` |

> [!NOTE]
> Automatic topic creation is disabled, so topics must be declared explicitly, as they would be in production. Each service declares the topics it produces and creates them on startup if they don't exist: ingest-service declares `f1.laps` (3 partitions) in `KafkaTopicConfig`, and stream-processor declares `f1.laps.DLT` (3 partitions) in `KafkaErrorHandlingConfig`.

### Inspecting Kafka with the CLI

The Kafka CLI tools are included in the broker image, so no local install is needed. The examples elsewhere in this README use a `kx` shell function that runs a tool in the container with the bootstrap server filled in; add it to `~/.zshrc` or `~/.bashrc`:

    # kx <tool> [args...], e.g. kx kafka-topics --list
    kx() { docker compose exec kafka /opt/kafka/bin/"$1".sh --bootstrap-server localhost:9092 "${@:2}"; }

`docker compose` finds `compose.yaml` from the current directory, so run `kx` from the repo root (or add `--project-directory <path-to-repo>`). The full commands without `kx`:

    # list topics
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

    # create a topic
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --topic <topic> --partitions 3 --replication-factor 1

    # describe a topic (partitions, leader, replicas)
    docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic <topic>

    # check consumer group offsets and lag
    docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group <group>

> [!NOTE]
> This is a single-node development setup: replication factor 1, no authentication, and combined broker/controller roles. Production Kafka runs at least three brokers with replication, TLS, and authentication, separate controllers, and is typically a managed service.

> [!WARNING]
> The broker port is bound to `127.0.0.1` only. Do not expose it on other interfaces; the PLAINTEXT listeners have no authentication.

### Topics

| Topic | Key | Value | Producer | Purpose |
|---|---|---|---|---|
| `f1.laps` | `<sessionKey>-<driverNumber>` | `LapEvent` (JSON) | ingest-service | One record per lap |
| `f1.laps.DLT` | Original record's key | Original bytes, or `LapEvent` (JSON) if it deserialized | stream-processor | Records that failed processing, with `kafka_dlt-*` failure headers; same partition number as the original |

> [!NOTE]
> Kafka guarantees ordering per partition, and the key determines the partition. Keying by session and driver keeps each driver's laps in order within a session while spreading a session across partitions. Cross-driver metrics (gaps, positions) are computed downstream from stored data.

---

## Replay a session

ingest-service fetches every lap of a session from the [OpenF1 API](https://openf1.org) and publishes each one to `f1.laps`. Requires the [Compose stack](#running-locally) to be running. On kind, `kubectl port-forward -n pit-wall deployment/ingest-service 8080:8080` first; the commands below that use `docker compose exec` are Compose-only.

```bash
curl -s -X POST http://127.0.0.1:8080/api/replays/9161
```

`9161` is an OpenF1 `session_key` (2023 Singapore Grand Prix qualifying). Find others at `https://api.openf1.org/v1/sessions?year=2023`. The request returns once Kafka has confirmed or rejected every lap:

```json
{"sessionKey":9161,"totalLaps":297,"lapsPublished":297,"lapsFailed":0}
```

| Status                    | Meaning                                                                  |
|---------------------------|--------------------------------------------------------------------------|
| `200 OK`                  | Every lap was published                                                  |
| `404 Not Found`           | OpenF1 has no laps for that session key                                  |
| `503 Service Unavailable` | Some laps failed to publish (`lapsFailed` > 0). Check that Kafka is up, then retry |
| `500 Internal Server Error` | OpenF1 returned an error or couldn't be reached                       |

Laps are published in race order: lap 1 for every driver, then lap 2, and so on. Each driver's laps stay in order within their partition.

To see the records, read the topic from the start:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic f1.laps --from-beginning --formatter-property print.key=true --max-messages 5
```

> [!NOTE]
> If Kafka is down, the request can take up to 2 minutes before returning `503`. That's the producer's `delivery.timeout.ms`, after which each send fails.

> [!WARNING]
> Replaying a session again, including retrying after a `503`, publishes every lap again. The topic will then contain duplicates; stream-processor's upserts absorb them (see [Lap times in Postgres](#lap-times-in-postgres)).

---

## Stream processor

stream-processor consumes `f1.laps`, stores lap times in Postgres, dead-letters bad records, and can reprocess the topic from the start. The examples assume the [Compose stack](#running-locally) is running and a session has been [replayed](#replay-a-session).

### Consumer group

`services/stream-processor` consumes `f1.laps` as the consumer group `stream-processor`. It deserializes into its own `LapEvent` record rather than sharing ingest's class: the JSON on the topic is the contract, so the two services build and deploy independently.

To see partitions split across a group, run two more instances on your machine next to the one in Compose (requires JDK 21). Use the values from `.env`:

    cd services/stream-processor
    ./gradlew bootJar
    POSTGRES_DB=<POSTGRES_DB> POSTGRES_USER=<POSTGRES_USER> POSTGRES_PASSWORD=<POSTGRES_PASSWORD> \
      java -jar build/libs/stream-processor.jar --server.port=8082
    # in another terminal, the same command with --server.port=8083
    kx kafka-consumer-groups --describe --group stream-processor   # from the repo root: one partition per instance

On your machine the app reaches Postgres on `127.0.0.1:5433` and Kafka on `127.0.0.1:9092` by default.

> [!NOTE]
> Parallelism is capped by partition count: `f1.laps` has 3 partitions, so a 4th instance would sit idle. `auto.offset.reset=earliest` only applies when the group has no committed offset; a restarted instance resumes from the group's committed offsets.

### Lap times in Postgres

The stream processor writes one row per lap to `stream_processor.lap_times`, keyed by the natural key `(session_key, driver_number, lap_number)`. Durations are stored as integer milliseconds; `timed_lap` marks laps with a time that aren't pit-out laps.

Kafka delivery is at-least-once, and replaying a session publishes the same laps again, so the processor must tolerate duplicates. Writes are upserts (`INSERT ... ON CONFLICT ... DO UPDATE`): processing the same event twice leaves the same row. Replaying a session twice leaves the row count unchanged and only advances `processed_at`.

    docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
      -c "SELECT count(*), max(processed_at) FROM stream_processor.lap_times;"'

> [!NOTE]
> The schema is managed by Flyway (`src/main/resources/db/migration`) and applied when the processor starts. The processor owns the `stream_processor` Postgres schema, with its own Flyway history, so its migration versions can't collide with ingest-service's in `public`. Applied migrations are never edited; changes go in a new versioned file.

### Error handling and dead-letter topic

Failures are handled by type:

| Failure | Example | Handling |
|---|---|---|
| Bad data | malformed JSON, missing required field, negative ID, empty value | Published to `f1.laps.DLT` immediately; the partition moves on |
| Infrastructure | Postgres unavailable, deadlock, dropped connection | Retried with exponential backoff (1s → 30s cap) with no attempt limit, until it succeeds; lag grows meanwhile |
| Unexpected | a bug in the processor | Published to `f1.laps.DLT` immediately |

Only failures known to be transient are retried (Spring's `DataAccessResourceFailureException`, `TransientDataAccessException` and `RecoverableDataAccessException`); everything else is dead-lettered. Retrying an unknown error forever could block a partition on a record that will never succeed.

Required fields (`sessionKey`, `driverNumber`, `lapNumber`, `pitOutLap`) are boxed types in `LapEvent`, so a missing key arrives as `null` and fails validation instead of silently becoming `0` or `false`.

The dead-letter topic is for bad data, not outages: dead-lettering during an outage would fill it with valid laps that need manual replay. Each dead-lettered record keeps its original key and partition and carries headers describing the failure (`kafka_dlt-exception-message`, `kafka_dlt-original-offset`, ...).

    kx kafka-console-consumer --topic f1.laps.DLT --from-beginning --formatter-property print.headers=true

To see each path, keep the consumer above running and send records with `kx kafka-console-producer --topic f1.laps`:

| Path | Send | Result |
|---|---|---|
| Poison pill | `{this is not json` | Appears in the DLT as the original bytes; `kafka_dlt-exception-fqcn` is `DeserializationException` |
| Invalid event | `{}` or `{"sessionKey":9888,"driverNumber":-1,"lapNumber":1,"pitOutLap":false}` | Appears in the DLT re-serialized as a `LapEvent`, with `InvalidLapEventException` as the cause |

In both cases lag returns to 0 and the `lap_times` count is unchanged.

For an outage, stop Postgres, replay a session, then start it again:

    docker compose stop postgres
    curl -s localhost:8081/actuator/health/readiness   # DOWN (HTTP 503)
    curl -X POST localhost:8080/api/replays/9888       # ingest doesn't need Postgres; all laps publish
    kx kafka-consumer-groups --describe --group stream-processor   # LAG rises and holds
    kx kafka-get-offsets --topic f1.laps.DLT           # unchanged: nothing dead-lettered
    docker compose start postgres                      # within ~30s (the next retry), LAG drains to 0

`docker compose logs stream-processor` shows `CannotGetJdbcConnectionException` at growing intervals while Postgres is down. Afterwards the `lap_times` count is unchanged and `max(processed_at)` is recent: every lap was reprocessed, and the upserts absorbed the duplicates.

> [!NOTE]
> `kafka-get-offsets` prints `partition:offset` pairs, where the offset is each partition's log-end offset (the offset the next record will get). With nothing deleted by retention, that equals the partition's record count.

> [!NOTE]
> Retries are blocking: while a record retries, the rest of its partition waits. This preserves per-driver lap order. Non-blocking retry topics would keep the partition moving but give up that ordering.

> [!WARNING]
> Nothing consumes the dead-letter topic yet. In production it would be monitored and alerted on, with tooling to inspect and replay fixed records.

### Reprocessing from the log

Kafka keeps records after they're consumed, so changing how the processor computes something doesn't require re-ingesting from OpenF1: rewind the consumer group's offsets and the processor reads the topic again with the new code. The upserts from [Lap times in Postgres](#lap-times-in-postgres) make this safe; reprocessed laps update their rows instead of adding new ones.

The example is `representative_lap`, a column added after laps were already stored: true for timed laps other than lap 1, which starts from the grid. Migration `V2__add_representative_lap.sql` adds it as nullable, so existing rows show `NULL` ("not computed yet") until they're reprocessed. The upsert sets it on conflict as well as on insert, which is what lets a replay fill it for existing rows.

Offsets can only be reset while the group has no active members, so stop the processor first:

    docker compose stop stream-processor
    kx kafka-consumer-groups --group stream-processor --topic f1.laps --reset-offsets --to-earliest --dry-run
    kx kafka-consumer-groups --group stream-processor --topic f1.laps --reset-offsets --to-earliest --execute
    docker compose start stream-processor
    kx kafka-consumer-groups --describe --group stream-processor   # LAG jumps to the topic size, then drains to 0

Afterwards every row has a value and the row count is unchanged:

    docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P null="(null)" \
      -c "SELECT representative_lap, count(*) FROM stream_processor.lap_times GROUP BY 1;"'

For session 9888 that's 1,025 representative laps and 42 that aren't, with no `(null)` row.

> [!NOTE]
> Only records still in the log can be replayed: in production that's bounded by the topic's retention, and locally by Kafka having no volume (`docker compose down` empties it). Reprocessing also replays failures: bad records still in `f1.laps` are dead-lettered again. The dead-letter topic itself isn't reread, because the processor only consumes `f1.laps`.

> [!NOTE]
> Always run `--dry-run` first. Resetting offsets changes shared state for every instance in the group, and `--to-offset` or `--to-datetime` can rewind to a specific point instead of the beginning.

---

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main`, every pull request, and every `v*` tag. Each stage starts only when the previous one passed, so an image is published only after its code passed the tests and deployed to a cluster.

| Job | What it does |
|---|---|
| Build and test (one per service) | `./gradlew build`: compiles, runs unit and Testcontainers integration tests, packages the jar |
| Smoke test on kind | Builds both images, deploys them to a new kind cluster with [`k8s/smoke-test.sh`](#smoke-test), and sends one lap through Kafka into Postgres |
| Image (one per service) | Builds the image for `linux/amd64` and `linux/arm64` and pushes it to GitHub Container Registry. Pull requests build without pushing |

### Published images

Both images are public, so pulling needs no login:

| Image | Tags |
|---|---|
| `ghcr.io/kcmodev/pit-wall/ingest-service` | `sha-<commit>` for every push to `main`, `<version>` (e.g. `0.4.0`) for each release |
| `ghcr.io/kcmodev/pit-wall/stream-processor` | Same |

```bash
docker pull ghcr.io/kcmodev/pit-wall/ingest-service:0.4.0
docker buildx imagetools inspect ghcr.io/kcmodev/pit-wall/ingest-service:0.4.0
```

The pull picks the image for your machine's architecture. `imagetools inspect` lists both platforms, plus two `unknown/unknown` entries: provenance attestations that record how each image was built (the Actions run, base image digests, commit). Clients skip them when pulling.

> [!NOTE]
> There's no `latest` tag. Every tag always refers to the same image, so a deploy or rollback names exactly what it runs.
> The local cluster doesn't use these images yet: [Deploy to the local cluster](#deploy-to-the-local-cluster) builds and loads its own.
