#!/usr/bin/env bash
# Smoke test: deploys k8s/base with the given image tag, waits until every workload is ready, then sends one
# lap through Kafka and checks that stream-processor stored it in Postgres. Proves the images start, find
# each other and their dependencies inside a real cluster, which unit and integration tests can't.
#
# Usage: k8s/smoke-test.sh <image-tag>
#   pit-wall/ingest-service:<tag> and pit-wall/stream-processor:<tag> must already be loaded into the cluster
#   (kind load docker-image ...). Runs against the current kubectl context: a fresh kind cluster in CI,
#   or your local one.
set -euo pipefail

tag=${1:?usage: $0 <image-tag>}
ns=pit-wall
cd "$(dirname "$0")/.."   # repo root

# Throwaway overlay: the base, with the tag under test. Kustomize only accepts a relative path to a base,
# so the overlay lives next to it and is removed on exit.
overlay=k8s/.smoke-overlay
trap 'rm -rf "$overlay"' EXIT
mkdir -p "$overlay"
cat > "$overlay/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../base
images:
  - name: pit-wall/ingest-service
    newTag: "$tag"
  - name: pit-wall/stream-processor
    newTag: "$tag"
EOF

echo "--- Deploying pit-wall/*:$tag"
kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f -
# CI's cluster is new and has no secret: generate throwaway credentials. A local cluster keeps its own,
# because Postgres only reads them when it first initializes its volume.
if ! kubectl get secret postgres-credentials -n "$ns" >/dev/null 2>&1; then
  kubectl create secret generic postgres-credentials -n "$ns" \
    --from-literal=POSTGRES_DB=pitwall \
    --from-literal=POSTGRES_USER=pitwall \
    --from-literal=POSTGRES_PASSWORD="$(openssl rand -hex 16)"
fi
kubectl apply -k "$overlay"

echo "--- Waiting for workloads to become ready"
# Rollout status waits for readiness probes, and both services include the database in readiness
for workload in statefulset/postgres statefulset/kafka deployment/ingest-service deployment/stream-processor; do
  kubectl rollout status -n "$ns" "$workload" --timeout=300s
done

echo "--- Sending one lap through f1.laps"
# A session key unique to this run, so a rerun against the same database can't pass on an old row
session=$(date +%s)
lap="{\"sessionKey\":$session,\"driverNumber\":44,\"lapNumber\":1,\"lapStartTime\":\"2023-09-17T12:03:44.658Z\",\"lapDurationSeconds\":91.234,\"pitOutLap\":false}"
# Same key format as ingest-service. f1.laps exists because ingest-service creates it on startup.
# --sync waits for each send, so a failed send fails the script here instead of being only logged.
echo "$session-44|$lap" | kubectl exec -i -n "$ns" kafka-0 -c kafka -- \
  /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic f1.laps --sync \
  --reader-property parse.key=true --reader-property key.separator='|'

echo "--- Waiting for stream-processor to store it"
for _ in $(seq 30); do
  count=$(kubectl exec -n "$ns" postgres-0 -c postgres -- sh -c \
    "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -tAc 'SELECT count(*) FROM stream_processor.lap_times WHERE session_key = $session'")
  if [ "$count" = 1 ]; then
    echo "OK: lap for session $session stored in stream_processor.lap_times"
    exit 0
  fi
  sleep 2
done
echo "FAILED: lap for session $session not stored within 60s" >&2
exit 1
