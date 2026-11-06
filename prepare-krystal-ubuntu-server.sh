#!/usr/bin/env bash
# Prepare an Ubuntu 22.04/24.04 Krystal VM for Example Security.
#
# Installs:
#   - current Ubuntu package updates
#   - Docker Engine, Compose and Buildx from Docker's official repository
#   - Terraform from HashiCorp's official repository
#   - OpenJDK 17 (headless), including keytool
#   - Git and common deployment utilities
#
# Recommended invocation from the VM:
#   sudo bash prepare-krystal-ubuntu-server.sh --docker-user ubuntu
#
# Optional host firewall (Krystal Security Groups must still be configured):
#   sudo bash prepare-krystal-ubuntu-server.sh \
#     --docker-user ubuntu --enable-ufw --ssh-port 22

set -Eeuo pipefail
IFS=$'\n\t'

DOCKER_USER="${SUDO_USER:-}"
ENABLE_UFW=false
SSH_PORT=22
RUN_UPGRADE=true
RUN_DOCKER_TEST=true

usage() {
    cat <<'USAGE'
Usage:
  sudo bash prepare-krystal-ubuntu-server.sh [options]

Options:
  --docker-user USER   Add USER to the docker group. Defaults to the account
                       that invoked sudo; no user is added when run directly
                       as root unless this option is supplied.
  --enable-ufw         Allow the selected SSH port and enable UFW. Krystal
                       Security Groups remain the primary network firewall.
  --ssh-port PORT      SSH port allowed by UFW (default: 22).
  --skip-upgrade       Refresh package indexes but do not upgrade installed
                       Ubuntu packages.
  --skip-docker-test   Do not run the Docker hello-world verification image.
  -h, --help           Show this help.

This script is safe to rerun. It supports Ubuntu 22.04 and 24.04 on amd64 or
arm64. Reconnect after it finishes if a user was added to the docker group.
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
    printf 'ERROR: preparation failed at line %s (exit %s).\n' \
        "${BASH_LINENO[0]}" "$exit_code" >&2
    exit "$exit_code"
}
trap on_error ERR

while (($#)); do
    case "$1" in
        --docker-user)
            DOCKER_USER="${2:?Missing value after --docker-user}"
            shift 2
            ;;
        --enable-ufw)
            ENABLE_UFW=true
            shift
            ;;
        --ssh-port)
            SSH_PORT="${2:?Missing value after --ssh-port}"
            shift 2
            ;;
        --skip-upgrade)
            RUN_UPGRADE=false
            shift
            ;;
        --skip-docker-test)
            RUN_DOCKER_TEST=false
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "Unknown option: $1"
            ;;
    esac
done

[[ $EUID -eq 0 ]] || die "Run this script through sudo or as root."
[[ "$SSH_PORT" =~ ^[0-9]+$ ]] || die "SSH port must be numeric."
((SSH_PORT >= 1 && SSH_PORT <= 65535)) || die "SSH port must be between 1 and 65535."

[[ -r /etc/os-release ]] || die "Cannot identify the operating system."
# shellcheck disable=SC1091
source /etc/os-release
[[ "${ID:-}" == "ubuntu" ]] || die "Ubuntu is required; found ${ID:-unknown}."
case "${VERSION_ID:-}" in
    22.04|24.04|26.04) ;;
    *) die "Ubuntu 22.04 or 24.04 is required; found ${VERSION_ID:-unknown}." ;;
esac

ARCHITECTURE="$(dpkg --print-architecture)"
[[ "$ARCHITECTURE" == "amd64" || "$ARCHITECTURE" == "arm64" ]] \
    || die "Only amd64 and arm64 are supported; found $ARCHITECTURE."

if [[ -n "$DOCKER_USER" ]]; then
    id "$DOCKER_USER" >/dev/null 2>&1 || die "User '$DOCKER_USER' does not exist."
fi

export DEBIAN_FRONTEND=noninteractive

log "Refreshing Ubuntu package indexes"
apt-get update

if [[ "$RUN_UPGRADE" == true ]]; then
    log "Installing current Ubuntu package updates"
    apt-get upgrade -y
