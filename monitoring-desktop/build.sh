#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mvn clean package
printf '%s\n' 'Built target/example-security-monitor.jar'
