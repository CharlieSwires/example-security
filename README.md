# ExampleSecurity

Spring Boot + Spring Security + MongoDB Atlas + React example.

## What this gives you

- Java 17 Spring Boot backend
- Spring Security role-based access
- MongoDB Atlas user storage
- PBKDF2WithHmacSHA256 password hashing with per-user salt
- React frontend
- Bootstrap 5 look and feel
- Context path: `/ExampleSecurity`

## Role rules

| Path | Access |
|---|---|
| `POST /ExampleSecurity/api/login` | Public login endpoint |
| `GET /ExampleSecurity/user` | `USER` or `SUPER` |
| `GET /ExampleSecurity/developer` | `DEVELOPER` or `SUPER` |
| `/ExampleSecurity/api/admin/**` | `SUPER` only |

`SUPER` can access everything.

## Backend setup

### env.list file

```text
# Copy this to env.list and fill in real values. Do not commit env.list.

MONGODB_URI=mongodb://mongo:27017/example_security
INITIAL_SUPER_USERNAME=super
INITIAL_SUPER_PASSWORD=ChangeThisPassword123!

# HTTPS local frontend/backend setup
SSL_ENABLED=true
SSL_KEYSTORE_PASSWORD=changeit
SSL_KEY_ALIAS=examplesecurity
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=lax
CORS_ALLOWED_ORIGINS=https://localhost:5173
FRONTEND_BASE_URL=https://localhost:5173
BACKEND_BASE_URL=https://localhost:8080/ExampleSecurity

# Local Mailpit defaults
MAIL_HOST=mailpit
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@example-security.local
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false

# Login throttling / brute-force protection
LOGIN_MAX_USER_IP_FAILURES=5
LOGIN_MAX_IP_FAILURES=25
LOGIN_FAILURE_WINDOW_MINUTES=15
LOGIN_LOCKOUT_MINUTES=15

SECURITY_AUDIT_PERSIST=true

LOGIN_THROTTLE_PERSISTENT=true

SESSION_TIMEOUT=30m

SECURITY_DEBUG_REQUEST_LOGGING=false
```

### application.yml file

```yaml
server:
  port: 8080
  servlet:
    context-path: /ExampleSecurity
    session:
      timeout: ${SESSION_TIMEOUT:30m}
      cookie:
        http-only: true
        secure: ${SESSION_COOKIE_SECURE:false}
        same-site: ${SESSION_COOKIE_SAME_SITE:lax}
  ssl:
    enabled: ${SSL_ENABLED:false}
    key-store: ${SSL_KEYSTORE:classpath:keystore.p12}
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeit}
    key-store-type: PKCS12
    key-alias: ${SSL_KEY_ALIAS:examplesecurity}

spring:
  data:
    mongodb:
      uri: ${MONGODB_URI:mongodb://localhost:27017/example_security}
  mail:
    host: ${MAIL_HOST:localhost}
    port: ${MAIL_PORT:1025}
    username: ${MAIL_USERNAME:}
    password: ${MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${MAIL_SMTP_AUTH:false}
          starttls:
            enable: ${MAIL_SMTP_STARTTLS:false}

app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173,https://localhost:5173}
  frontend-base-url: ${FRONTEND_BASE_URL:https://localhost:5173}
  backend-base-url: ${BACKEND_BASE_URL:https://localhost:8080/ExampleSecurity}
  mail:
    from: ${MAIL_FROM:no-reply@example-security.local}
  initial-super:
    username: ${INITIAL_SUPER_USERNAME:super}
    password: ${INITIAL_SUPER_PASSWORD:ChangeThisPassword123!}
  security:
    debug-request-logging: ${SECURITY_DEBUG_REQUEST_LOGGING:false}
    audit:
      persist: ${SECURITY_AUDIT_PERSIST:true}
    login:
      persistent: ${LOGIN_THROTTLE_PERSISTENT:true}
      max-user-ip-failures: ${LOGIN_MAX_USER_IP_FAILURES:5}
      max-ip-failures: ${LOGIN_MAX_IP_FAILURES:25}
      failure-window-minutes: ${LOGIN_FAILURE_WINDOW_MINUTES:15}
      lockout-minutes: ${LOGIN_LOCKOUT_MINUTES:15}
```

```bash
cd backend
```


Run:

```bash
mvn spring-boot:run
```

Backend starts at:

```text
http://localhost:8080/ExampleSecurity
```

## Frontend setup

```bash
cd frontend
npm install
npm run dev
```

Frontend starts at:

```text
http://localhost:5173
```

## Initial login

The backend creates this initial `SUPER` account if it does not already exist:

```text
username: super
password: ChangeThisPassword123!
```

Change this before using anything beyond local testing.

## HTTPS / TLS

The frontend posts the password to the backend. For local development this example uses HTTP, but for real use you must serve the backend over HTTPS so the password is encrypted in transit.

You can generate a local self-signed certificate:

```bash
cd backend
keytool -genkeypair -alias examplesecurity -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore src/main/resources/keystore.p12 -validity 3650
```

Then run with:

### Windows PowerShell

```powershell
$env:SSL_ENABLED="true"
$env:SSL_KEYSTORE_PASSWORD="your-keystore-password"
mvn spring-boot:run
```

### Git Bash / Linux

```bash
export SSL_ENABLED=true
export SSL_KEYSTORE_PASSWORD='your-keystore-password'
mvn spring-boot:run
```

Then use:

```text
https://localhost:8080/ExampleSecurity
```

## Production warning

This example keeps the username/password in browser `sessionStorage` so protected calls can use HTTP Basic Auth.

That is fine for a small local learning example, but do not use this design for a public production system. A production system should use secure `HttpOnly`, `Secure`, `SameSite` cookies, server-side sessions, JWT/OAuth2/OIDC, or another hardened authentication flow.


## Docker

This ZIP also includes Docker support.

Run both backend and frontend:

```bash
docker compose up --build
```

Then open:

```text
http://localhost:5173
```

See `README-Docker.md` for MongoDB Atlas and local MongoDB options.


## Updating a user's password

The SUPER admin screen includes a **New password** field and **Update pw** button for each user.

Backend endpoint:

```text
PUT /ExampleSecurity/api/admin/users/{username}/password
```

Request body:

```json
{
  "password": "new-password-here"
}
```

The password is not stored directly. The backend creates a new salt and stores only the new PBKDF2 hash and salt.


## Email verification and password reset

This ZIP includes verified-email and password-reset support.

See:

```text
README-Email-Password-Reset.md
```

For local Docker testing, Mailpit is included. After `docker compose up --build`, open:

```text
http://localhost:8025
```

to see verification and reset emails.


## Session cookie + CSRF version

This version includes a more secure login design:

```text
POST /api/login over HTTPS
server verifies PBKDF2 hash
server creates session
browser gets HttpOnly Secure SameSite cookie
React calls API with credentials: include
CSRF enabled
password never stored in browser JS
```

See:

```text
README-Session-Cookie-CSRF.md
```


## Login throttling

This package includes in-memory login throttling to slow brute-force attempts. See `README-Login-Throttling.md`.
