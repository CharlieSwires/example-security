# Remove debug exposure and add security-behaviour tests

This copy-over ZIP focuses on two production-hardening items:

1. Remove debug exposure.
2. Add security behaviour tests.

## Debug exposure removed

`DebugRequestFilter` is now disabled unless you explicitly set:

```text
SECURITY_DEBUG_REQUEST_LOGGING=true
```

Even when enabled, it no longer prints cookie values, session IDs, CSRF tokens, passwords, reset tokens, verification tokens or request bodies.

`SecurityConfig` access-denied handling now records short audit events and returns simple JSON errors instead of printing raw request/session/cookie details.

## Security behaviour tests added

New test file:

```text
backend/src/test/java/com/example/security/security/SecurityBehaviorTest.java
```

It checks:

```text
unauthenticated /api/me returns 401
USER cannot access /api/admin/users
DEVELOPER cannot access /api/admin/users
SUPER can access /api/admin/users
admin DELETE without CSRF is forbidden
login creates a JSESSIONID-backed session
/api/me can use the login session
logout returns 204
basic security headers are present
```

Existing `LoginAttemptServiceTest` still checks throttling logic.

## Run

```bash
cd backend
mvn clean test
```

or:

```bash
docker compose down
docker compose build --no-cache backend frontend
docker compose up
```

Keep `SECURITY_DEBUG_REQUEST_LOGGING=false` in production.


## Compile fix

This version removes the direct `SecurityAuditService` dependency from `SecurityConfig` to avoid package/signature mismatches between project versions.

Audit logging should stay in the controller/service layer where the concrete project already knows the correct `SecurityAuditService` package and method signature. SecurityConfig still returns clean JSON `401` and `403` responses and no longer prints session/cookie details.


## MockMvc session assertion fix

`SecurityBehaviorTest.loginCreatesSessionAndMeUsesSession` now asserts that `MockMvc` created a server-side session and reuses that session for `/api/me`.

It no longer requires a literal `Set-Cookie: JSESSIONID` header, because in `MockMvc` the session can be present on the mock request/response lifecycle without being represented as a browser cookie in the same way as a real container response.
