#!/usr/bin/env bash
# Deploy ExampleSecurity to a fresh Krystal Ubuntu 24.04 VPS.
#
# First run (creates a root-only environment template, then stops):
#   sudo bash deploy_krystal.sh \
#     --domain app.example.co.uk \
#     --email admin@example.co.uk \
#     --ssh-cidr 203.0.113.10/32
#
# Edit /root/example-security-production.env, replace the SMTP placeholders,
# then run the same command again.

set -Eeuo pipefail
IFS=$'\n\t'

readonly SCRIPT_NAME="$(basename "$0")"

DOMAIN="${DOMAIN:-}"
LETSENCRYPT_EMAIL="${LETSENCRYPT_EMAIL:-}"
SSH_ALLOWED_CIDR="${SSH_ALLOWED_CIDR:-}"
SSH_PORT="${SSH_PORT:-22}"
REPO_URL="${REPO_URL:-https://github.com/CharlieSwires/example-security.git}"
BRANCH="${BRANCH:-master}"
APP_DIR="${APP_DIR:-/opt/example-security}"
ENV_FILE="${ENV_FILE:-/root/example-security-production.env}"
BACKEND_REPLICAS="${BACKEND_REPLICAS:-1}"
FRONTEND_REPLICAS="${FRONTEND_REPLICAS:-1}"
SKIP_DNS_CHECK="${SKIP_DNS_CHECK:-false}"

usage() {
    cat <<USAGE
Usage:
  sudo bash ${SCRIPT_NAME} --domain HOST --email ADDRESS [options]

Required:
  --domain HOST          Public DNS name, e.g. app.example.co.uk
  --email ADDRESS        Email address used by Let's Encrypt

Recommended:
  --ssh-cidr CIDR        IP/CIDR allowed to SSH, e.g. 203.0.113.10/32

Options:
  --ssh-port PORT        SSH port (default: 22)
  --repo URL             Git repository
  --branch NAME          Git branch (default: master)
  --app-dir PATH         Checkout path (default: /opt/example-security)
  --env-file PATH        Root-only secrets file
  --backend-replicas N   Backend containers (default: 1)
  --frontend-replicas N  Frontend containers (default: 1)
  --skip-dns-check       Let Certbot perform the only DNS validation
  -h, --help             Show this help

The script is designed for a fresh Ubuntu 24.04 Krystal VPS. It is safe to
rerun for application updates: Git uses a fast-forward-only update and the
production environment file is preserved. MongoDB Atlas and SMTP stay external.
USAGE
}

log() {
    printf '\n[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

on_error() {
    local exit_code=$?
    printf 'ERROR: deployment stopped at line %s (exit %s).\n' "${BASH_LINENO[0]}" "$exit_code" >&2
    exit "$exit_code"
}
trap on_error ERR

while (($#)); do
    case "$1" in
        --domain) DOMAIN="${2:?Missing value after --domain}"; shift 2 ;;
        --email) LETSENCRYPT_EMAIL="${2:?Missing value after --email}"; shift 2 ;;
        --ssh-cidr) SSH_ALLOWED_CIDR="${2:?Missing value after --ssh-cidr}"; shift 2 ;;
        --ssh-port) SSH_PORT="${2:?Missing value after --ssh-port}"; shift 2 ;;
        --repo) REPO_URL="${2:?Missing value after --repo}"; shift 2 ;;
        --branch) BRANCH="${2:?Missing value after --branch}"; shift 2 ;;
        --app-dir) APP_DIR="${2:?Missing value after --app-dir}"; shift 2 ;;
        --env-file) ENV_FILE="${2:?Missing value after --env-file}"; shift 2 ;;
        --backend-replicas) BACKEND_REPLICAS="${2:?Missing value after --backend-replicas}"; shift 2 ;;
        --frontend-replicas) FRONTEND_REPLICAS="${2:?Missing value after --frontend-replicas}"; shift 2 ;;
        --skip-dns-check) SKIP_DNS_CHECK=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) die "Unknown argument: $1" ;;
    esac
