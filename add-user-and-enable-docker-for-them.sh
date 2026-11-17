#!/usr/bin/env bash
set -Eeuo pipefail

# Create one ordinary Linux user and grant that user access to Docker.
# This script intentionally does NOT grant sudo membership.
#
# Usage:
#   sudo ./add-user-and-enable-docker-for-them.sh USERNAME

readonly SCRIPT_NAME="$(basename -- "$0")"
readonly STATE_DIR="/var/lib/add-user-and-enable-docker-for-them"

usage() {
    printf 'Usage: sudo %s USERNAME\n' "$SCRIPT_NAME" >&2
}

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

if [[ ${EUID} -ne 0 ]]; then
    fail "Run this script as root, for example: sudo ./$SCRIPT_NAME USERNAME"
fi

if [[ $# -ne 1 ]]; then
    usage
    exit 2
fi

username=$1

# Use a conservative portable Linux username format.
if [[ ! $username =~ ^[a-z_][a-z0-9_-]{0,31}$ ]]; then
    fail "Username must start with a lowercase letter or underscore and contain only lowercase letters, digits, underscores or hyphens (maximum 32 characters)."
fi

if getent passwd "$username" >/dev/null; then
    fail "User '$username' already exists. No changes were made."
fi

if [[ -e "$STATE_DIR/$username.done" ]]; then
    fail "Provisioning for '$username' was already completed previously. No changes were made."
fi

command -v useradd >/dev/null 2>&1 || fail "useradd is not installed."
command -v usermod >/dev/null 2>&1 || fail "usermod is not installed."
command -v chpasswd >/dev/null 2>&1 || fail "chpasswd is not installed."
command -v openssl >/dev/null 2>&1 || fail "openssl is required to generate the suggested password."
command -v docker >/dev/null 2>&1 || fail "Docker is not installed."
getent group docker >/dev/null || fail "The Docker group does not exist. Install Docker before running this script."

suggested_password=$(openssl rand -base64 24)

printf '\nSuggested strong password for %s:\n\n  %s\n\n' "$username" "$suggested_password"
printf 'Press Enter to use the suggested password, or type a different password.\n'
IFS= read -r -s -p "Password: " entered_password
printf '\n'

if [[ -z $entered_password ]]; then
    password=$suggested_password
else
    password=$entered_password
fi

if (( ${#password} < 14 )); then
    unset entered_password password suggested_password
    fail "The password must contain at least 14 characters."
fi

IFS= read -r -s -p "Confirm password: " confirmed_password
printf '\n'

if [[ $password != "$confirmed_password" ]]; then
    unset entered_password password confirmed_password suggested_password
    fail "The passwords did not match. No user was created."
fi

created_user=false

rollback() {
    exit_code=$?
    unset entered_password password confirmed_password suggested_password

    if [[ $created_user == true ]] && getent passwd "$username" >/dev/null; then
        printf 'Provisioning failed; removing the partially created user %s.\n' "$username" >&2
        userdel --remove "$username" >/dev/null 2>&1 || true
    fi

    exit "$exit_code"
}
trap rollback ERR INT TERM

printf 'Creating ordinary Linux user %s...\n' "$username"
useradd --create-home --shell /bin/bash --user-group "$username"
created_user=true

printf '%s:%s\n' "$username" "$password" | chpasswd
usermod --append --groups docker "$username"

install -d -m 0750 "$STATE_DIR"
{
    printf 'username=%s\n' "$username"
    printf 'created_utc=%s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    printf 'created_by=%s\n' "${SUDO_USER:-root}"
} > "$STATE_DIR/$username.done"
chmod 0640 "$STATE_DIR/$username.done"

trap - ERR INT TERM
unset entered_password password confirmed_password suggested_password

printf '\nUser %s was created successfully.\n' "$username"
printf 'Docker group membership: '
id -nG "$username"
printf '\nThe user was NOT added to sudo.\n'
printf 'Docker-group membership is effectively root-equivalent because Docker can mount and modify host files.\n'
printf 'The user must start a new login session before Docker access takes effect.\n'
printf 'Test after logging in as that user with: docker run --rm hello-world\n'
