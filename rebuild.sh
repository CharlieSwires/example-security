#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.production.yml}"
readonly BACKEND_IMAGE="${BACKEND_IMAGE:-example-security-backend:krystal}"
readonly FRONTEND_IMAGE="${FRONTEND_IMAGE:-example-security-frontend:krystal}"
readonly VITE_API_BASE="${VITE_API_BASE:-/ExampleSecurity}"

# Default to this VM's short hostname, while still allowing an explicit override.
export MONITORING_INSTANCE_ID="${MONITORING_INSTANCE_ID:-$(hostname -s)}"

cd "$SCRIPT_DIR"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: Compose file not found: $SCRIPT_DIR/$COMPOSE_FILE" >&2
  exit 1
fi

command -v docker >/dev/null 2>&1 || {
  echo "ERROR: Docker is not installed or is not on PATH." >&2
  exit 1
}

docker compose version >/dev/null

if [[ -z "$MONITORING_INSTANCE_ID" ]]; then
  echo "ERROR: Could not determine MONITORING_INSTANCE_ID from the hostname." >&2
  exit 1
fi

echo "Monitoring instance ID: $MONITORING_INSTANCE_ID"

# Force the hostname-derived value into the backend even when backend.env
# contains an older MONITORING_INSTANCE_ID value.
COMPOSE_OVERRIDE="$(mktemp)"
trap 'rm -f "$COMPOSE_OVERRIDE"' EXIT
printf '%s\n' \
  'services:' \
  '  backend:' \
  '    environment:' \
  '      MONITORING_INSTANCE_ID: "${MONITORING_INSTANCE_ID}"' \
  > "$COMPOSE_OVERRIDE"

COMPOSE=(docker compose -f "$COMPOSE_FILE" -f "$COMPOSE_OVERRIDE")

echo "Stopping and removing the production containers and network..."
"${COMPOSE[@]}" down --remove-orphans

echo "Building backend image from scratch: $BACKEND_IMAGE"
docker build --pull --no-cache \
  --tag "$BACKEND_IMAGE" \
  "$SCRIPT_DIR/backend"

echo "Building frontend image from scratch: $FRONTEND_IMAGE"
docker build --pull --no-cache \
  --build-arg "VITE_API_BASE=$VITE_API_BASE" \
  --tag "$FRONTEND_IMAGE" \
  --file "$SCRIPT_DIR/frontend/Dockerfile.production" \
  "$SCRIPT_DIR/frontend"

echo "Deploying the rebuilt production stack..."
"${COMPOSE[@]}" up -d --force-recreate --remove-orphans

echo
echo "Deployment status:"
"${COMPOSE[@]}" ps

