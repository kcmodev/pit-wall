# pit-wall

**Pit Wall** is an event-driven telemetry platform for Formula 1 data that I'm building in stages. The target architecture: an ingest service replays historical session data from the OpenF1 API into Kafka, and a stream processor turns raw car and timing events into race metrics. The results will be stored in Postgres, served through a REST API, and visualized in Grafana dashboards. The system will run on Kubernetes, with GitHub Actions handling builds, tests, and deployments. I'm building it to get hands-on experience with containers, event streaming, and orchestration, using real-world data I genuinely enjoy working with.

## Status

- [x] **Docker:** ingest-service and stream-processor containerized, running with PostgreSQL and Kafka via Docker Compose
- [x] **Kubernetes:** running on a local kind cluster, deployed with Kustomize
- [x] **Kafka:** ingest-service replays OpenF1 lap data into Kafka on request; stream-processor consumes it
- [ ] Stream processing, REST API, Grafana dashboards, CI/CD with GitHub Actions

## Contents

- [Running locally (Docker Compose)](#running-locally)
- [Local Kubernetes cluster (kind)](#local-kubernetes-cluster-kind)
- [Deploy to the local cluster](#deploy-to-the-local-cluster)
- [Kafka (local)](#kafka-local)
- [Replay a session](#replay-a-session)

---

## Running locally

Runs ingest-service (Spring Boot), PostgreSQL 18, and Kafka with Docker Compose. A healthy stack means the app is up, connected to Postgres, and has applied its database migrations. To pull F1 data into Kafka, [replay a session](#replay-a-session).

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
> `--wait` runs the stack in the background and returns once every container is healthy, or fails if one doesn't get there. The first run builds the image and initializes the database, so expect a few minutes. Flyway applies the migrations on startup, and ingest-service creates the `f1.laps` topic.
> If it fails with `port is already allocated`, another process is using `8080`, `5433` or `9092`. Stop it, or change the host side of the mapping in `compose.yaml` (e.g. `"127.0.0.1:8081:8080"`).
> If ingest-service keeps restarting, check `docker compose logs ingest-service`. `password authentication failed` means `.env` changed after the database was initialized: run `docker compose down -v` (**deletes DB data**) and start again. `UnknownHostException: postgres` means the Postgres container isn't running: run `docker compose up --wait`.

### 3. Verify

```bash
docker compose ps
curl -s http://127.0.0.1:8080/actuator/health
```

All three services should show `Up ... (healthy)`. In the health response, expect `"status":"UP"` at the top level and `"db":{...,"status":"UP"}` inside `components`, which confirms the app can query Postgres.

To confirm the migrations ran (expect `sessions` and `flyway_schema_history`):

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
```

| Service          | Host address     | Container port |
|------------------|------------------|----------------|
| `ingest-service` | `127.0.0.1:8080` | `8080`         |
| `postgres`       | `127.0.0.1:5433` | `5432`         |
| `kafka`          | `127.0.0.1:9092` | `9092`         |

> [!NOTE]
> Postgres is published on 5433 to avoid clashing with a local Postgres on 5432. Point DB tools at `127.0.0.1:5433` with the credentials from `.env`.
> All ports bind to `127.0.0.1`, so only your machine can reach them. Without a host IP, Docker publishes on every network interface.

### Develop

The app is compiled into the ingest-service image, so a plain `docker compose up` keeps running the previous build after code changes. There are two ways to pick up changes without rebuilding by hand.

**Rebuild on save with `watch`:**

```bash
docker compose watch
```

Starts the stack, then rebuilds, and restarts ingest-service whenever a file under `services/ingest-service/src`, `build.gradle.kts` or `settings.gradle.kts` changes. Postgres and Kafka keep running, and Gradle's cache keeps rebuilds to roughly the compile time. It runs in the foreground; stop watching with Ctrl-C, and use `docker compose ps` to see what's still running.

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
docker build -t pit-wall/ingest-service:0.3.0 services/ingest-service
docker build -t pit-wall/stream-processor:0.3.0 services/stream-processor
kind load docker-image pit-wall/ingest-service:0.3.0 pit-wall/stream-processor:0.3.0 --name pit-wall
```

> [!NOTE]
> There's no registry, so `kind load` copies the image into each node, and the Deployment uses `imagePullPolicy: Never`.
> Image tags follow each app's version in its `build.gradle.kts`, and must match its `newTag` in `k8s/base/kustomization.yaml`. If the version is no longer `0.3.0`, use the current one in both commands. After code changes, bump the version, build with the new tag, and update `newTag`. Reusing a tag keeps the old image running.

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

### 3.5 Stream processor

`services/stream-processor` consumes `f1.laps` as the consumer group `stream-processor` and (for now) logs each `LapEvent`. It deserializes into its own `LapEvent` record rather than sharing ingest's class: the JSON on the topic is the contract, so the two services build and deploy independently.

Run two instances to see partitions split across a group:

    ./gradlew bootJar
    java -jar build/libs/stream-processor.jar
    java -jar build/libs/stream-processor.jar --server.port=8082
    kx kafka-consumer-groups --describe --group stream-processor

> [!NOTE]
> Parallelism is capped by partition count: `f1.laps` has 3 partitions, so a 4th instance would sit idle. `auto.offset.reset=earliest` only applies when the group has no committed offset; a restarted instance resumes from the group's committed offsets.

> [!WARNING]
> At this stage, delivery is at-least-once and there is no dead-letter handling; a malformed record blocks its partition. Duplicates are handled in 3.6a and bad records in 3.6b.

---

### 3.6a Lap times in Postgres

The stream processor writes one row per lap to `stream_processor.lap_times`, keyed by the natural key `(session_key, driver_number, lap_number)`. Durations are stored as integer milliseconds; `timed_lap` marks laps with a time that aren't pit-out laps.

Kafka delivery is at-least-once, and replaying a session publishes the same laps again, so the processor must tolerate duplicates. Writes are upserts (`INSERT ... ON CONFLICT ... DO UPDATE`): processing the same event twice leaves the same row. Replaying a session twice leaves the row count unchanged and only advances `processed_at`.

    docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
      -c "SELECT count(*), max(processed_at) FROM stream_processor.lap_times;"'

> [!NOTE]
> The schema is managed by Flyway (`src/main/resources/db/migration`) and applied when the processor starts. The processor owns the `stream_processor` Postgres schema, with its own Flyway history, so its migration versions can't collide with ingest-service's in `public`. Applied migrations are never edited; changes go in a new versioned file.

> [!WARNING]
> At this stage, records that keep failing are retried briefly, then logged and skipped. Dead-letter handling is added in 3.6b.

---

### 3.6b Error handling and dead-letter topic

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

> [!NOTE]
> Retries are blocking: while a record retries, the rest of its partition waits. This preserves per-driver lap order. Non-blocking retry topics would keep the partition moving but give up that ordering.

> [!WARNING]
> Nothing consumes the dead-letter topic yet. In production it would be monitored and alerted on, with tooling to inspect and replay fixed records.

---

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

To confirm the migrations ran (expect `sessions` and `flyway_schema_history`):

```bash
kubectl exec -n pit-wall postgres-0 -- sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
```

> [!NOTE]
> stream-processor's `f1.laps` topic is created by ingest-service on startup; until then the processor logs `UNKNOWN_TOPIC_OR_PARTITION` warnings and picks the topic up once it exists.
> ingest-service may restart or show `0/1` until Postgres is ready. Flyway needs the database at startup, and the database is part of its readiness check.
> If `port-forward` fails with "address already in use", stop whatever else is on port 8080 (e.g. `docker compose down`).

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
  --topic f1.laps --from-beginning --property print.key=true --max-messages 5
```

> [!NOTE]
> If Kafka is down, the request can take up to 2 minutes before returning `503`. That's the producer's `delivery.timeout.ms`, after which each send fails.

> [!WARNING]
> Replaying a session again, including retrying after a `503`, publishes every lap again. The topic will then contain duplicates.