done

[[ $EUID -eq 0 ]] || die "Run this script with sudo."
[[ -n "$DOMAIN" ]] || die "--domain is required."
[[ -n "$LETSENCRYPT_EMAIL" ]] || die "--email is required."
[[ "$DOMAIN" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?$ ]] || die "Invalid domain name: $DOMAIN"
[[ "$LETSENCRYPT_EMAIL" == *@*.* ]] || die "Invalid email address: $LETSENCRYPT_EMAIL"
[[ "$SSH_PORT" =~ ^[0-9]+$ ]] && ((SSH_PORT >= 1 && SSH_PORT <= 65535)) || die "Invalid SSH port."
[[ "$BACKEND_REPLICAS" =~ ^[1-9][0-9]*$ ]] || die "Backend replicas must be a positive integer."
[[ "$FRONTEND_REPLICAS" =~ ^[1-9][0-9]*$ ]] || die "Frontend replicas must be a positive integer."

if [[ -r /etc/os-release ]]; then
    # shellcheck disable=SC1091
    source /etc/os-release
else
    die "Cannot identify the operating system."
fi
[[ "${ID:-}" == "ubuntu" ]] || die "This script supports Ubuntu only."
[[ "${VERSION_ID:-}" == "24.04" ]] || die "Ubuntu 24.04 is required; found ${VERSION_ID:-unknown}."
[[ "$(dpkg --print-architecture)" == "amd64" || "$(dpkg --print-architecture)" == "arm64" ]] \
    || die "Only amd64 and arm64 are supported."

create_environment_template() {
    local env_parent initial_super_password
    local crypto_passphrase crypto_salt word

    env_parent="$(dirname "$ENV_FILE")"
    install -d -m 0700 "$env_parent"
    umask 077

    initial_super_password="$(openssl rand -base64 36 | tr -d '\n')"
    crypto_salt="$(openssl rand -base64 32 | tr -d '\n')"
    crypto_passphrase=""
    for _ in $(seq 1 14); do
        word="$(openssl rand -hex 4)"
        crypto_passphrase+="${crypto_passphrase:+ }${word}"
    done

    {
        printf '%s\n' '# Root-only production settings for ExampleSecurity.'
        printf '%s\n' '# Keep this file out of the Git repository and all backups unless separately encrypted.'
        printf 'MONGODB_URI=CHANGE_ME_ATLAS_SRV_URI\n'
        printf 'REQUIRE_EXTERNAL_SERVICES=true\n'
        printf '\nINITIAL_SUPER_USERNAME=super\n'
        printf 'INITIAL_SUPER_PASSWORD=%s\n' "$initial_super_password"
        printf '\nFIELD_CRYPTO_ENABLED=true\n'
        printf 'FIELD_CRYPTO_PASSPHRASE="%s"\n' "$crypto_passphrase"
        printf 'FIELD_CRYPTO_MASTER_SALT_B64=%s\n' "$crypto_salt"
        printf '\nSESSION_COOKIE_SECURE=true\n'
        printf 'SESSION_COOKIE_SAME_SITE=lax\n'
        printf 'SESSION_COLLECTION_NAME=spring_sessions\n'
        printf 'SESSION_TIMEOUT=30m\n'
        printf '\nLOGIN_MAX_USER_IP_FAILURES=5\n'
        printf 'LOGIN_MAX_IP_FAILURES=25\n'
        printf 'LOGIN_FAILURE_WINDOW_MINUTES=15\n'
        printf 'LOGIN_LOCKOUT_MINUTES=15\n'
        printf 'PASSWORD_RESET_MAX_ACCOUNT_REQUESTS=3\n'
        printf 'PASSWORD_RESET_MAX_IP_REQUESTS=20\n'
        printf 'PASSWORD_RESET_WINDOW_MINUTES=60\n'
        printf 'PASSWORD_RESET_LOCKOUT_MINUTES=60\n'
        printf 'SECURITY_AUDIT_PERSIST=true\n'
        printf 'SECURITY_DEBUG_REQUEST_LOGGING=false\n'
        printf 'MAX_REQUEST_BYTES=262144\n'
        printf '\nMAIL_HOST=CHANGE_ME\n'
        printf 'MAIL_PORT=587\n'
        printf 'MAIL_USERNAME=CHANGE_ME\n'
        printf 'MAIL_PASSWORD=CHANGE_ME\n'
        printf 'MAIL_FROM=CHANGE_ME\n'
        printf 'MAIL_SMTP_AUTH=true\n'
        printf 'MAIL_SMTP_STARTTLS=true\n'
        printf 'MAIL_DEBUG=false\n'
    } > "$ENV_FILE"
    chmod 0600 "$ENV_FILE"
}

