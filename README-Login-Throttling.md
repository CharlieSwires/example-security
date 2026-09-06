# Login throttling / brute-force protection

Login throttling is stored in MongoDB so every backend replica shares the same
brute-force counters.

## Behaviour

The backend now tracks failed login attempts by:

- username + client IP address
- client IP address alone

Defaults:

```text
LOGIN_MAX_USER_IP_FAILURES=5
LOGIN_MAX_IP_FAILURES=25
LOGIN_FAILURE_WINDOW_MINUTES=15
LOGIN_LOCKOUT_MINUTES=15
```

After too many failed attempts, `POST /ExampleSecurity/api/login` returns:

```text
429 Too Many Requests
Retry-After: <seconds>
```

Failed attempts below the lockout threshold return:

```text
401 Unauthorized
```

A successful login clears the username+IP failure counter. The IP-wide counter is not cleared on success, which prevents one attacker from rotating usernames and occasionally logging in to reset the whole IP throttle.

## Files changed

```text
backend/src/main/java/com/example/security/security/LoginAttemptService.java
backend/src/main/java/com/example/security/controller/AuthController.java
backend/src/main/resources/application.yml
backend/env.list.example
```

## Important production note

The MongoDB repository is always used; there is no configuration switch or
in-memory fallback. This prevents attackers bypassing throttling by alternating
between backend replicas.


## Constructor injection fix

This version adds `@Autowired` to the public `LoginAttemptService` constructor.

That is needed because `LoginAttemptService` also has a package-private constructor for tests. With two constructors present, Spring may otherwise try to instantiate the service using a no-argument constructor and fail with:

```text
No default constructor found
```