fi

log "Installing common deployment tools and OpenJDK 17"
apt-get install -y \
    ca-certificates \
    curl \
    git \
    gnupg \
    gzip \
    jq \
    openssl \
    openjdk-17-jdk-headless \
    unzip \
    ufw \
    wget

log "Checking for Docker packages that conflict with Docker CE"
CONFLICTING_PACKAGES=()
for package_name in docker.io docker-compose docker-compose-v2 docker-doc docker-buildx podman-docker containerd runc; do
    if dpkg-query -W -f='${Status}' "$package_name" 2>/dev/null | grep -q 'install ok installed'; then
        CONFLICTING_PACKAGES+=("$package_name")
    fi
done
if ((${#CONFLICTING_PACKAGES[@]} > 0)) && \
   ! dpkg-query -W -f='${Status}' docker-ce 2>/dev/null | grep -q 'install ok installed'; then
    printf 'Conflicting pre-existing Docker packages were found:\n' >&2
    printf '  %s\n' "${CONFLICTING_PACKAGES[@]}" >&2
    die "Remove or migrate these deliberately before installing Docker CE; the script will not remove an existing container runtime automatically."
fi

log "Configuring Docker's official Ubuntu repository"
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
    -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: ${ARCHITECTURE}
Signed-By: /etc/apt/keyrings/docker.asc
EOF

apt-get update

log "Installing Docker Engine, Compose and Buildx"
apt-get install -y \
    docker-ce \
    docker-ce-cli \
    containerd.io \
    docker-buildx-plugin \
    docker-compose-plugin
systemctl enable --now docker

if [[ -n "$DOCKER_USER" ]]; then
    log "Adding '$DOCKER_USER' to the docker group"
    usermod -aG docker "$DOCKER_USER"
fi

log "Configuring HashiCorp's official Ubuntu repository"
curl -fsSL https://apt.releases.hashicorp.com/gpg \
    | gpg --dearmor --yes -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
chmod a+r /usr/share/keyrings/hashicorp-archive-keyring.gpg

cat >/etc/apt/sources.list.d/hashicorp.list <<EOF
deb [arch=${ARCHITECTURE} signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com ${UBUNTU_CODENAME:-$VERSION_CODENAME} main
EOF

apt-get update

log "Installing Terraform"
apt-get install -y terraform

if [[ "$ENABLE_UFW" == true ]]; then
    log "Allowing SSH port $SSH_PORT and enabling the host firewall"
    ufw allow "${SSH_PORT}/tcp"
    ufw --force enable
else
    log "Leaving UFW disabled"
    printf '%s\n' \
        "Use --enable-ufw only after confirming the VM's actual SSH port." \
        "Configure the Krystal Security Group even when UFW is enabled."
fi

if [[ "$RUN_DOCKER_TEST" == true ]]; then
    log "Running Docker's hello-world verification"
    docker run --rm hello-world
fi

log "Verifying installed tools"
docker version --format 'Docker Engine: {{.Server.Version}}'
docker compose version
docker buildx version
terraform version
java -version
keytool -help >/dev/null
printf 'keytool: %s\n' "$(command -v keytool)"
git --version

if [[ -f /var/run/reboot-required ]]; then
    printf '\nA reboot is required to finish applying Ubuntu updates:\n'
    printf '  sudo reboot\n'
fi

if [[ -n "$DOCKER_USER" ]]; then
    printf '\nLog out and reconnect before %s uses Docker without sudo.\n' "$DOCKER_USER"
fi

cat <<'NEXT_STEPS'

Server preparation completed.

Still configure these outside this script:
  1. Krystal Security Group: SSH only from your administration IP.
  2. Krystal Security Group: origin web port only from managed LB IPs.
  3. Do not expose Docker ports 2375 or 2376.
  4. Do not publish the Spring backend port 8080 publicly.
  5. Keep MongoDB Atlas, SMTP and application secrets outside images and Git.

Docker-published ports can bypass normal UFW filtering. Treat the Krystal
Security Group as the primary perimeter firewall and publish only the required
frontend/origin port.
NEXT_STEPS