environment_value() {
    local key="$1"
    sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1
}

validate_environment() {
    local key value
    local required=(
        MONGODB_URI
        INITIAL_SUPER_USERNAME INITIAL_SUPER_PASSWORD
        FIELD_CRYPTO_PASSPHRASE FIELD_CRYPTO_MASTER_SALT_B64
        MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD MAIL_FROM
    )

    [[ -f "$ENV_FILE" ]] || return 1
    [[ "$(stat -c '%a' "$ENV_FILE")" == "600" ]] || chmod 0600 "$ENV_FILE"

    for key in "${required[@]}"; do
        value="$(environment_value "$key")"
        [[ -n "$value" ]] || die "$key is missing from $ENV_FILE"
        [[ "$value" != *CHANGE_ME* ]] || die "Replace $key in $ENV_FILE before deployment."
    done

    [[ "$(environment_value SECURITY_AUDIT_PERSIST)" == "true" ]] \
        || die "SECURITY_AUDIT_PERSIST must be true."
    [[ "$(environment_value MONGODB_URI)" == mongodb+srv://* ]] \
        || die "MONGODB_URI must be a MongoDB Atlas mongodb+srv:// connection string."
    [[ "$(environment_value MAIL_HOST)" != "mailpit" && "$(environment_value MAIL_HOST)" != "localhost" ]] \
        || die "MAIL_HOST must identify an external SMTP service."
}

log "Installing operating-system packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y ca-certificates curl git gnupg nginx ufw certbot openssl ipcalc

if [[ ! -f "$ENV_FILE" ]]; then
    log "Creating the production environment template"
    create_environment_template
    printf '\nCreated %s with generated SUPER and encryption secrets.\n' "$ENV_FILE"
    printf 'Add the Atlas URI and external SMTP values, then run this command again.\n'
    printf 'The file is readable by root only and must never be committed to Git.\n'
    exit 2
fi
validate_environment

if [[ "$SKIP_DNS_CHECK" != "true" ]]; then
    log "Checking public DNS"
    resolved_ip="$(getent ahostsv4 "$DOMAIN" | awk 'NR == 1 { print $1 }')"
    [[ -n "$resolved_ip" ]] || die "$DOMAIN does not currently resolve to an IPv4 address. Add its DNS A record first."
    route_ip="$(ip -4 route get 1.1.1.1 | awk '{ for (i=1; i<=NF; i++) if ($i == "src") { print $(i+1); exit } }')"
    if [[ -n "$route_ip" && "$resolved_ip" != "$route_ip" ]]; then
        die "$DOMAIN resolves to $resolved_ip but this VPS route address is $route_ip. Correct DNS or use --skip-dns-check if the address is intentionally proxied."
    fi
fi

install_docker() {
    log "Installing Docker Engine from Docker's Ubuntu repository"
    apt-get remove -y docker.io docker-compose docker-compose-v2 docker-doc docker-buildx podman-docker containerd runc 2>/dev/null || true
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    cat > /etc/apt/sources.list.d/docker.sources <<DOCKER_REPOSITORY
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
DOCKER_REPOSITORY
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
}

if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
    install_docker
fi
systemctl enable --now docker containerd

log "Configuring the firewall"
ufw default deny incoming
ufw default allow outgoing
if [[ -n "$SSH_ALLOWED_CIDR" ]]; then
    ufw allow from "$SSH_ALLOWED_CIDR" to any port "$SSH_PORT" proto tcp comment 'Restricted SSH'
else
    printf 'WARNING: --ssh-cidr was omitted; SSH port %s will be open globally.\n' "$SSH_PORT" >&2
    ufw allow "$SSH_PORT/tcp" comment 'SSH'
fi
ufw allow 80/tcp comment 'HTTP and ACME'
ufw allow 443/tcp comment 'HTTPS'
ufw --force enable

log "Checking out the application"
if [[ ! -e "$APP_DIR" ]]; then
    git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$APP_DIR"
elif [[ -d "$APP_DIR/.git" ]]; then
    git -C "$APP_DIR" fetch origin "$BRANCH"
    git -C "$APP_DIR" checkout "$BRANCH"
    git -C "$APP_DIR" merge --ff-only "origin/$BRANCH"
else
    die "$APP_DIR exists but is not a Git checkout. Move it aside or choose another --app-dir."
fi

readonly DEPLOY_DIR="/etc/example-security"
readonly BACKEND_ENV="$DEPLOY_DIR/backend.env"
readonly COMPOSE_FILE="$DEPLOY_DIR/docker-compose.yml"
readonly GATEWAY_CONFIG="$DEPLOY_DIR/gateway.conf"
readonly HOST_NGINX_AVAILABLE="/etc/nginx/sites-available/example-security.conf"
readonly HOST_NGINX_ENABLED="/etc/nginx/sites-enabled/example-security.conf"
readonly ACME_WEBROOT="/var/www/letsencrypt"

install -d -m 0700 "$DEPLOY_DIR"
install -d -m 0755 "$ACME_WEBROOT/.well-known/acme-challenge"

cp "$ENV_FILE" "$BACKEND_ENV"
chmod 0600 "$BACKEND_ENV"

cat > "$GATEWAY_CONFIG" <<'GATEWAY_NGINX'
worker_processes auto;

events {}

http {
    resolver 127.0.0.11 valid=10s ipv6=off;

    upstream frontend_pool {
        zone frontend_pool 64k;
        server frontend:80 resolve;
    }

    upstream backend_pool {
        zone backend_pool 64k;
        server backend:8080 resolve;
    }

    server {
        listen 80;
        server_name _;
        client_max_body_size 256k;

        location /ExampleSecurity/ {
            proxy_pass http://backend_pool;
            proxy_http_version 1.1;
            proxy_set_header Host $http_host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
        }

        location / {
            proxy_pass http://frontend_pool;
            proxy_http_version 1.1;
            proxy_set_header Host $http_host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
        }
    }
}
GATEWAY_NGINX

cat > "$COMPOSE_FILE" <<COMPOSE_YAML
services:
  backend:
    build:
      context: $APP_DIR/backend
      dockerfile: Dockerfile
    restart: unless-stopped
    env_file:
      - $BACKEND_ENV
    environment:
      SSL_ENABLED: "false"
      SESSION_COOKIE_SECURE: "true"
      SESSION_COOKIE_SAME_SITE: "lax"
      CORS_ALLOWED_ORIGINS: "https://$DOMAIN"
      FRONTEND_BASE_URL: "https://$DOMAIN"
      BACKEND_BASE_URL: "https://$DOMAIN/ExampleSecurity"
      SECURITY_AUDIT_PERSIST: "true"
      SECURITY_DEBUG_REQUEST_LOGGING: "false"
      REQUIRE_EXTERNAL_SERVICES: "true"
    networks:
      - app
    logging:
      driver: local

  frontend:
    build:
      context: $APP_DIR/frontend
      dockerfile: Dockerfile
      args:
        VITE_API_BASE: "https://$DOMAIN/ExampleSecurity"
    restart: unless-stopped
    networks:
      - app
    logging:
      driver: local

  gateway:
    image: nginx:1.31.1-alpine
    restart: unless-stopped
    ports:
      - "127.0.0.1:18000:80"
    volumes:
      - $GATEWAY_CONFIG:/etc/nginx/nginx.conf:ro
    depends_on:
      - frontend
      - backend
    networks:
      - app
    logging:
      driver: local

networks:
  app:
COMPOSE_YAML
chmod 0600 "$COMPOSE_FILE"

export DOMAIN
log "Building and starting ExampleSecurity"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build \
    --scale backend="$BACKEND_REPLICAS" \
    --scale frontend="$FRONTEND_REPLICAS"


log "Preparing Nginx for Let's Encrypt validation"
cat > "$HOST_NGINX_AVAILABLE" <<NGINX_HTTP
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    location /.well-known/acme-challenge/ {
        root $ACME_WEBROOT;
    }

    location / {
        proxy_pass http://127.0.0.1:18000;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto http;
    }
}
NGINX_HTTP
ln -sfn "$HOST_NGINX_AVAILABLE" "$HOST_NGINX_ENABLED"
if [[ -L /etc/nginx/sites-enabled/default ]]; then
    unlink /etc/nginx/sites-enabled/default
fi
nginx -t
systemctl enable --now nginx
systemctl reload nginx

if [[ ! -s "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" || ! -s "/etc/letsencrypt/live/$DOMAIN/privkey.pem" ]]; then
    log "Requesting the Let's Encrypt certificate"
    certbot certonly --webroot \
        --webroot-path "$ACME_WEBROOT" \
        --domain "$DOMAIN" \
        --email "$LETSENCRYPT_EMAIL" \
        --agree-tos \
        --no-eff-email \
        --non-interactive
fi

log "Enabling production HTTPS"
cat > "$HOST_NGINX_AVAILABLE" <<NGINX_HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    location /.well-known/acme-challenge/ {
        root $ACME_WEBROOT;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name $DOMAIN;

    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    add_header Strict-Transport-Security "max-age=31536000" always;

    client_max_body_size 256k;
    server_tokens off;
    add_header Content-Security-Policy "default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "no-referrer" always;
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;

    location / {
        proxy_pass http://127.0.0.1:18000;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
NGINX_HTTPS

cat > /etc/letsencrypt/renewal-hooks/deploy/reload-example-security-nginx.sh <<'RENEW_HOOK'
#!/bin/sh
set -eu
/usr/sbin/nginx -t
/bin/systemctl reload nginx
RENEW_HOOK
chmod 0750 /etc/letsencrypt/renewal-hooks/deploy/reload-example-security-nginx.sh

nginx -t
systemctl reload nginx
systemctl enable --now certbot.timer 2>/dev/null || true

log "Verifying the deployment"
docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
curl --fail --silent --show-error --head "https://$DOMAIN/" >/dev/null
curl --fail --silent --show-error "https://$DOMAIN/ExampleSecurity/api/csrf" >/dev/null

printf '\nDeployment completed successfully.\n'
printf 'Application: https://%s/\n' "$DOMAIN"
printf 'API:         https://%s/ExampleSecurity\n' "$DOMAIN"
printf 'Secrets:     %s (root-only)\n' "$ENV_FILE"
printf 'Compose:     %s\n' "$COMPOSE_FILE"
printf '\nThe application gateway is bound only to 127.0.0.1.\n'
printf 'MongoDB is provided by Atlas; no MongoDB service or database port runs on this VPS.\n'
printf 'SMTP is external; no local mail-capture service runs on this VPS.\n'
printf 'Next: verify Atlas backups and perform a test restore.\n'
