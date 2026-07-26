#!/bin/bash
# End-to-end smoke check for the runtime docker-compose.yml (spec issue
# #32): brings the whole 5-service stack up for real, then proves the
# backend, Kafka, Postgres, Redis, and the simulator actually work
# together — not just that each container starts.
#
# Requires: docker compose, curl. Deliberately avoids jq/uuidgen so it
# runs on the widest range of dev machines with no extra tools to install.
#
# Usage: ./scripts/smoke-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

# Shorten the retry ladder just for this run so the DEAD/DLQ scenario
# doesn't take the full production ~2.5 minutes (docs/adr/0004's real
# 10s/30s/2m delays) — these override docker-compose.yml's defaults.
export RETRY_DELAY_BAND_1_MS=500
export RETRY_DELAY_BAND_2_MS=500
export RETRY_DELAY_BAND_3_MS=500
export DELIVERY_HTTP_TIMEOUT_MS=2000

BACKEND_PORT="${BACKEND_PORT:-8080}"
BASE_URL="http://localhost:${BACKEND_PORT}/v1"

cleanup() {
  echo "--- Tearing down ---"
  docker compose down -v
}
trap cleanup EXIT

# Extracts "field":"value" from a flat-ish JSON blob without jq — good
# enough for this script's own predictable response shapes. "|| true": a
# genuinely empty result (e.g. no delivery yet) makes grep -o exit 1 with
# no match, which — under set -e -o pipefail — would otherwise silently
# kill the whole script the moment a poll finds nothing yet.
json_field() {
  local field="$1" json="$2"
  echo "$json" | grep -o "\"${field}\":\"[^\"]*\"" | head -1 | sed -E "s/\"${field}\":\"([^\"]*)\"/\\1/" || true
}

# A unique-enough string for Idempotency-Key — doesn't need to be a real
# UUID, the header is just an opaque client-supplied string.
unique_id() {
  echo "smoke-$(date +%s%N)-${RANDOM}"
}

echo "--- Bringing up the stack ---"
docker compose up -d --build

echo "--- Waiting for the backend to report healthy ---"
for _ in $(seq 1 60); do
  if curl -sf "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
    echo "Backend is healthy."
    break
  fi
  sleep 2
done
if ! curl -sf "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "FAIL: backend never became healthy" >&2
  docker compose logs backend
  exit 1
fi

register_endpoint() {
  local response
  response=$(curl -sf -X POST "${BASE_URL}/endpoints" \
    -H "Content-Type: application/json" \
    -d "{\"url\":\"$1\"}")
  json_field "id" "$response"
}

ingest_event() {
  local response
  response=$(curl -sf -X POST "${BASE_URL}/events" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(unique_id)" \
    -H "X-Producer-Id: smoke-test" \
    -d "{\"eventType\":\"order.created\",\"payload\":{\"orderId\":\"$1\"}}")
  json_field "id" "$response"
}

# Filters by endpointId (not the event's own delivery list) so a second
# scenario's fan-out to a second endpoint can't be confused with the
# first scenario's delivery — each scenario's endpoint is used exactly
# once, so the first matching delivery is always the right one.
wait_for_delivery_state() {
  local endpoint_id="$1"
  local expected_state="$2"
  local attempts="$3"
  local state=""
  for _ in $(seq 1 "$attempts"); do
    local response
    response=$(curl -sf "${BASE_URL}/deliveries?endpointId=${endpoint_id}")
    state=$(json_field "state" "$response")
    if [ "$state" = "$expected_state" ]; then
      return 0
    fi
    sleep 1
  done
  echo "FAIL: delivery for endpoint ${endpoint_id} never reached ${expected_state} (last seen: ${state:-none})" >&2
  return 1
}

echo "--- Scenario 1: successful delivery ---"
success_endpoint_id=$(register_endpoint "http://simulator:4000/simulate/success")
ingest_event "success" >/dev/null
# Generous budget: the very first outbox-poll tick after a cold start pays
# a one-time Kafka producer/metadata-negotiation cost (a few seconds) on
# top of the 1s poll interval.
wait_for_delivery_state "$success_endpoint_id" "DELIVERED" 60
echo "PASS: delivery reached DELIVERED (endpoint ${success_endpoint_id})"

echo "--- Scenario 2: exhausted retries -> DEAD (DLQ) ---"
dead_endpoint_id=$(register_endpoint "http://simulator:4000/simulate/error-500")
ingest_event "dead" >/dev/null
wait_for_delivery_state "$dead_endpoint_id" "DEAD" 60
echo "PASS: delivery reached DEAD (endpoint ${dead_endpoint_id})"

echo "--- All smoke checks passed ---"
