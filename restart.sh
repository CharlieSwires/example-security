#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.production.yml}"

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

echo "Starting the production stack from the existing images..."
docker compose -f "$COMPOSE_FILE" up -d --force-recreate --remove-orphans

echo
echo "Deployment status:"
docker compose -f "$COMPOSE_FILE" ps

