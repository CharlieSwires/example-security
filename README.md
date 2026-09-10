# ExampleSecurity — Optician Hub

ExampleSecurity is a Spring Boot and React reference application for an ophthalmic clinic network. It combines role-based patient and practice workflows with production-style authentication, encrypted clinical fields, shared server-side sessions, audit logging and deployment support.

It is a reference implementation, not a certified clinical product. A real deployment still requires clinical-safety assessment, privacy governance, penetration testing, monitoring and compliance work.

## At a glance

| Area | Implementation |
|---|---|
| Backend | Java 17, Spring Boot 3.3, Spring Security |
| Frontend | React, Vite, Bootstrap 5, Nginx |
| Database | MongoDB or MongoDB Atlas |
| Sessions | Spring Session documents in MongoDB |
| Passwords | Per-user salted PBKDF2-HMAC-SHA256, 600,000 iterations |
| MFA | RFC 6238 TOTP for Authy and compatible apps, plus recovery codes |
| Federated login | Optional Google OAuth 2.0/OpenID Connect for linked users |
| Clinical encryption | AES-256-GCM with deterministic HMAC lookup fields |
| Roles | `PATIENT`, `OFFICE`, `OFFICE_ADMIN`, `HQ`, `SUPER` |
| Deployment | Local Docker Compose and two-node Krystal/Atlas layout |

## Application capabilities

- Patients view their own appointments, clinician and prescription information read-only.
- Office users work with appointments and clinical records for their assigned practice.
- Office administrators manage permitted users and appointments within their practice.
- HQ users manage offices and move patients or clinicians between practices.
- SUPER users administer the system and select an office context.
- Names, telephone numbers, prescriptions and clinical notes are encrypted at rest.
- Current prescriptions and dated historical note prescriptions are retained separately.
- Lists are paginated at 50 records per page.
- Validation is enforced by the backend and reported safely by the frontend.
- Security-sensitive activity can be recorded in the audit collection.

## Role model

| Role | Scope |
|---|---|
| `PATIENT` | Read-only access to the patient's own appointment documents |
| `OFFICE` | Appointment and clinical workflows for the assigned office |
| `OFFICE_ADMIN` | Office workflows plus permitted user administration |
| `HQ` | Cross-office management and office-level transfers |
| `SUPER` | Global administration and selectable office context |

For `PATIENT`, `OFFICE`, `OFFICE_ADMIN` and `HQ`, the associated office is displayed read-only. `SUPER` retains the office selector. Roles and office assignments always come from the MongoDB `AppUser` record.

## Authentication

### Password and session login

Passwords are posted over HTTPS to the backend. Spring Security verifies the PBKDF2 hash and creates a server-side session. The browser receives a `JSESSIONID` cookie configured as `HttpOnly`, `Secure` in HTTPS environments and with an explicit `SameSite` policy.

React never stores the password and cannot read the `HttpOnly` cookie. State-changing requests use CSRF protection. Login throttling limits repeated failures by account/IP and by IP.

### Authenticator-app MFA

Users can enrol Authy or another RFC 6238-compatible authenticator. For an MFA-enabled user, successful password or Google authentication creates only a five-minute server-side challenge. The authenticated session is established only after a valid TOTP or unused recovery code is supplied.

- Six-digit TOTP with 30-second periods and limited clock tolerance
- Maximum five login verification attempts
- Encrypted TOTP secret
- One-use recovery codes stored only as hashes

See [README-Authy-MFA.md](README-Authy-MFA.md).

### Google sign-in

Google login is optional and disabled by default. It identifies an existing user; it never creates an application user or assigns a role.

The user must first sign in normally, verify an application email and deliberately link a Google account using the same verified email. The application stores Google's immutable OpenID Connect `sub` identifier under a unique sparse index. Google access and refresh tokens are not retained. Application MFA is still required when enabled.

See [README-Google-OAuth-Login.md](README-Google-OAuth-Login.md) for Google Cloud test-user, callback and deployment configuration.

## Security controls

- Spring Security URL and method authorization
- CSRF cookie/header flow
- Shared MongoDB sessions for multiple backend instances
- Session ID rotation after authentication
- Login and password-reset throttling
- Verified-email and password-reset flows
- Request-size limits and security response headers
- Trusted-proxy handling for forwarded scheme, address, host and port
- Audit events without passwords, secrets, raw tokens or session IDs
- No live endpoint for rotating master encryption material

