# Krystal High-Availability Production Deployment

This document describes the production deployment design added to `example-security` for running two independent Krystal/Katapult application servers behind a Krystal managed load balancer, with MongoDB Atlas as the shared database and Spring Session store.

The aim is to keep the service available when one application server, Docker container, or underlying compute host fails, without introducing Kubernetes.

## Production architecture

```text
                              USERS
                                |
                              HTTPS
                                |
                     +----------------------+
                     | Krystal managed      |
                     | load balancer        |
                     | health: GET /health  |
                     +----------+-----------+
                                |
                  +-------------+-------------+
                  |                           |
                  v                           v
        +--------------------+      +--------------------+
        | Krystal app-01     |      | Krystal app-02     |
        | 2 vCPU / 4 GB      |      | 2 vCPU / 4 GB      |
        | Docker Compose     |      | Docker Compose     |
        | nginx / React      |      | nginx / React      |
        | Spring Boot        |      | Spring Boot        |
        +---------+----------+      +----------+---------+
                  |                            |
                  +-------------+--------------+
                                |
                                v
                     +----------------------+
                     | MongoDB Atlas        |
                     | shared application   |
                     | data + sessions      |
                     +----------------------+
```

Both application nodes are intended to be disposable and interchangeable. Neither node should contain unique production data.

## What was added to the project

The production/HA work is centred around these files:

```text
docker-compose.production.yml
infrastructure/
    README.md
    versions.tf
    provider.tf
    variables.tf
    main.tf
    outputs.tf
    templates/
        cloud-init.yaml
    scripts/
        deploy.sh
        health-check.sh
        rebuild-node.sh
```

The frontend also contains production nginx/Docker configuration, and the backend exposes Spring Boot Actuator readiness information used by the load balancer health check.

## Failure behaviour

### Docker container failure

Each production container uses:

```yaml
restart: unless-stopped
```

Docker therefore attempts to restart a failed backend or frontend container on the same VM automatically.

### Application or complete VM failure

The Krystal load balancer should health-check:

```text
GET /health
```

If a node stops returning HTTP 200, the load balancer removes that node from normal traffic and sends requests to the surviving node.

Normal operation:

```text
LB -> app-01
   -> app-02
```

After app-01 fails:

```text
LB -X-> app-01
   ----> app-02
```

The surviving server therefore needs enough spare capacity to carry the full normal workload for a period while redundancy is restored.

A useful sizing target is roughly 35-45% sustained CPU per server in normal operation, leaving enough headroom for one server to carry the application alone after failure.

### Atlas database-node failure

MongoDB Atlas remains outside the application VMs. The application servers share the same Atlas deployment, so losing one Krystal VM does not remove the database or Spring Session data.

A normal dedicated Atlas replica set protects against individual database member failure. Protecting against loss of an entire cloud region requires an Atlas multi-region deployment.

## Why Kubernetes is not required

This deployment deliberately separates responsibilities:

```text
Docker restart policy     -> container recovery
Krystal load balancer     -> traffic failover / health checks
Krystal infrastructure    -> VM/host infrastructure
Terraform                 -> repeatable VM/infrastructure creation
MongoDB Atlas             -> database replication/failover
```

For two application nodes this provides the main high-availability features required without the operational overhead of maintaining a Kubernetes cluster.

## Krystal/Katapult Terraform

The Terraform provider is configured in `infrastructure/`.

The provider is pinned because the Katapult provider schema is still evolving. Before enabling the VM and load-balancer resources, verify the exact schema and account-specific IDs rather than guessing them.

Typical setup:

```bash
cd infrastructure
terraform init
terraform providers schema -json > provider-schema.json
```

Set the Katapult credentials outside Git, for example:

```bash
export KATAPULT_API_KEY='...'
export KATAPULT_ORGANIZATION='...'
export KATAPULT_DATA_CENTER='uk-lon-01'
```

Do not commit the API key.

## VM segregation

`infrastructure/main.tf` defines a segregated VM group:

```hcl
resource "katapult_virtual_machine_group" "app" {
  name      = "${var.app_name}-production"
  segregate = true
}
```

The intention is to keep `app-01` and `app-02` away from the same underlying compute host where supported by Krystal.

Before production use, confirm with Krystal what `segregate = true` guarantees in the chosen London environment. In particular, ask whether the two VMs can be placed in separate:

- physical compute hosts;
- racks;
- power domains/feeds;
- availability or failure zones.

If Krystal can provide two independent London failure zones, that is preferable to putting patient-facing application servers in different countries simply for redundancy.

## Account-specific Terraform values

The project intentionally does not invent these values:

- 2-vCPU/4-GB VM package ID;
- Ubuntu disk-template/image ID;
- network IDs;
- security-group IDs;
- load-balancer resource fields specific to the current provider/API.

Resolve these from the Krystal console/API after the production account exists.

The commented VM block in `infrastructure/main.tf` is the safe starting point for mapping those verified values.

## Cloud-init

`infrastructure/templates/cloud-init.yaml` is intended to bootstrap a newly created application node.

