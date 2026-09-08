# Krystal two-node production deployment

This directory prepares `example-security` for two independent Krystal/Katapult
application VMs behind the Krystal managed load balancer, with MongoDB Atlas as
the shared database/session store. Kubernetes is not required.

## Failure behaviour

* Docker restarts a failed container locally.
* The Krystal load balancer should health-check `GET /health` and remove a node
  that stops returning HTTP 200.
* The surviving VM can serve all traffic while the failed VM is repaired or
  replaced.
* Atlas remains outside both application VMs, so loss of an app VM does not
  remove the database or shared Spring sessions.

## Before the first Terraform apply

The Katapult Terraform provider is pinned because it is still changing. Krystal
package, disk-template, network and load-balancer identifiers are specific to the
account/data centre. Do not guess them.

1. Install Terraform.
2. Export `KATAPULT_API_KEY`, `KATAPULT_ORGANIZATION` and
   `KATAPULT_DATA_CENTER` (London is normally selected in the account).
3. Run `terraform init`.
4. Run `terraform providers schema -json > provider-schema.json` and confirm the
   current `katapult_virtual_machine`, security-group and load-balancer schemas.
5. Obtain the exact 2-vCPU/4-GB package ID and Ubuntu template ID from Krystal.
6. Enable the VM block in `main.tf` using those verified field names/IDs.
7. Add the managed load-balancer and security-group resources using the current
   provider schema/API. Configure the LB to check `/health` and target both VMs.

The VM-group resource is active and uses `segregate = true`. Ask Krystal to
confirm whether segregation guarantees only different compute hosts or also
separate rack/power failure domains.

## VM secret file

Create `/etc/example-security/backend.env` on each VM with mode 0600. Both nodes
must receive the same production values for at least:

* `MONGODB_URI`
* `FIELD_CRYPTO_PASSPHRASE`
* `FIELD_CRYPTO_MASTER_SALT_B64`
* SMTP settings
* initial/cookie/application settings

Do not store these values in Terraform `.tf` files or commit them to Git. Do not
use `0.0.0.0/0` for the Atlas network allow-list; allow the stable outbound IP of
each application VM.

## Load-balancer path

The LB should send client HTTPS traffic to port 80 on each VM after TLS
termination and health-check:

    GET /health

The frontend nginx container proxies this to Spring Boot's readiness endpoint:

    /ExampleSecurity/actuator/health/readiness

Mail health is deliberately excluded, so an SMTP outage does not take the whole
patient application out of service. MongoDB health remains part of readiness.

## Deployment

On each VM place the release at `/opt/example-security`, install the secret env
file, then run:

    sudo EXAMPLE_SECURITY_ENV_FILE=/etc/example-security/backend.env \
      /opt/example-security/infrastructure/scripts/deploy.sh

For rolling deployment, drain one node from the LB, deploy and verify `/health`,
return it to service, then repeat for the second node. Encryption-key/salt
rotation remains a coordinated maintenance operation until dual-key rolling
rotation is implemented.