The separate `example-security-key-rotator` utility performs controlled offline key rotation while the application is prevented from writing affected data.

## Encrypted data

`FieldCryptoService` provides AES-256-GCM field encryption. The passphrase and master salt must be supplied externally and shared by every backend instance accessing the same data.

Encrypted content includes patient names, telephone numbers, prescriptions, note subjects, note bodies and TOTP secrets. Dates required for sorting may remain clear. Deterministic HMAC values support equality lookup without searchable plaintext.

Never change `FIELD_CRYPTO_PASSPHRASE` or `FIELD_CRYPTO_MASTER_SALT_B64` after storing data except through the documented rotation procedure.

See [README-Encrypted-Clinical-Fields.md](README-Encrypted-Clinical-Fields.md).

## Repository layout

```text
backend/                    Spring Boot API
frontend/                   React/Vite frontend and Nginx configuration
load-balancer/              Local TLS/load-balancer configuration
mongo-init/                 Authenticated local MongoDB bootstrap
infrastructure/             Production Terraform, cloud-init and scripts
deployment/krystal/         Krystal deployment support
docker-compose.yml          Local multi-container environment
docker-compose.production.yml
```

## Local Docker setup

Copy the environment example and replace its placeholders:

```bash
cp env.list.example env.list
```

Keep `env.list` out of Git. Supply a MongoDB Atlas SRV connection string,
authenticated external SMTP credentials, a strong bootstrap password, and both
field-encryption values. Generate a 32-byte master salt with:

```bash
openssl rand -base64 32
```

Generate the local certificates with the supplied script, then start the application:

```bash
docker compose up --build --scale backend=2 --scale frontend=2
```

| Service | Default local URL |
|---|---|
| Frontend | `https://localhost:5173` |
| Backend | `https://localhost:8080/ExampleSecurity` |

The browser may initially warn about a locally generated certificate.

## Important environment variables

### Database, URLs and bootstrap

```properties
MONGODB_URI=mongodb+srv://example_security_app:URL_ENCODED_PASSWORD@YOUR_CLUSTER.mongodb.net/example_security?retryWrites=true&w=majority
REQUIRE_EXTERNAL_SERVICES=true
INITIAL_SUPER_USERNAME=super
INITIAL_SUPER_PASSWORD=replace-with-a-long-random-password
CORS_ALLOWED_ORIGINS=https://localhost:5173
FRONTEND_BASE_URL=https://localhost:5173
BACKEND_BASE_URL=https://localhost:8080/ExampleSecurity
VITE_API_BASE=https://localhost:8080/ExampleSecurity
```

`VITE_API_BASE` is compiled into the frontend, so rebuild the frontend after changing it.

### Sessions and encryption

```properties
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
SESSION_COLLECTION_NAME=spring_sessions
SESSION_TIMEOUT=30m
FIELD_CRYPTO_ENABLED=true
FIELD_CRYPTO_PASSPHRASE=replace-with-14-or-more-random-words
FIELD_CRYPTO_MASTER_SALT_B64=replace-with-openssl-output
```

Different local frontend/backend ports normally require `SameSite=none` with HTTPS. Production traffic through one public origin normally uses `SameSite=lax`.

### Optional Google login

```properties
GOOGLE_OAUTH_ENABLED=false
GOOGLE_OAUTH_CLIENT_ID=
GOOGLE_OAUTH_CLIENT_SECRET=
```

For local Google testing, create a Web application OAuth client and register exactly:

```text
https://localhost:8080/ExampleSecurity/login/oauth2/code/google
```

Set `GOOGLE_OAUTH_ENABLED=true` only after supplying both credentials. Blank credentials are permitted while it is disabled.

### Mail

Mailpit is not included. Configure Gmail SMTP or another external provider using
`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`,
`MAIL_SMTP_AUTH=true` and `MAIL_SMTP_STARTTLS=true`. Compose enables strict
startup validation and refuses local/placeholder external-service settings.

See [README-MongoDB-Authentication.md](README-MongoDB-Authentication.md) for
the Atlas user and network-access checklist.

## Selected API access rules

