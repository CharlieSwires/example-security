#!/usr/bin/env bash
set -euo pipefail

# Usage:
# ./scripts_install_ionos_certs.sh path/to/domain.crt path/to/intermediate.crt path/to/private.key [keystore-password]

DOMAIN_CERT="${1:?Usage: $0 domain.crt intermediate.crt private.key [keystore-password]}"
INTERMEDIATE_CERT="${2:?Usage: $0 domain.crt intermediate.crt private.key [keystore-password]}"
PRIVATE_KEY="${3:?Usage: $0 domain.crt intermediate.crt private.key [keystore-password]}"
KEYSTORE_PASSWORD="${4:-changeit}"

CERT_DIR="frontend/certs"
BACKEND_CERT_DIR="backend/src/main/resources"

mkdir -p "$CERT_DIR" "$BACKEND_CERT_DIR"

echo "Installing public nginx certificate files..."

cat "$DOMAIN_CERT" "$INTERMEDIATE_CERT" > "$CERT_DIR/fullchain.pem"
cp "$PRIVATE_KEY" "$CERT_DIR/privkey.pem"

chmod 644 "$CERT_DIR/fullchain.pem"
chmod 600 "$CERT_DIR/privkey.pem"

echo "Creating optional Spring Boot PKCS12 backend keystore..."

openssl pkcs12 -export \
  -in "$CERT_DIR/fullchain.pem" \
  -inkey "$CERT_DIR/privkey.pem" \
  -out "$BACKEND_CERT_DIR/keystore.p12" \
  -name examplesecurity \
  -passout pass:"$KEYSTORE_PASSWORD"
  
chmod 600 "$BACKEND_CERT_DIR/keystore.p12"

echo "Installed IONOS certificate files:"
echo "  $CERT_DIR/fullchain.pem"
echo "  $CERT_DIR/privkey.pem"
echo "  $BACKEND_CERT_DIR/keystore.p12"
echo
echo "Do not commit certs/, privkey.pem, fullchain.pem, or keystore.p12."