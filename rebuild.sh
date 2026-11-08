#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.production.yml}"
readonly BACKEND_IMAGE="${BACKEND_IMAGE:-example-security-backend:krystal}"
readonly FRONTEND_IMAGE="${FRONTEND_IMAGE:-example-security-frontend:krystal}"
readonly VITE_API_BASE="${VITE_API_BASE:-/ExampleSecurity}"

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

echo "Stopping and removing the production containers and network..."
docker compose -f "$COMPOSE_FILE" down --remove-orphans

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
docker compose -f "$COMPOSE_FILE" up -d --force-recreate --remove-orphans

echo
echo "Deployment status:"
docker compose -f "$COMPOSE_FILE" ps

