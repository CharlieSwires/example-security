# Example Security desktop monitor

This Java 17 Swing application is intended to run on a separate, usually-on laptop. It checks the application from outside Krystal, samples a protected diagnostic endpoint through the load balancer, displays current values and sends optional Twilio SMS alerts and recoveries.

It monitors public uptime and response time, TLS-certificate lifetime, each observed backend JVM, MongoDB reachability, JVM heap, CPU, thread count and the backend container filesystem. It does not replace Krystal load-balancer health, Atlas monitoring, host-level monitoring or tested backups.

## Build and run on Windows

Install a JDK 17 and Maven, then from this directory:

```powershell
Copy-Item monitor.properties.example monitor.properties
Copy-Item monitor.env.example monitor.env
.\build.bat
.\run-monitor.ps1
```

Edit both copied files first. Set `sms.enabled=true` only after supplying all Twilio variables. Numbers must use E.164 format, for example `+44...`. A Twilio trial account can normally send only to a recipient verified in the Twilio console.

The monitor must stay running and the laptop must remain awake and connected. Use Windows Task Scheduler to run `run-monitor.ps1` at sign-in if required. The detailed installation, alert and recovery procedures are in [RUNBOOK-Monitoring-And-Alerts.md](../RUNBOOK-Monitoring-And-Alerts.md).

## Security

- Never commit `monitor.env`, `monitor.properties`, Twilio credentials or the monitoring API key.
- Use a unique random monitoring key of at least 32 characters.
- The monitoring endpoint returns operational values only; it must never return users, clinical data, tokens, database strings or exception details.
- SMS messages intentionally contain only service and node status.
- Do not expose the backend directly. Access the endpoint through HTTPS and the Krystal load balancer.

Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.
