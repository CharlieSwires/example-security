# Krystal month-test infrastructure

This Terraform describes the proven test layout for `example-security`:

```text
Internet -> Krystal managed load balancer (HTTPS 443)
         -> two Ubuntu VMs (TCP 5173)
         -> frontend nginx container (port 80)
         -> backend Spring Boot container (port 8080)
         -> MongoDB Atlas
```

It is deliberately conservative. Infrastructure management is **disabled by
default**, existing paid resources are protected with `prevent_destroy`, and a
normal plan cannot silently delete the two VMs, public IPs or load balancer.

## What Terraform manages

When enabled, this configuration manages:

- a segregated VM group;
- two public IPv4 addresses and two application VMs;
- one Krystal managed load balancer;
- HTTPS `443 -> 5173`, HTTP-to-HTTPS redirect, and `GET /health` checks;
- a security group allowing SSH only from an administrator CIDR and port 5173
  only from Krystal's managed load-balancer address list.

The provider cannot currently install Docker, upload files, supply cloud-init,
place a VM into a specific independent Krystal cluster, create DNS records, or
configure MongoDB Atlas. Those remain explicit manual steps. The files in
`templates/` and `scripts/` are deployment aids, not resources applied by the
provider.

## Prerequisites

Run Terraform on the external laptop, not inside an application container.
Install Terraform 1.6 or later and create a restricted Krystal API token.

Export the credentials in the shell that runs Terraform:

```bash
export KATAPULT_API_KEY='replace-with-api-token'
export KATAPULT_ORGANIZATION='replace-with-organization-sub-domain'
export KATAPULT_DATA_CENTER='uk-lon-01'
```

For Git Bash on Windows, the same `export` commands work. Do not put the token
in `terraform.tfvars`, source control, shell history or the release ZIP.

## Initial setup

```bash
cd infrastructure
terraform init
terraform fmt -check
terraform validate
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`. Keep this line unchanged initially:

```hcl
manage_infrastructure = false
```

Set the package and Ubuntu disk-template IDs, certificate ID, administrator
CIDR, and the two existing VM names. The example contains the names used by the
month test. Never use `0.0.0.0/0` for `trusted_admin_cidr`.

The supplied `ssh_public_key` variable is documentation-only because the
current provider has no VM SSH-key argument. Install the key through Krystal's
VM creation workflow or with `ssh-copy-id`.

## Inspect account IDs

The configuration includes read-only inventory data sources. With management
still disabled, use:

```bash
terraform plan
terraform console
```

Useful console expressions are:

```hcl
data.katapult_virtual_machines.available[0].virtual_machines
data.katapult_load_balancers.available[0].load_balancers
data.katapult_security_groups.available[0].security_groups
data.katapult_virtual_machine_packages.available[0].packages
data.katapult_disk_templates.available[0].templates
data.katapult_global_address_lists.global[0].address_lists
```

Enter `exit` to leave the console. Copy IDs carefully; a name is not always an
import ID.

## Adopt the existing month-test system

Terraform state must not be treated as permission to recreate working servers.
First fill in `terraform.tfvars`, then set:

```hcl
manage_infrastructure = true
```

Import each existing object before any normal apply. Substitute the real IDs:

```bash
terraform import 'katapult_ip.app[0]' IP_ID_1
terraform import 'katapult_ip.app[1]' IP_ID_2
terraform import 'katapult_virtual_machine.app[0]' VM_ID_1
terraform import 'katapult_virtual_machine.app[1]' VM_ID_2
terraform import 'katapult_load_balancer.app[0]' LOAD_BALANCER_ID
terraform import 'katapult_load_balancer_rule.https[0]' LOAD_BALANCER_RULE_ID
terraform import 'katapult_security_group.app[0]' SECURITY_GROUP_ID
```

If a segregated VM group already exists, also import it:

```bash
terraform import 'katapult_virtual_machine_group.app[0]' VM_GROUP_ID
```

If it does not exist, import everything else first. Review a targeted plan for
the group and VM association, then create it deliberately. Krystal Support must
still arrange one VM per independent London cluster; Terraform's `segregate`
setting provides anti-affinity but is not a substitute for that support request.

After all imports, reconcile state before changing anything:

```bash
terraform plan -refresh-only -out=refresh.tfplan
terraform show refresh.tfplan
terraform apply refresh.tfplan
terraform plan -out=month-test.tfplan
terraform show month-test.tfplan
```

Do **not** apply the normal plan until every proposed replacement or deletion is
understood. An imported resource can still be replaced if its configured
package, template, disk or name differs from reality.

## Creating a replacement VM

When a VM genuinely needs replacement:

1. take and test the required backups;
2. remove or drain the failed VM from load-balancer service;
3. approve only the reviewed Terraform change;
4. ask Krystal Support to place the replacement in the intended cluster;
5. install Docker and the deployment user/key;
6. create `/etc/example-security/backend.env` with mode `0600`;
7. transfer the versioned Docker images and Compose files;
8. start `docker-compose.production.yml`;
9. verify `curl http://127.0.0.1:5173/health` returns HTTP 200;
10. verify the LB reports the node Up before restoring traffic.

Both nodes need the same security/session/encryption configuration. Production
URLs must use the public HTTPS origin without `:5173`, for example:

```text
https://charlie-example-security.co.uk
```

Atlas must allow the VMs' outbound public IPs. Keep SMTP, Google OAuth, Authy,
field-encryption and initial-account secrets outside Terraform and Git.

## Month-test proof to retain

Before cancelling any paid service, preserve enough evidence to reproduce the
test:

- Git commit or clean source ZIP and the exact Docker image tags;
- `terraform.tfvars` stored securely and a redacted copy for documentation;
- Terraform state, final plan, and all Krystal resource IDs;
- `docker save` archives for the frontend and backend images;
- encrypted backup of `backend.env` and the field-crypto seed material;
- Atlas backup/export plus a documented restore test;
- screenshots or exports showing both LB targets Up;
- Krystal Support confirmation of separate-cluster placement;
- functional results for password login, Authy, Google login, email links,
  logout, failover and node recovery.

Never publish state, environment files, private keys, API tokens, OAuth secrets,
passwords, Authy secrets or database connection strings.

## Ending the paid test

`prevent_destroy` intentionally makes `terraform destroy` fail. This guards
against accidentally ending the one-month test early. After backups and proof
have been verified, remove the paid resources deliberately through an approved
shutdown plan or the Krystal UI. Confirm that VMs, extra disks, public IPs, load
balancer, snapshots/backups and Atlas resources have all stopped billing.

Do not weaken the destruction guards merely to make a command succeed.

## Routine commands

```bash
terraform fmt
terraform validate
terraform plan -out=month-test.tfplan
terraform show month-test.tfplan
terraform apply month-test.tfplan
terraform output
```

Commit `.terraform.lock.hcl` so every operator uses the reviewed provider
version. Do not commit `.terraform/`, state files, plans or `terraform.tfvars`.
