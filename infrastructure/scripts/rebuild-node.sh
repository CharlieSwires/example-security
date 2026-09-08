#!/usr/bin/env bash
set -euo pipefail
cat <<'MSG'
Recovery procedure:
1. Confirm the Krystal load balancer has removed the failed node.
2. If Krystal has not recovered the VM, recreate the missing node with the
   reviewed Terraform/Katapult definition.
3. Securely install /etc/example-security/backend.env on the replacement.
4. Copy/checkout this release to /opt/example-security.
5. Run infrastructure/scripts/deploy.sh.
6. Verify /health returns HTTP 200 before allowing the LB to use the node.

Do not rotate FIELD_CRYPTO_PASSPHRASE or FIELD_CRYPTO_MASTER_SALT_B64 during a
single-node rebuild. Both production backends must use the same crypto material.
MSG
