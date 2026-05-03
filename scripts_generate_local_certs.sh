#!/usr/bin/env bash
set -euo pipefail

PASSWORD="${1:-changeit}"
ALIAS="examplesecurity"

rm -f backend/src/main/resources/keystore.* frontend/certs/keystore.*
mkdir -p backend/src/main/resources frontend/certs

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore backend/src/main/resources/keystore.p12 \
  -validity 3650 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -dname "CN=localhost, OU=Dev, O=ExampleSecurity, L=Local, ST=Local, C=GB"

openssl pkcs12 \
  -in backend/src/main/resources/keystore.p12 \
  -clcerts \
  -nokeys \
  -out frontend/certs/keystore.crt \
  -passin pass:"$PASSWORD"

openssl pkcs12 \
  -in backend/src/main/resources/keystore.p12 \
  -nocerts \
  -nodes \
  -out frontend/certs/keystore.key \
  -passin pass:"$PASSWORD"

echo "Generated local development certificates. Do not commit keystore.*, *.key, *.crt, *.p12."
