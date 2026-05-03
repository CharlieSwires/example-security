# Production-hardening changes in this ZIP

This copy-over ZIP adds practical production-hardening without moving to OAuth2.

## Added

- `SecurityAuditService` that writes structured security events to MongoDB in `security_audit_events`.
- Audit events for login success/failure/throttling, logout, admin actions, password reset, email verification and access denied.
- `LoginAttemptService` can now persist throttle state in MongoDB in `login_attempts` so lockouts survive restarts and work better across containers.
- Debug request logging is now disabled by default and only enabled with the `debug-security` Spring profile.
- Security headers: HSTS, `X-Content-Type-Options`, `frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, and a starter CSP.
- Session timeout config through `SESSION_TIMEOUT`, default `30m`.
- Generic API exception handler for cleaner JSON error responses.
- Password minimum length guard.
- Protection against deleting or demoting the last `SUPER` user.

## New environment values

```env
SECURITY_AUDIT_PERSIST=true
LOGIN_THROTTLE_PERSISTENT=true
SESSION_TIMEOUT=30m
```

Existing throttling settings still work:

```env
LOGIN_MAX_USER_IP_FAILURES=5
LOGIN_MAX_IP_FAILURES=25
LOGIN_FAILURE_WINDOW_MINUTES=15
LOGIN_LOCKOUT_MINUTES=15
```

## Important before production

- Do not commit keystores, private keys, `.env`, `env.list`, `.7z`, `.zip`, or generated cert folders.
- Replace local self-signed certificates with real TLS or terminate TLS at a proper reverse proxy/load balancer.
- Review the Content Security Policy for your final deployment hostnames.
- Consider moving rate limiting to Redis/API gateway if you run many application instances.
- Add retention policy for `security_audit_events` appropriate to your needs.


## Test constructor fix

This version updates `LoginAttemptServiceTest` for the hardened constructor signature:

```text
LoginAttemptRepository, persistent flag, user/IP limit, IP limit, failure window, lockout duration, clock
```

The tests use `persistent=false` and a Mockito mock repository so they still exercise the in-memory throttling path.
