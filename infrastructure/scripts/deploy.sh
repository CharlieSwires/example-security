#!/usr/bin/env bash
set -euo pipefail
APP_DIR=${APP_DIR:-/opt/example-security}
ENV_FILE=${EXAMPLE_SECURITY_ENV_FILE:-/etc/example-security/backend.env}
[[ -f "$ENV_FILE" ]] || { echo "Missing $ENV_FILE" >&2; exit 2; }
chmod 600 "$ENV_FILE"
cd "$APP_DIR"
EXAMPLE_SECURITY_ENV_FILE="$ENV_FILE" docker compose -f docker-compose.production.yml build --pull
docker compose -f docker-compose.production.yml up -d --remove-orphans
for i in {1..30}; do
  if curl -fsS http://127.0.0.1/health >/dev/null; then echo "Deployment healthy"; exit 0; fi
  sleep 5
done
echo "Health check failed" >&2
docker compose -f docker-compose.production.yml ps >&2
exit 1
