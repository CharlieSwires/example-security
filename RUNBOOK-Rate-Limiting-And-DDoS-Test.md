# Rate limiting and DDoS-resilience test

The application returns `429 Too Many Requests` when one client address
exhausts its API token bucket. Login failures retain the existing, shared
MongoDB-backed username/IP throttle.

## Production settings

```text
RATE_LIMIT_ENABLED=true
RATE_LIMIT_REQUESTS_PER_SECOND=15
RATE_LIMIT_BURST_CAPACITY=30
RATE_LIMIT_ALLOWLIST=
```

The general limiter is maintained separately on each application node. With
two nodes, load-balancer routing can make the aggregate observed threshold
higher than the configured per-node value. It protects application capacity;
it is not a substitute for Krystal's network-level DDoS protection.

Tomcat obtains the client address through the trusted proxy chain configured by
`TRUSTED_PROXY_REGEX`. Do not broaden that expression to arbitrary public
addresses and do not parse an untrusted `X-Forwarded-For` header in application
code.

## Protection test

1. Confirm that the test target and traffic level are authorised.
2. Leave `RATE_LIMIT_ALLOWLIST` empty.
3. In the load tester, start above the configured aggregate rate. With two
   nodes set the tester comfortably above twice the per-node limit.
4. Confirm that the tester reports HTTP 429 responses separately, while 5xx
   and transport failures remain zero.
5. Check the backend logs for `API_RATE_LIMITED` entries.
6. Stop immediately if latency, 5xx responses or infrastructure alarms rise
   unexpectedly.

## Friendly-client resilience test

Use a second client on a genuinely different public IP address. Two laptops on
the same home broadband normally share one public IPv4 address and therefore
do not test per-IP isolation. A phone hotspot, an authorised remote VM, or a
second external location is suitable.

While the first client is being throttled, repeatedly request `/api/me` from
the friendly client and record success rate and latency. Expected result:

- attack/test client receives 429 responses;
- friendly client continues receiving 200 responses with acceptable latency;
- public health remains 200;
- no 5xx responses, container restarts or database saturation occur.

For a capacity test rather than a protection test, put the generator's exact
public IP temporarily in `RATE_LIMIT_ALLOWLIST`, restart both backends, run a
bounded test, and remove the exception immediately afterwards. Never commit a
real address or permanent bypass to source control.

If the public connection or load balancer is saturated before requests reach
the application, application throttling cannot help. Preserve timestamps and
logs and escalate the incident to Krystal for upstream mitigation.
