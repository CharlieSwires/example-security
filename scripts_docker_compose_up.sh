#!/usr/bin/env bash
set -euo pipefail

backend_replicas="${1:-1}"
frontend_replicas="${2:-1}"

[[ "$backend_replicas" =~ ^[1-9][0-9]*$ ]] || { echo "Backend replicas must be a positive integer." >&2; exit 2; }
[[ "$frontend_replicas" =~ ^[1-9][0-9]*$ ]] || { echo "Frontend replicas must be a positive integer." >&2; exit 2; }

if [[ "$backend_replicas" == "1" && "$frontend_replicas" == "1" ]]; then
    echo "Starting single-instance mode (no separate Nginx load-balancer)."
    docker compose -f docker-compose.yml up -d --build --remove-orphans
else
    echo "Starting scaled mode with the Nginx load-balancer."
    docker compose -f docker-compose.scaled.yml up -d --build --remove-orphans \
        --scale backend="$backend_replicas" \
        --scale frontend="$frontend_replicas"
fi