The desired process is:

```text
Terraform creates VM
        |
        v
cloud-init starts
        |
        v
install/configure Docker
        |
        v
place application deployment
        |
        v
obtain production secrets securely
        |
        v
docker compose up -d
        |
        v
/health becomes UP
        |
        v
load balancer can use the node
```

A production VM should be reproducible from source/configuration rather than being a manually customised machine that cannot easily be replaced.

## Production Docker Compose

Use:

```bash
docker compose -f docker-compose.production.yml up -d
```

or the supplied deployment script.

The production compose file contains two containers on each VM:

### backend

- Spring Boot application;
- internal port 8080 only;
- production environment loaded from a protected env file;
- Actuator readiness health check;
- `restart: unless-stopped`.

### frontend

- nginx serving the React production build;
- port 80 exposed to the Krystal load balancer after LB TLS termination;
- proxies the application API to the local backend;
- exposes `/health` for the Krystal load balancer;
- `restart: unless-stopped`.

The backend port must not be exposed directly to the Internet.

## Load-balancer health check

Configure the Krystal load balancer to request:

```text
GET /health
```

The nginx frontend proxies this to:

```text
/ExampleSecurity/actuator/health/readiness
```

A healthy response should ultimately report Spring Boot readiness as UP.

The readiness check is intended to establish that the application can actually serve requests, not simply that a Java process exists.

MongoDB connectivity should remain relevant to readiness. SMTP should not make the entire application unhealthy: an email-provider outage should not stop patients or staff using otherwise available application functions.

## Firewall/security-group policy

The intended policy is restrictive.

| Direction | Traffic | Policy |
|---|---|---|
| Internet -> LB | HTTPS 443 | Allow |
| LB -> application VMs | HTTP 80 or selected private/backend port | Allow |
| Internet -> application VMs | direct web access | Prefer deny |
| Internet -> backend | TCP 8080 | Deny |
| Admin -> application VMs | SSH 22 | Restrict to trusted admin source/VPN |
| Application VMs -> Atlas | MongoDB TLS | Allow outbound |
| Application VMs -> SMTP | SMTP/TLS | Allow outbound |
| Other inbound traffic | unspecified | Deny |

Use Krystal security groups/firewalling and retain host firewalling where appropriate for defence in depth.

## MongoDB Atlas network access

Atlas connections originate from the application VMs, not from the public load-balancer address.

Therefore the Atlas network access list should contain the stable outbound public IPv4 addresses of both application servers, for example:

```text
app-01 public outbound IP /32
app-02 public outbound IP /32
```

Do not use:

```text
0.0.0.0/0
```

for production Atlas access.

## Production secrets

Each VM should receive the same production settings, including at least:

```text
MONGODB_URI
FIELD_CRYPTO_PASSPHRASE
FIELD_CRYPTO_MASTER_SALT_B64
SMTP configuration
cookie/application configuration
public URL configuration
```

The production compose file expects a protected backend environment file. The default path is:

```text
/etc/example-security/backend.env
```

Protect it:

```bash
sudo chown root:root /etc/example-security/backend.env
sudo chmod 600 /etc/example-security/backend.env
```

Do not store production secrets directly in Terraform `.tf` files. Marking a Terraform variable as `sensitive` does not guarantee that the value will never enter Terraform state.

Do not commit production `.env` files, Terraform state, Terraform plans, provider schema dumps, private keys or certificates containing private keys.

## Important encryption requirement

Both application nodes must use identical active values for:

```text
FIELD_CRYPTO_PASSPHRASE
FIELD_CRYPTO_MASTER_SALT_B64
```

and must connect to the same application data.

The current encryption key/salt rotation process must remain coordinated across both nodes. Do not leave one server running the old crypto configuration while the other has moved to the new configuration, because that can cause decryption failures.

Until dual-key rolling rotation is implemented, perform crypto rotation during a controlled maintenance operation in which both application servers switch consistently.

## First production deployment

A practical first deployment sequence is:

1. Create/configure the Atlas dedicated production cluster.
2. Create the Krystal networking/security-group rules.
3. Create the segregated application VM group.
4. Create `app-01` and `app-02` using the verified 2-vCPU/4-GB package and Ubuntu image.
5. Assign stable outbound/public addresses as required.
6. Add both VM outbound IPs to the Atlas network access list.
7. Install `/etc/example-security/backend.env` securely on both VMs.
8. Deploy the same application version on both nodes.
9. Confirm locally that each node returns healthy readiness information.
10. Create/configure the Krystal managed load balancer.
11. Configure `GET /health` as the LB health check.
12. Confirm that both nodes are healthy behind the LB.
13. Point production DNS at the load balancer.
14. Test actual login/session behaviour while requests alternate between nodes.
15. Test failover by deliberately draining/stopping one application node.

## Deployment script

The supplied deployment script is:

```text
infrastructure/scripts/deploy.sh
```

Typical use from a VM where the release is at `/opt/example-security`:

```bash
sudo EXAMPLE_SECURITY_ENV_FILE=/etc/example-security/backend.env \
  /opt/example-security/infrastructure/scripts/deploy.sh
```

