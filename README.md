# pit-wall

**Pit Wall** is an event-driven telemetry platform for Formula 1 data that I'm building in stages. The target architecture: an ingest service replays historical session data from the OpenF1 API into Kafka, and a stream processor turns raw car and timing events into race metrics. The results will be stored in Postgres, served through a REST API and visualized in Grafana dashboards. The system will run on Kubernetes, with GitHub Actions handling builds, tests and deployments. I'm building it to get hands-on experience with containers, event streaming and orchestration, using real-world data I genuinely enjoy working with.

## Status

- [x] **Docker:** ingest-service containerized, running with PostgreSQL via Docker Compose
- [x] **Kubernetes:** running on a local kind cluster, deployed with Kustomize
- [ ] **Kafka:** ingest-service replays OpenF1 session data into Kafka
- [ ] Stream processing, REST API, Grafana dashboards, CI/CD with GitHub Actions

## Contents

- [Running locally (Docker Compose)](#running-locally)
- [Local Kubernetes cluster (kind)](#local-kubernetes-cluster-kind)
- [Deploy to the local cluster](#deploy-to-the-local-cluster)

---

## Running locally

Runs ingest-service (Spring Boot) and PostgreSQL 18 with Docker Compose. The ingest service doesn't pull F1 data yet. For now, a healthy stack means the app is up, connected to Postgres, and has applied its database migrations.

### Prerequisites

| Tool                    | Minimum version | Check with               |
|-------------------------|-----------------|--------------------------|
| Git                     | any             | `git --version`          |
| Docker Engine           | 25.0            | `docker version`         |
| Docker Compose (plugin) | 2.20.2          | `docker compose version` |

> [!NOTE]
> A current Docker Desktop covers both Docker requirements. The version floors come from the `start_interval` healthcheck option in `compose.yaml`, which older versions don't support.
> You don't need a local JDK or Gradle. The app is compiled inside Docker.

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
> `--wait` runs the stack in the background and returns once both containers are healthy, or fails if either one doesn't get there. The first run builds the image and initializes the database, so expect a few minutes. Flyway applies the migrations on startup.
> If it fails with `port is already allocated`, another process is using `8080` or `5433`. Stop it, or change the host side of the mapping in `compose.yaml` (e.g. `"127.0.0.1:8081:8080"`).
> If ingest-service keeps restarting, check `docker compose logs ingest-service`. `password authentication failed` means `.env` changed after the database was initialized: run `docker compose down -v` (**deletes DB data**) and start again. `UnknownHostException: postgres` means the Postgres container isn't running: run `docker compose up --wait`.

### 3. Verify

```bash
docker compose ps
curl -s http://127.0.0.1:8080/actuator/health
```

Both services should show `Up ... (healthy)`. In the health response, expect `"status":"UP"` at the top level and `"db":{...,"status":"UP"}` inside `components`, which confirms the app can query Postgres.

To confirm the migrations ran (expect `sessions` and `flyway_schema_history`):

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt"'
```

| Service          | Host address     | Container port |
|------------------|------------------|----------------|
| `ingest-service` | `127.0.0.1:8080` | `8080`         |
| `postgres`       | `127.0.0.1:5433` | `5432`         |

> [!NOTE]
> Postgres is published on 5433 to avoid clashing with a local Postgres on 5432. Point DB tools at `127.0.0.1:5433` with the credentials from `.env`.
> Both ports bind to `127.0.0.1`, so only your machine can reach them. Without a host IP, Docker publishes on every network interface.

### Stop and reset

| Command                  | Containers    | Database data |
|--------------------------|---------------|---------------|
| `docker compose stop`    | Stopped, kept | Kept          |
| `docker compose down`    | Removed       | Kept          |
| `docker compose down -v` | Removed       | **Deleted**   |

To start again after `stop` or `down`, run `docker compose up --wait`. After code changes, run `docker compose up --build --wait`. Neither touches your data.

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

Deploys ingest-service and PostgreSQL 18 into the `pit-wall` namespace, using the Kustomize manifests in `k8s/base`. Requires the kind cluster from the previous section.

### 1. Build and load the image

From the repo root:

```bash
docker build -t pit-wall/ingest-service:0.2.0 services/ingest-service
kind load docker-image pit-wall/ingest-service:0.2.0 --name pit-wall
```

> [!NOTE]
> There's no registry, so `kind load` copies the image into each node, and the Deployment uses `imagePullPolicy: Never`.
> Image tags follow the app version in `services/ingest-service/build.gradle.kts`, and must match `newTag` in `k8s/base/kustomization.yaml`. If the version is no longer `0.2.0`, use the current one in both commands. After code changes, bump the version, build with the new tag and update `newTag`. Reusing a tag keeps the old image running.

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
kubectl rollout status -n pit-wall deployment/ingest-service --timeout=180s
kubectl get pods -n pit-wall
```

Both pods should show `1/1` under `READY`. Then check the health endpoint:

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
> ingest-service may restart or show `0/1` until Postgres is ready. Flyway needs the database at startup, and the database is part of its readiness check.
> If `port-forward` fails with "address already in use", stop whatever else is on port 8080 (e.g. `docker compose down`).

### Remove

```bash
kubectl delete -k k8s/base
```

> [!WARNING]
> Deletes the `pit-wall` namespace and everything in it, **including the database volume and the secret**. To redeploy, start again from step 2.

---

## 4. Kafka (local)

Pit Wall uses Apache Kafka (KRaft mode, single combined broker/controller) as the event backbone between ingest and processing. It starts with the rest of the stack:

    docker compose up -d
    docker compose ps   # kafka should report "healthy"

| Client location | Bootstrap address |
|---|---|
| Other Compose services | `kafka:29092` |
| Host (IDE, local tools) | `127.0.0.1:9092` |

> [!NOTE]
> Automatic topic creation is disabled. Topics must be created explicitly, as they would be in production.

### Inspecting Kafka with the CLI

The Kafka CLI tools are included in the broker image, so no local install is needed:

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
> This is a single-node development setup: replication factor 1, no authentication, and combined broker/controller roles. Production Kafka runs at least three brokers with replication, TLS and authentication, separate controllers, and is typically a managed service.

> [!WARNING]
> The broker port is bound to `127.0.0.1` only. Do not expose it on other interfaces; the PLAINTEXT listeners have no authentication.