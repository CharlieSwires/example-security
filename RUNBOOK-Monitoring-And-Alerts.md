# Monitoring and alerting runbook

Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

## Purpose and limits

The desktop monitor provides an independent, external view of Example Security while the one-month Krystal trial is used to remove deployment issues. It is deliberately read-only: it reports faults but never restarts containers, changes firewall rules or rotates credentials automatically.

The Swing monitor observes application/JVM values, not the whole VM. Its filesystem figure is the filesystem visible to the backend container. Use Krystal metrics and SSH host tools for VM RAM, host disk, network and Docker restart information. The Krystal load-balancer page remains authoritative for whether both targets are healthy.

## Data flow

1. The laptop performs an unauthenticated external `GET /health` through the public domain and records status, latency and TLS-certificate lifetime.
2. It calls `/ExampleSecurity/api/monitoring/snapshot` over HTTPS with `X-Monitoring-Key`.
3. The load balancer may route repeated samples to different backend nodes. Each response includes its configured instance ID.
4. The laptop displays the readings and sends transition/recovery messages through Twilio when enabled.
5. MongoDB Atlas continues to provide its own database alerts independently.

No patient data, user records, passwords, OAuth tokens, MongoDB connection strings or stack traces are returned or included in SMS messages.

## 1. Enable the protected backend endpoint

Generate one high-entropy key on a trusted machine:

```bash
openssl rand -base64 48
```

Add these values to `/etc/example-security/backend.env` on both application VMs. Use the same key but a unique instance ID:

```properties
MONITORING_ENABLED=true
MONITORING_API_KEY=paste-the-generated-key
MONITORING_INSTANCE_ID=sweet-quiet-caterpillar
```

Use `shapely-yummy-kiwi` on the other node. Recreate each backend with the production Compose file:

```bash
docker compose -f docker-compose.production.yml up -d --force-recreate backend frontend
docker compose -f docker-compose.production.yml ps
```

Test without placing the key in shell history:

```bash
read -rsp 'Monitoring key: ' MONITOR_KEY; echo
curl -fsS -H "X-Monitoring-Key: $MONITOR_KEY" \
  https://charlie-example-security.co.uk/ExampleSecurity/api/monitoring/snapshot
unset MONITOR_KEY
```

An absent/wrong key must return HTTP 401. If monitoring is disabled the endpoint is not registered.

## 2. Install the laptop monitor

Install a JDK 17 and Maven. Verify:

```powershell
java -version
mvn -version
```

In `monitoring-desktop`:

```powershell
Copy-Item monitor.properties.example monitor.properties
Copy-Item monitor.env.example monitor.env
.\build.bat
```

Set the public URLs and thresholds in `monitor.properties`. Set `expected.instance.ids` to the two `MONITORING_INSTANCE_ID` values so a missing node is detected even while the public service remains healthy. Put the same backend monitoring key in `monitor.env`. Both local files are ignored by Git.

Restrict the secret file to the current Windows account:

```powershell
icacls monitor.env /inheritance:r
icacls monitor.env /grant:r "$env:USERNAME:(R,W)"
```

Start it with:

```powershell
.\run-monitor.ps1
```

Prevent Windows from sleeping while monitoring. For unattended startup, create a Task Scheduler task that runs PowerShell at sign-in with:

```text
-NoProfile -ExecutionPolicy Bypass -File C:\path\to\example-security\monitoring-desktop\run-monitor.ps1
```

This laptop is a useful trial monitor but remains a single point of failure. No SMS can be sent if the laptop is off, asleep, disconnected or the monitor has stopped. For production, add a hosted external uptime service and a separate dead-man/heartbeat alert.

## 3. Configure Twilio SMS

In Twilio, locate the Account SID and Auth Token and obtain an SMS-capable Twilio sender number. During a trial, verify the destination phone first. Put all numbers in E.164 format.

Edit `monitor.env`:

```properties
TWILIO_ACCOUNT_SID=AC...
TWILIO_AUTH_TOKEN=...
TWILIO_FROM_NUMBER=+44...
MONITOR_SMS_TO=+44...
```

Set this in `monitor.properties`:

```properties
sms.enabled=true
sms.cooldown.minutes=30
```

Restart the monitor and use **Send test SMS**. A Twilio HTTP failure is shown in the event history without exposing the response body or credentials. Review Twilio messaging charges and UK sender rules before production.

## 4. Atlas Free alerts

Atlas Free includes a limited set of alerts, including connections, logical database size, network and operation counters. Configure those in the Atlas project. Free-tier alerts do not replace the application MongoDB ping in this monitor, and they do not cover all metrics available on paid dedicated clusters.

Use at least email notifications in Atlas. If SMS is available for the selected notification configuration and account, configure it separately so an Atlas alert does not depend on the monitoring laptop.

## Alert response table

| Alert | First checks | Remedial action |
|---|---|---|
| Public health down | Krystal target status, DNS, certificate, both VMs, `docker compose ps` | Restore the failed target/container; do not restart both nodes together |
| One Krystal target down | SSH to that VM; inspect frontend/backend health and logs | Repair/recreate only that node, then confirm it rejoins healthy |
| Response time slow | Compare both nodes, VM CPU/RAM, Mongo latency and recent traffic | Identify the bottleneck before resizing; capture timings during load test |
| MongoDB ping failed | Atlas status/alerts, IP access list, DNS and `MONGODB_URI` | Restore connectivity; never paste the URI into tickets or logs |
| JVM heap high | Observe whether it falls after GC; inspect load and logs | Capture evidence, consider heap/container sizing, investigate leaks |
| Container filesystem high | Run `df -h`, `docker system df`, inspect logs/images | Rotate logs and remove only confirmed-unused artifacts; preserve required images |
| TLS expiry warning | Check Krystal certificate renewal and DNS validation | Renew/repair validation before expiry and retest externally |
| No monitor updates/SMS | Check laptop power, network, Java process and Twilio console | Restart the monitor; add hosted heartbeat monitoring for production |

Useful node commands:

```bash
docker compose -f docker-compose.production.yml ps
docker logs --since 15m example-security-frontend-1
docker logs --since 15m example-security-backend-1
free -h
df -h
docker system df
```

Never include secrets, session cookies, clinical content or full environment files in support tickets.

## Trial acceptance test

Run these tests before relying on alerts:

1. Press **Send test SMS** and confirm receipt.
2. Stop the frontend on one node only; confirm Krystal removes that target while the public site remains up.
3. Restore it and confirm a healthy target and recovery indication.
4. Temporarily stop both frontends during a controlled window; confirm the public-down alert only after the configured consecutive failures.
5. Restore both and confirm recovery.
6. Confirm both unique instance IDs appear after repeated snapshot samples.
7. Test Atlas email alerts independently.
8. Record alert, detection and recovery times in the trial notes.

Repeat the controlled alert test weekly during the month. An untested alerting path should be treated as unavailable.