After deployment, verify:

```bash
curl -fsS http://localhost/health
```

and also confirm the externally load-balanced application is functioning.

## Rolling application deployment

Ordinary application releases can be performed without deliberately taking the whole service offline.

Starting state:

```text
app-01 -> version A
app-02 -> version A
```

Procedure:

1. Drain/remove `app-02` from LB traffic.
2. Deploy version B to `app-02`.
3. Verify `/health` and perform a functional smoke test.
4. Return `app-02` to the LB.
5. Drain `app-01`.
6. Deploy version B to `app-01`.
7. Verify it.
8. Return `app-01` to the LB.

Final state:

```text
app-01 -> version B
app-02 -> version B
```

Do not use this mixed-version rolling procedure for a crypto-key/salt rotation until the application supports dual-key rolling rotation.

## Replacing a failed server

The supplied recovery script is:

```text
infrastructure/scripts/rebuild-node.sh
```

The design goal is that rebuilding a dead application node becomes a controlled infrastructure operation rather than manual server reconstruction.

Initially it is reasonable for the sequence to be:

```text
node fails
   |
LB removes it automatically
   |
surviving node serves all users
   |
monitoring alerts administrator
   |
administrator runs controlled Terraform/rebuild operation
   |
new node boots/configures/deploys
   |
health check returns UP
   |
LB restores two-node service
```

This avoids automatically creating replacement servers in response to a brief transient network fault.

## Future automatic replacement

Full automatic node replacement can be added later without Kubernetes if desired.

A separate monitor should check that the desired healthy application-node count is two. If it remains below two for a sustained period, for example five minutes, it can trigger a narrowly controlled recovery action.

Do not host the only recovery monitor on one of the two application VMs; otherwise the recovery mechanism may disappear with the failed machine.

A sensible policy is:

```text
healthy nodes >= 2 -> do nothing
healthy nodes < 2  -> wait/recheck
still < 2          -> alert and/or trigger controlled rebuild
```

The load balancer already keeps the application available during this process, so replacement does not have to happen within seconds.

## Spring Session and load balancing

The application stores sessions in MongoDB/Atlas, allowing both backends to share session state.

That means sticky sessions should not be required for normal operation. Round-robin load balancing is useful because it exercises the genuinely shared-session/stateless design.

Production testing should verify that a logged-in user's successive requests can be served by different application nodes without losing authentication or application state.

## Backups and disaster recovery

The application VMs should be treated as replaceable infrastructure. The important persistent production data is in Atlas.

Maintain:

- Atlas backup/snapshot policy appropriate to the production tier;
- tested database restore procedure;
- source-controlled infrastructure configuration;
- securely backed-up deployment secrets and encryption material;
- container/application release history;
- DNS and certificate recovery information.

Do not rely solely on a VM disk snapshot as the application disaster-recovery strategy.

## Monitoring recommendations

At minimum monitor:

- public HTTPS availability;
- `/health` on each application node where possible;
- load-balancer backend health;
- CPU, RAM and disk use per Krystal VM;
- Docker container restart/failure state;
- Atlas health, connections and storage;
- certificate expiry;
- failed login/throttling/audit anomalies;
- backup success.

An alert should be generated whenever the system drops from two healthy application servers to one even though the public application remains available.

## Capacity assumption

Because one node must temporarily run the full service, avoid sizing the system so both servers are already close to saturation during normal operation.

A reasonable initial aim is:

```text
normal:          each node ~35-45% sustained CPU or less
single-node mode: surviving node ~70-90% at equivalent load
```

Measure actual production behaviour and scale the VM package if needed.

## Items to confirm with Krystal before go-live

Confirm all of the following with Krystal before treating the design as datacentre/failure-domain HA:

1. What exactly does a segregated VM group guarantee?
2. Can the two London VMs be placed on different physical hosts?
3. Can they be placed in separate racks/power domains or availability zones?
4. Is the managed load balancer itself implemented as a highly available service?
5. Can one load balancer target VMs across the required London failure zones?
6. What behaviour should be expected after an underlying compute-host failure?
7. Which Terraform/API resources and IDs should be used for the selected C-4 equivalent VM package, Ubuntu image, networking and load balancer?

## Files that must stay out of Git

The project's `.gitignore` should continue to exclude at least:

```text
*.tfstate
*.tfstate.*
*.tfplan
.terraform/
provider-schema.json
.env
.env.*
!*.env.example
*.pem
*.key
```

Review this list against the existing project before introducing any new secret-management files.

## Summary

The production target is:

```text
2 x Krystal application VMs
+ Krystal managed load balancer
+ MongoDB Atlas
+ Docker Compose
+ Terraform/cloud-init
```

This provides automatic traffic failover and local container recovery while keeping the infrastructure simple enough to operate without Kubernetes.

The critical remaining production work is to populate the account-specific Katapult VM/network/LB values after the Krystal account is available, confirm the physical failure-domain guarantees with Krystal, and test a real single-node failure before go-live.
