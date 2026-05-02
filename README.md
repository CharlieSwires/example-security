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

```bash
cd backend
```

Set your MongoDB Atlas URI.

### Windows PowerShell

```powershell
$env:MONGODB_URI="mongodb+srv://USERNAME:PASSWORD@cluster.example.mongodb.net/example_security?retryWrites=true&w=majority"
$env:INITIAL_SUPER_USERNAME="super"
$env:INITIAL_SUPER_PASSWORD="ChangeThisPassword123!"
```

### Git Bash / Linux

```bash
export MONGODB_URI='mongodb+srv://USERNAME:PASSWORD@cluster.example.mongodb.net/example_security?retryWrites=true&w=majority'
export INITIAL_SUPER_USERNAME='super'
export INITIAL_SUPER_PASSWORD='ChangeThisPassword123!'
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
