# ExampleSecurity

> A Spring Boot, React and MongoDB reference application for secure multi-office ophthalmic workflows.

[ExampleSecurity](https://github.com/CharlieSwires/example-security) demonstrates authentication, Authy-compatible multi-factor authentication, server-side sessions, role- and office-based access control, encrypted clinical data, patient appointment records, email verification, password recovery, audit logging and containerised deployment.

The project is a working security and clinical-workflow foundation. It is suitable for development, demonstration and controlled testing, but it still requires a deployment-specific security and operational review before real patient data or public production use.

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Roles and access](#roles-and-access)
- [Clinical workflows](#clinical-workflows)
- [Security design](#security-design)
- [Running with Docker](#running-with-docker)
- [Configuration](#configuration)
- [API overview](#api-overview)
- [Testing](#testing)
- [Production checklist](#production-checklist)
- [Detailed documentation](#detailed-documentation)

## Features

### Authentication and account security

- Salted PBKDF2 password hashing; plaintext passwords are never stored.
- Spring Security authentication with server-side HTTP sessions.
- MongoDB-backed Spring Session storage, allowing multiple backend containers without sticky sessions.
- `HttpOnly`, `Secure` and configurable `SameSite` session cookies.
- CSRF protection for state-changing requests.
- Authy-compatible RFC 6238 TOTP multi-factor authentication.
- Single-use recovery codes stored only as SHA-256 hashes.
- Login throttling by username/IP and by IP, optionally persisted in MongoDB.
- Verified-email workflow and non-enumerating password reset.
- Protection against deleting or demoting the final `SUPER` account.

### Ophthalmic workflow

- Read-only patient appointment portal.
- Office clinician workflow for prescriptions and clinical notes.
- Office administration for appointments, accounts and practice transfers.
- HQ management across multiple offices.
- SUPER administration, office context selection, encryption-key rotation and audit review.
- Current prescriptions and historical prescriptions attached to dated clinical notes.
- Patient lookup and identity autofill when creating appointments.
- Patient telephone display for authorised office users.
- Backend pagination and scrollable lists, fixed at 50 records per page.

### Data protection and administration

- AES-256-GCM field encryption for clinical and identifying data.
- Deterministic HMAC lookup token for encrypted patient display names.
- One-shot, recorded rotation of the field-encryption passphrase.
- Structured security events stored in `security_audit_events`.
- SUPER-only, newest-first audit-log viewer showing every stored field.
- Security headers including HSTS, `X-Content-Type-Options`, frame restrictions, referrer policy and a starter Content Security Policy.
- Debug security logging disabled by default and sanitised when explicitly enabled.

## Architecture

| Layer | Technology and responsibility |
|---|---|
| Frontend | React 18, Vite and Bootstrap 5 |
| Frontend serving | Nginx production container |
| Backend | Java 17, Spring Boot 3.3, Spring Security and Spring Data MongoDB |
| Database | Local MongoDB 7 or MongoDB Atlas |
| Shared sessions | Spring Session MongoDB, stored in `spring_sessions` by default |
| Local email | Mailpit |
| Production email | Configurable SMTP provider |
| TLS and balancing | Nginx load balancer in the local Docker deployment |
| Packaging | Docker Compose with independently scalable frontend and backend services |

The browser connects to the Nginx load balancer over HTTPS. TLS is terminated there, and Nginx forwards requests to the internal frontend and backend containers. Authentication state is stored in MongoDB, so any backend replica can continue the session.

```text
Browser
  |
  | HTTPS
  v
Nginx load balancer
  |-- frontend replicas -> React static application
  `-- backend replicas  -> Spring Boot
                              |-- MongoDB: users, clinical data, sessions and audit events
                              `-- SMTP: verification and password-reset messages
```

## Roles and access

| Role | Access |
|---|---|
| `PATIENT` | Read-only access to the patient's own appointment documents |
| `OFFICE` | Clinician access to appointments belonging to the user's office |
| `OFFICE_ADMIN` | Office appointments, local account administration and authorised user transfers |
| `HQ` | Cross-office management and practice movement |
| `SUPER` | System-wide user, office, encryption and audit administration |

Legacy MongoDB values remain readable:

- `USER` is normalised to `PATIENT`.
- `DEVELOPER` is normalised to `OFFICE_ADMIN`.

Role checks are enforced by Spring Security. Patient and office data also use service-layer ownership checks based on the authenticated username and `officeId`; hiding a screen in React is not treated as an access-control boundary.

`OFFICE_ADMIN` accounts can administer only the permitted office-scoped roles. `HQ` and `SUPER` retain the wider administrative capabilities required for cross-office operations.

## Clinical workflows

### Patient portal

Patients can retrieve only their own appointment documents through a read-only endpoint. The portal shows:

- appointment date, time and type;
- clinic and clinician;
- current prescription;
- historical notes with date, subject and visit prescription;
- expandable note text.

The patient API exposes no appointment write or delete operation.

### Office clinician

An `OFFICE` user can open appointments belonging to the user's office and record:

- the current prescription;
- a clinical-note date and time;
- note subject;
- the prescription associated with that historical visit;
- clinical note text.

Clinical content is encrypted before it is written to MongoDB.

### Office administration

An `OFFICE_ADMIN` can:

- look up an existing patient by username;
- automatically populate the patient's encrypted display name and telephone number;
- create and manage appointments for the office;
- change the clinician, appointment date, time or type;
- remove an appointment;
- move a patient and their appointment documents to another practice;
- move a clinician or office user to another practice;
- administer permitted users belonging to that office.

The backend checks the office boundary for every operation.

### HQ and practice movement

`HQ` and `SUPER` can create, list and remove office accounts. They can also move the users and appointment documents associated with one practice to another:

- `PATIENT` users;
- `OFFICE` and `OFFICE_ADMIN` users;
- appointment documents.

The old office is not deleted automatically. The move should be reviewed before the old office account is deleted manually.

### SUPER administration

The `SUPER` screen provides:

- system-wide user and role administration;
- office selection when entering office-scoped screens;
- one-shot field-encryption key rotation;
- security audit-log review.

## Security design

### Session and CSRF flow

1. The browser submits the username and password to `POST /ExampleSecurity/api/login` over HTTPS.
2. The backend verifies the salted PBKDF2 hash.
3. If MFA is disabled, Spring Security creates the authenticated server-side session.
4. If MFA is enabled, the backend creates only a temporary challenge; full authentication waits for the TOTP or recovery code.
5. The browser receives an `HttpOnly` session cookie and includes credentials on API calls.
6. React obtains a CSRF token from `/ExampleSecurity/api/csrf` and sends it as `X-XSRF-TOKEN` for state-changing requests.

Passwords and session identifiers are not stored in browser JavaScript. React retains only a small display object containing the username and roles.

### Authy-compatible MFA

The MFA implementation uses the standard `otpauth://` TOTP format and therefore works with Authy and other compatible authenticator applications.

#### Enrolment

1. Log in and open **Multi-factor authentication (Authy)**.
2. Select **Set up Authy**.
3. Scan the QR code or enter the displayed Base32 secret manually.
4. Enter the current six-digit code to confirm enrolment.
5. Store the ten recovery codes securely.

#### MFA controls

| Control | Behaviour |
|---|---|
| Algorithm | RFC 6238 TOTP using HMAC-SHA1 |
| Code | Six digits, changing every 30 seconds |
| Clock tolerance | Current time step plus or minus one step |
| Login challenge | Five-minute lifetime and at most five verification attempts |
| Setup challenge | Ten-minute lifetime in the server-side session |
| TOTP secret | AES-256-GCM encrypted at rest |
| Recovery codes | 80 random bits each; SHA-256 hashes stored |
| Disabling MFA | Requires a current TOTP or unused recovery code |

The authenticated Spring Security context is not established until MFA succeeds.

### Encrypted fields

The application encrypts sensitive values using `FieldCryptoService` before MongoDB storage.

#### Encrypted at rest

- patient and user display names;
- patient and user telephone numbers;
- office addresses and telephone numbers;
- clinic and clinician names;
- current and historical prescriptions;
- clinical note subjects and note text;
- TOTP secrets.

#### Deliberately searchable plaintext

- username;
- `officeId`;
- appointment date, time and type;
- roles;
- verified email address;
- non-clinical note date/time used for ordering.

Dates and routing identifiers remain plaintext so MongoDB can sort, filter and enforce ownership efficiently. They must not contain clinical narrative.

New appointment records store an encrypted patient display name together with a deterministic HMAC-SHA256 lookup token. The deprecated plaintext model field remains only for compatibility with older development records.

### Field-encryption key rotation

The SUPER key-rotation operation requires the currently configured passphrase, a new passphrase and confirmation. The backend records a deterministic old-key-to-new-key rotation identifier in `crypto_rotation_records` and refuses an attempt to repeat the same rotation.

After a successful rotation, update `FIELD_CRYPTO_PASSPHRASE` on every backend instance and restart them. Never change the live passphrase or master salt without completing a migration, because previously encrypted fields would otherwise become unreadable.

### Login throttling

Failed login attempts are tracked for both username/IP and IP alone. Defaults are:

| Rule | Default |
|---|---:|
| Username and IP failures | 5 within 15 minutes |
| All failures from one IP | 25 within 15 minutes |
| Lockout | 15 minutes |

Blocked login attempts return `429 Too Many Requests` with a `Retry-After` header. Lower failure counts return `401 Unauthorized`. Persistent throttling uses MongoDB so counters survive restarts and apply across backend replicas.

### Email verification and password reset

- A proposed email address is kept separate until the recipient follows its verification link.
- Password-reset requests return the same safe response whether or not the supplied address exists.
- Reset and verification tokens are stored as hashes rather than plaintext.
- Logged-in users can request an emailed password-change link.
- Local messages are captured by Mailpit; production delivery uses configured SMTP credentials.

### Audit log

Structured security events are stored in the `security_audit_events` collection when `SECURITY_AUDIT_PERSIST=true`.

Examples include login success, failure and throttling; logout; password reset; email verification; account administration; role changes; access denial; and CSRF denial.

The SUPER-only audit screen:

- uses the backend-protected `GET /api/admin/audit-events` endpoint;
- sorts by timestamp descending and then MongoDB ID descending;
- returns 50 events per page;
- displays the ID, timestamp, event type, actor, target, client IP, user agent, success flag, reason and details map;
- supports refresh, sticky headings and scrolling without truncating long values.

Passwords, password hashes, reset or verification tokens, cookie values, complete session IDs, SMTP passwords and database credentials must never be written to the audit log.

## Running with Docker

### Prerequisites

- Docker Engine or Docker Desktop with Docker Compose;
- local certificate files generated by the supplied script;
- an `env.list` created from `env.list.example`.

### Prepare local configuration

```bash
cp env.list.example env.list
```

Review every placeholder in `env.list`, particularly the bootstrap password and field-encryption passphrase. Do not commit this file.

Generate the local certificates if they are not already present:

```bash
./scripts_generate_local_certs.sh
```

### Build and start

One frontend and one backend:

```bash
docker compose up --build
```

Two frontend and two backend replicas:

```bash
docker compose up --build --scale frontend=2 --scale backend=2
```

### Local addresses

| Service | Address |
|---|---|
| Application | `https://localhost:5173` |
| Backend API | `https://localhost:8080/ExampleSecurity` |
| Mailpit | `http://localhost:8025` |
| Local MongoDB | `localhost:27017` |

Include `https://` when opening the application. A locally generated certificate may produce a browser warning until it is trusted by the operating system.

### Clean frontend rebuild

The frontend has its own `.dockerignore` so Windows `node_modules` and `dist` directories cannot overwrite Linux dependencies during the container build.

If a stale frontend build persists:

```bash
docker compose down --remove-orphans
docker compose build --no-cache frontend
docker compose up --build --force-recreate
```

Use `docker compose down --volumes` only when you intentionally want to remove the local MongoDB volume as well.

### Useful logs

```bash
docker compose ps
docker compose logs load-balancer --tail=100
docker compose logs frontend --tail=100
docker compose logs backend --tail=200
```

## Running without Docker

### Backend

```bash
cd backend
mvn clean test
mvn spring-boot:run
```

For a MongoDB instance on the development machine:

```env
MONGODB_URI=mongodb://localhost:27017/example_security
```

Inside Docker, `localhost` refers to the current container; use the Compose service name `mongo` to reach the included database.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend API base URL, backend CORS origins and cookie settings must describe the same development topology.

## Configuration

### Core environment variables

```env
MONGODB_URI=mongodb://mongo:27017/example_security

INITIAL_SUPER_USERNAME=super
INITIAL_SUPER_PASSWORD=replace-with-a-strong-bootstrap-password

CORS_ALLOWED_ORIGINS=https://localhost:5173
FRONTEND_BASE_URL=https://localhost:5173
BACKEND_BASE_URL=https://localhost:8080/ExampleSecurity

SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=none
SESSION_COLLECTION_NAME=spring_sessions
SESSION_TIMEOUT=30m

LOGIN_MAX_USER_IP_FAILURES=5
LOGIN_MAX_IP_FAILURES=25
LOGIN_FAILURE_WINDOW_MINUTES=15
LOGIN_LOCKOUT_MINUTES=15
LOGIN_THROTTLE_PERSISTENT=true

SECURITY_AUDIT_PERSIST=true
SECURITY_DEBUG_REQUEST_LOGGING=false

FIELD_CRYPTO_ENABLED=true
FIELD_CRYPTO_PASSPHRASE=replace-with-fourteen-or-more-random-words
FIELD_CRYPTO_MASTER_SALT_B64=replace-with-your-deployment-salt
```

Use `SESSION_COOKIE_SAME_SITE=lax` when the deployment is same-site and it works with the required flows. `SameSite=None` requires `Secure=true`.

### Local Mailpit

```env
MAIL_HOST=mailpit
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@example-security.local
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
MAIL_DEBUG=false
```

For production, configure the equivalent values for the chosen SMTP provider and keep its credentials outside source control.

### MongoDB Atlas

To use Atlas instead of the included MongoDB container, set `MONGODB_URI` to the deployment's Atlas connection string and remove or ignore the local `mongo` service. Restrict network access and give the application database user only the permissions it requires.

## API overview

All paths below are relative to the backend context path `/ExampleSecurity`.

### Authentication and account recovery

| Method | Endpoint | Access |
|---|---|---|
| `POST` | `/api/login` | Public |
| `POST` | `/api/login/mfa` | Temporary password-authenticated MFA challenge |
| `POST` | `/api/logout` | Current session |
| `GET` | `/api/csrf` | Public CSRF bootstrap |
| `GET` | `/api/me` | Authenticated |
| `GET` | `/api/mfa/status` | Authenticated |
| `POST` | `/api/mfa/setup` | Authenticated |
| `POST` | `/api/mfa/enable` | Authenticated |
| `POST` | `/api/mfa/disable` | Authenticated |
| `POST` | `/api/password/forgot` | Public, non-enumerating |
| `POST` | `/api/password/reset` | Public reset-token flow |
| `POST` | `/api/password/change-link` | Authenticated |
| `GET` | `/api/email/verify` | Public verification-token flow |

### Clinical and office operations

| Method | Endpoint | Principal access |
|---|---|---|
| `GET` | `/api/patient/appointments` | Patient's own documents |
| `GET` | `/api/office/appointments` | Office-scoped clinician access |
| `PUT` | `/api/office/appointments/{id}/clinical` | Office-scoped clinician update |
| `GET` | `/api/office-admin/appointments` | Office administration |
| `POST` | `/api/office-admin/appointments` | Office administration |
| `PUT` | `/api/office-admin/appointments/{id}/admin` | Office administration |
| `DELETE` | `/api/office-admin/appointments/{id}` | Office administration |
| `GET` | `/api/office-admin/patients/{username}` | Authorised patient lookup |
| `PUT` | `/api/office-admin/patients/{username}/office` | Authorised practice transfer |
| `PUT` | `/api/office-admin/clinicians/{username}/office` | Authorised practice transfer |
| `GET` | `/api/hq/offices` | `HQ`, `SUPER` |
| `POST` | `/api/hq/offices` | `HQ`, `SUPER` |
| `DELETE` | `/api/hq/offices/{officeId}` | `HQ`, `SUPER` |
| `POST` | `/api/hq/offices/move-patients` | `HQ`, `SUPER` |

### Administration

| Method | Endpoint | Access |
|---|---|---|
| `GET/POST` | `/api/admin/users` | `SUPER` |
| `PUT/DELETE` | `/api/admin/users/{username}/...` | `SUPER` |
| `POST` | `/api/admin/crypto/rotate` | `SUPER` |
| `GET` | `/api/admin/audit-events` | `SUPER` |

List endpoints use zero-based `page` parameters and return at most 50 records in the common `PageResponse` format.

## Testing

Run the backend tests with:

```bash
cd backend
mvn clean test
```

Build the frontend with:

```bash
cd frontend
npm install
npm run build
```

The backend suite covers PBKDF2 hashing, login throttling, authenticated sessions, role restrictions, CSRF enforcement, logout, security headers and SUPER-only audit access. The audit test also verifies that every stored audit field is returned.

Useful manual checks after logging in include `GET /ExampleSecurity/api/me` and the protected administrative endpoints. A non-SUPER account must receive `403 Forbidden` from `/api/admin/**` regardless of what the frontend displays.

## Secret-handling rules

Never commit:

- `env.list` or `.env` files;
- database URIs containing credentials;
- SMTP or Gmail app passwords;
- the field-encryption passphrase or live master salt;
- keystores, private keys or generated production certificates;
- reset, verification or MFA secrets;
- production backups containing patient data.

If a secret has ever entered Git history, screenshots, logs or a shared archive, treat it as compromised and rotate it. For production, use deployment secrets, an encrypted secret store or another controlled mechanism rather than a committed environment file.

## Production checklist

Before processing real patient information:

- replace development certificates with trusted certificates for the real domain;
- use unique bootstrap credentials and rotate them immediately;
- store secrets outside Git and restrict who can access them;
- select and document a secure 14-word-or-longer field-encryption passphrase;
- restrict MongoDB network access and use a least-privilege database account;
- protect MongoDB with authentication even on a private Docker network;
- remove the public MongoDB port when remote host access is unnecessary;
- configure encrypted, off-host backups and regularly test restoration;
- define retention and review procedures for audit events;
- review CORS, cookie and CSRF settings for the production domains;
- review and tighten the Content Security Policy;
- add health checks, monitoring, alerting and dependency scanning;
- document key rotation and disaster recovery;
- complete a clinical-data protection, privacy and threat assessment;
- conduct application and infrastructure security testing.

## Detailed documentation

The root README summarises the current system. The following documents retain implementation history and deeper operational detail:

### Authentication and security

- [Authy-compatible MFA](https://github.com/CharlieSwires/example-security/blob/master/README-Authy-MFA.md)
- [Session cookies and CSRF](https://github.com/CharlieSwires/example-security/blob/master/README-Session-Cookie-CSRF.md)
- [Login throttling](https://github.com/CharlieSwires/example-security/blob/master/README-Login-Throttling.md)
- [Email verification and password reset](https://github.com/CharlieSwires/example-security/blob/master/README-Email-Password-Reset.md)
- [Production hardening](https://github.com/CharlieSwires/example-security/blob/master/README-Production-Hardening.md)
- [Debug removal and security tests](https://github.com/CharlieSwires/example-security/blob/master/README-Remove-Debug-And-Security-Tests.md)
- [SUPER audit log](https://github.com/CharlieSwires/example-security/blob/master/README-Super-Audit-Log.md)

### Encryption and clinical data

- [Encrypted clinical and identifying fields](https://github.com/CharlieSwires/example-security/blob/master/README-Encrypted-Clinical-Fields.md)
- [Encrypted patient display names](https://github.com/CharlieSwires/example-security/blob/master/README-Patient-DisplayName-Encrypted.md)
- [Current and historical prescriptions](https://github.com/CharlieSwires/example-security/blob/master/README-Current-And-Historical-Prescriptions.md)
- [Patient telephone numbers in appointment lists](https://github.com/CharlieSwires/example-security/blob/master/README-Patient-Telephone-In-Appointment-Lists.md)
- [HQ clinician moves and encryption-key rotation](https://github.com/CharlieSwires/example-security/blob/master/README-HQ-Clinician-Moves-And-Key-Rotation.md)

### Roles and clinical workflows

- [Patient, office and HQ roles](https://github.com/CharlieSwires/example-security/blob/master/README-Patient-Office-Roles.md)
- [Ophthalmic screens](https://github.com/CharlieSwires/example-security/blob/master/README-Ophthalmic-Screens.md)
- [Patient portal appointments](https://github.com/CharlieSwires/example-security/blob/master/README-Patient-Portal-Appointments.md)
- [Office appointment and clinical flow](https://github.com/CharlieSwires/example-security/blob/master/README-Office-Appointment-Clinical-Flow.md)
- [Patient lookup and autofill](https://github.com/CharlieSwires/example-security/blob/master/README-Patient-Lookup-Autofill.md)
- [Office administration and HQ practice movement](https://github.com/CharlieSwires/example-security/blob/master/README-Office-Admin-HQ-Moves.md)
- [HQ offices and SUPER office context](https://github.com/CharlieSwires/example-security/blob/master/README-HQ-Offices-And-Super-Context.md)
- [Pagination and scrollable lists](https://github.com/CharlieSwires/example-security/blob/master/README-Pagination-Scrollable-Lists.md)

### Deployment

- [Docker usage](https://github.com/CharlieSwires/example-security/blob/master/README-Docker.md)
- [Load-balanced MongoDB sessions](https://github.com/CharlieSwires/example-security/blob/master/README-Load-Balanced-Sessions.md)
- [HTTPS load-balancer setup](https://github.com/CharlieSwires/example-security/blob/master/README-HTTPS-Load-Balancer-Fix.md)
- [Frontend Docker build isolation](https://github.com/CharlieSwires/example-security/blob/master/README-Docker-Frontend-Build-Fix.md)

## Project status

| Use | Status |
|---|---|
| Learning and portfolio demonstration | Suitable |
| Local/private demonstration | Suitable with local configuration |
| Controlled internal testing | Suitable after environment and access review |
| Public production with real patient data | Requires the production checklist and formal security review |

ExampleSecurity is intentionally transparent about that boundary: it demonstrates a strong design, but secure production operation also depends on hosting, secrets, monitoring, backups, maintenance and organisational controls.

# Super Screen

<img src="Screenshot 2026-09-04 192815.png" alt="Super screen top" width="900">
<img src="Screenshot 2026-09-04 192922.png" alt="Super screen bottom" width="900">

# HQ Screen

<img src="Screenshot 2026-09-04 193027.png" alt="HQ Screen top" width="900">
<img src="Screenshot 2026-09-04 193055.png" alt="HQ screen bottom" width="900">

# Office Admin Screen

<img src="Screenshot 2026-09-04 193159.png" alt="Office Admin screen top" width="900">
<img src="Screenshot 2026-09-04 193221.png" alt="Office Admin screen bottom" width="900">
