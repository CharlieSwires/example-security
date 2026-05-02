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

###env.list file
```text
MAIL_HOST=
MAIL_PORT=
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS=true
FRONTEND_BASE_URL=http://localhost:5173
BACKEND_BASE_URL=http://localhost:8080/ExampleSecurity
INITIAL_SUPER_USERNAME=super
INITIAL_SUPER_PASSWORD=ChangeThisPassword123!
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

###application.yml file
Set your MongoDB Atlas URI.
```text
server:
  port: 8080
  servlet:
    context-path: /ExampleSecurity
  ssl:
    enabled: ${SSL_ENABLED:false}
    key-store: ${SSL_KEYSTORE:classpath:keystore.p12}
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeit}
    key-store-type: PKCS12
    key-alias: ${SSL_KEY_ALIAS:examplesecurity}

spring:
  data:
    mongodb:
      uri: mongodb+srv://DBUSER:PASSWORD@cluster0.icebq.mongodb.net/example_security
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
  frontend-base-url: ${FRONTEND_BASE_URL:http://localhost:5173}
  backend-base-url: ${BACKEND_BASE_URL:http://localhost:8080/ExampleSecurity}
  mail:
    from: ${MAIL_FROM:no-reply@example-security.local}
  initial-super:
    username: super
    password: ChangeThisPassword123!
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