All paths are beneath `/ExampleSecurity` through the backend context path.

| Endpoint | Access |
|---|---|
| `GET /api/csrf` | Public |
| `POST /api/login` | Public |
| `POST /api/login/mfa` | Public while a challenge exists |
| `POST /api/logout` | Session logout flow |
| `GET /api/me` | Authenticated |
| `GET /api/oauth/google/config` | Public |
| `GET /api/oauth/google/login` | Public when Google is enabled |
| `GET /api/oauth/google/status` | Authenticated |
| `POST /api/oauth/google/link` | Authenticated and CSRF-protected |
| `DELETE /api/oauth/google/link` | Authenticated and CSRF-protected |
| `/api/office/**` | `OFFICE`, `OFFICE_ADMIN`, `HQ`, `SUPER` |
| `/api/office-admin/**` | `OFFICE_ADMIN`, `HQ`, `SUPER` |
| `/api/hq/**` | `HQ`, `SUPER` |
| `/api/admin/**` | `SUPER` |

Controller and service checks further restrict users to permitted patient and office data.

## Testing and builds

```bash
cd backend
mvn test
mvn package
```

```bash
cd frontend
npm ci
npm run build
```

For a full Docker rebuild:

```bash
docker compose down
docker compose up --build
```

## Production deployment

The production design uses two application nodes behind the Krystal load balancer with MongoDB Atlas providing the application database and shared sessions. Every backend node must use the same MongoDB connection, encryption material, session collection, OAuth credentials and externally visible URLs.

The proxy must preserve `Host`, `X-Forwarded-Host`, `X-Forwarded-Proto`, `X-Forwarded-For` and `X-Forwarded-Port`; this is required for OAuth callback generation and accurate auditing.

See [README-Krystal-HA-Production-Deployment.md](README-Krystal-HA-Production-Deployment.md) and [infrastructure/README.md](infrastructure/README.md).

## Operational cautions

- Never commit `env.list`, private keys, keystores or Google client secrets.
- Back up Atlas and test restoration rather than relying only on successful jobs.
- Protect encryption recovery material separately from database backups.
- Keep SUPER, SMTP and MongoDB credentials independent.
- Review audit records and throttling operationally.
- Run dependency, container and application security scanning before production.
- Treat clinical and identifying data as sensitive even when encrypted.

## Detailed documentation

- [Google OAuth login](README-Google-OAuth-Login.md)
- [Authy-compatible MFA](README-Authy-MFA.md)
- [Session cookie and CSRF design](README-Session-Cookie-CSRF.md)
- [Encrypted clinical fields](README-Encrypted-Clinical-Fields.md)
- [Production hardening](README-Production-Hardening.md)
- [Security hardening fixes](README-Security-Hardening-Fixes.md)
- [Security hardening round 2](README-Security-Hardening-Round-2.md)
- [Load-balanced sessions](README-Load-Balanced-Sessions.md)
- [Krystal high-availability deployment](README-Krystal-HA-Production-Deployment.md)
- [Patient portal appointments](README-Patient-Portal-Appointments.md)
- [Office appointment and clinical flow](README-Office-Appointment-Clinical-Flow.md)
- [HQ offices and SUPER context](README-HQ-Offices-And-Super-Context.md)

## Licence and suitability

Add the intended licence before distributing the project. Secure production operation also depends on infrastructure, secrets management, monitoring, maintenance and organizational controls outside the source code.

# Login Screens

<img src="Screenshot 2026-09-09 213019.png" alt="Login screen initial with password and OAuth2" width="900">
<img src="Screenshot 2026-09-09 213103.png" alt="Login screen Authy MFA" width="900">

# Super Screen

<img src="Screenshot 2026-09-09 213218.png" alt="Super screen top" width="900">
<img src="Screenshot 2026-09-09 213302.png" alt="Super screen bottom" width="900">

# HQ Screen

<img src="Screenshot 2026-09-04 193027.png" alt="HQ Screen top" width="900">
<img src="Screenshot 2026-09-04 193055.png" alt="HQ screen bottom" width="900">

# Office Admin Screen

<img src="Screenshot 2026-09-04 193159.png" alt="Office Admin screen top" width="900">
<img src="Screenshot 2026-09-04 193221.png" alt="Office Admin screen bottom" width="900">
