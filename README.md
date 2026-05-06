# ExampleSecurity

> A Spring Boot + React security starter application showing the foundations of a production-style web app.

ExampleSecurity is a secure login and user-management starter project. It demonstrates how a modern web application can handle authentication, sessions, roles, password reset, email verification, CSRF protection, and admin user management.

It is **not a finished business application**. It is a secure foundation that can be built on before adding real business features.

---

## At a glance

```text
| Area | What it uses |
|---|---|
| Backend | Java 17, Spring Boot, Spring Security |
| Frontend | React, Vite, Bootstrap 5 |
| Database | MongoDB or MongoDB Atlas |
| Local email testing | Mailpit |
| Authentication | Server-side Spring session |
| Password storage | Salted PBKDF2 hashes |
| Browser session | `HttpOnly`, `Secure`, `SameSite` cookie |
| Admin control | `SUPER` role |
| Security extras | CSRF protection, login throttling, audit logging foundations |
```
---

## What this app does

The application has two main parts:

```text
- a **React frontend**, which users see in the browser
- a **Spring Boot backend**, which checks passwords, manages sessions, protects endpoints, stores users, and 
```
sends emails

Users can log in with a username and password. The backend checks the password, creates a server-side session, and gives the browser a secure `JSESSIONID` cookie.

The browser does **not** store the password. JavaScript does **not** read the session cookie because it is `HttpOnly`.

---

## User roles

The app has three roles.

```text
| Role | Meaning |
|---|---|
| `USER` | Can access the user endpoint |
| `DEVELOPER` | Can access the developer endpoint |
| `SUPER` | Administrator role; can access everything |
```
The `SUPER` user can access the admin screen to manage users and roles.

---

## Main security design

This version uses a session-cookie and CSRF design:

```text
POST /api/login over HTTPS
server verifies PBKDF2 password hash
server creates server-side session
browser receives HttpOnly Secure SameSite JSESSIONID cookie
React calls the API using credentials: include
CSRF protection is enabled
password is never stored in browser JavaScript
```

This is more production-like than storing credentials in the browser or using Basic Auth from the frontend.

Technology stack
Backend

```text
Java 17
Spring Boot 3.3.x
Spring Security
Spring Data MongoDB
Java Mail / Spring Mail
PBKDF2 password hashing
Server-side sessions
CSRF protection
Login throttling
Audit logging foundations
```

Frontend


```text
React
Vite
Bootstrap 5
Nginx container for the built frontend
HTTPS local frontend support
```
Database and mail

```text
MongoDB or MongoDB Atlas
Mailpit for local email testing
Gmail SMTP or another SMTP provider for real email delivery
```
Endpoint access rules
Endpoint	Access

```text
POST /ExampleSecurity/api/login	Public
POST /ExampleSecurity/api/logout	Public/session logout
GET /ExampleSecurity/api/csrf	Public CSRF token endpoint
GET /ExampleSecurity/api/me	Logged-in users
GET /ExampleSecurity/user	USER or SUPER
GET /ExampleSecurity/developer	DEVELOPER or SUPER
/ExampleSecurity/api/admin/**	SUPER only
POST /ExampleSecurity/api/password/forgot	Public
POST /ExampleSecurity/api/password/reset	Public reset-token flow
GET /ExampleSecurity/api/email/verify	Public verification-token flow


SUPER can access all protected areas.
```

Current features:

```text
Authentication
Login with username and password
Logout
Server-side session creation
HttpOnly session cookie
Secure cookie support for HTTPS
SameSite cookie configuration
/api/me endpoint to check the logged-in user
Password security
Passwords are not stored directly
Passwords are salted
Passwords are hashed using PBKDF2
Password comparison is done using hashes
Password reset flow uses email links
User and role management
```

The SUPER admin screen can:

```text
create users
delete users
update roles
update a user password
manage USER, DEVELOPER, and SUPER roles
```
```text
The app should prevent unsafe admin behaviour such as removing the final SUPER user if that hardening code is present in your current branch.

Email verification

A user email address is not trusted immediately.

The app sends a verification link to the proposed email address. The email is only saved as verified after the user clicks the verification URL.

Forgot password and reset password

The login screen includes a forgot-password flow.

The user enters their email address. If it matches a verified email address, the app sends a password-reset link. If it does not match, the app should respond generically and not reveal whether the email exists.

CSRF protection

The backend uses CSRF protection for authenticated state-changing requests.

React fetches a CSRF token and sends it using the X-XSRF-TOKEN header where required.

Login throttling

The backend includes login throttling to slow brute-force password guessing.

Typical defaults:

Rule	Default
Same username + IP failures	5 failed attempts within 15 minutes
Same IP failures	25 failed attempts within 15 minutes
Lockout duration	15 minutes

The exact values are configurable by environment variables.

Audit logging

The app includes foundations for security audit logging.
```

Useful events include:

```text
LOGIN_SUCCESS
LOGIN_FAILURE
LOGIN_THROTTLED
LOGOUT
PASSWORD_RESET_REQUESTED
PASSWORD_RESET_COMPLETED
EMAIL_VERIFICATION_REQUESTED
EMAIL_VERIFIED
USER_CREATED
USER_DELETED
USER_ROLES_CHANGED
ACCESS_DENIED
CSRF_DENIED
```
Do not log:

```text
passwords
password hashes
reset tokens
verification tokens
full cookies
full session IDs
SMTP passwords
MongoDB credentials
Local development URLs
```

With the HTTPS Docker setup:

```text
Service	URL
Frontend	https://localhost:5173
Backend	https://localhost:8080/ExampleSecurity
Mailpit	http://localhost:8025
```
The browser may warn about local self-signed certificates. That is expected for local development.

Environment configuration

Create an env.list file for local Docker use.

Do not commit env.list.

Example:

# MongoDB

```text
MONGODB_URI=mongodb://mongo:27017/example_security
```
# Initial bootstrap SUPER account

```text
INITIAL_SUPER_USERNAME=super
INITIAL_SUPER_PASSWORD=ChangeThisPassword123!
```
# HTTPS backend

```text
SSL_ENABLED=true
SSL_KEYSTORE_PASSWORD=changeit
SSL_KEY_ALIAS=examplesecurity
```
# Session cookie

```text
SESSION_COOKIE_SECURE=true
SESSION_COOKIE_SAME_SITE=lax
SESSION_TIMEOUT=30m
```
# CORS / URLs

```text
CORS_ALLOWED_ORIGINS=https://localhost:5173
FRONTEND_BASE_URL=https://localhost:5173
BACKEND_BASE_URL=https://localhost:8080/ExampleSecurity
```
# Local Mailpit defaults

```text
MAIL_HOST=mailpit
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_FROM=no-reply@example-security.local
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
```
# Login throttling

```text
LOGIN_MAX_USER_IP_FAILURES=5
LOGIN_MAX_IP_FAILURES=25
LOGIN_FAILURE_WINDOW_MINUTES=15
LOGIN_LOCKOUT_MINUTES=15
LOGIN_THROTTLE_PERSISTENT=true
```
# Audit / debugging

```text
SECURITY_AUDIT_PERSIST=true
SECURITY_DEBUG_REQUEST_LOGGING=false
```
For real production use, do not use env.list as your long-term secret strategy. Use Docker secrets, Kubernetes secrets, a cloud secret manager, or another proper secret-management system.

Important secret warning

Do not commit any of these to Git:

```text
*.p12
*.jks
*.key
*.crt
*.pem
env.list
.env
```
Gmail app passwords
MongoDB URIs with credentials
SMTP passwords

If any keystore, private key, Gmail app password, MongoDB URI, or secret has ever been pushed to GitHub, treat it as compromised and rotate it.

Local certificates

For local HTTPS, the backend uses a PKCS12 keystore.

Example backend keystore generation:

```text
keytool -genkeypair \
  -alias examplesecurity \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore backend/src/main/resources/keystore.p12 \
  -validity 3650 \
  -storepass changeit \
  -keypass changeit \
  -dname "CN=localhost, OU=Dev, O=ExampleSecurity, L=Local, ST=Local, C=GB"
```
For Nginx frontend HTTPS, Nginx needs certificate and key files, not a .p12 file directly.

If you have a local keystore.p12, you can extract the certificate:

```text

openssl pkcs12 \
  -in frontend/certs/keystore.p12 \
  -clcerts \
  -nokeys \
  -out frontend/certs/keystore.crt
```
And extract the private key:

```text

openssl pkcs12 \
  -in frontend/certs/keystore.p12 \
  -nocerts \
  -nodes \
  -out frontend/certs/keystore.key
```

Do not commit these generated files.

Docker

Build and run:

```text
docker compose up --build
```
Or rebuild cleanly:

```text
docker compose down
docker compose build --no-cache backend frontend
docker compose up
```

Then open:

```text
https://localhost:5173
```

Mailpit is available at:

```text
http://localhost:8025
```

Running backend locally without Docker

From the backend folder:

```bash
cd backend
mvn clean test
mvn spring-boot:run
```

If running without Docker, make sure your MongoDB URI points to a MongoDB instance that is actually reachable from your machine.

For local MongoDB outside Docker:

```text
MONGODB_URI=mongodb://localhost:27017/example_security
```
For Docker Compose local MongoDB from inside the backend container:

```text
MONGODB_URI=mongodb://mongo:27017/example_security
```
Inside a Docker container, localhost means the container itself, not the MongoDB container.

Running frontend locally without Docker

From the frontend folder:

```bash
cd frontend
npm install
npm run dev
```

For the full secure cookie flow, the frontend and backend URLs must match the configured HTTPS, CORS, and cookie settings.

Initial login

The backend creates an initial SUPER account if it does not already exist.

Default local values:

Field	Value

```text
Username	super
Password	ChangeThisPassword123!
```

Change this before using the app beyond local testing.

For any real deployment, do not use a default production password. Use a one-time bootstrap secret or force password rotation immediately.

Useful manual checks

After logging in as super, this should return your logged-in user and roles:

```text
fetch('https://localhost:8080/ExampleSecurity/api/me', {
  credentials: 'include'
})
.then(async r => {
  console.log(r.status);
  console.log(await r.text());
});
```
Expected:

```text
200
{"username":"super","roles":["SUPER"]}
```
The admin users endpoint should work for SUPER:

```text
fetch('https://localhost:8080/ExampleSecurity/api/admin/users', {
  credentials: 'include'
})
.then(async r => {
  console.log(r.status);
  console.log(await r.text());
});
```
Expected:

200

For a user without SUPER, it should return:

403
Tests

Run backend tests:

```bash
cd backend
mvn clean test
```

The test suite includes checks for:

```text
password hashing
login throttling
unauthenticated /api/me returns 401
USER cannot access admin
DEVELOPER cannot access admin
SUPER can access admin
CSRF blocks unsafe admin requests without a token
login creates a server-side session
logout works
security headers are present
Security status
```
This app is a strong learning, demo, and staging foundation for a secure local-login web application.

It is not automatically production-ready just because it runs. Before public production use, still review:

```text
real TLS certificates
secret management
rotated secrets
MongoDB Atlas IP restrictions
least-privilege database user
backups
monitoring
audit log retention
container hardening
dependency scanning
production CORS settings
CSRF settings for the real domain
security review
```

A fair description is:

```text
Use case	Status
Private demo	Suitable
Portfolio project	Suitable
Internal staging	Suitable after config review
Public production	Needs final deployment/security review
```
Project purpose

In plain English:

This app is a secure login and user-management system. It lets people sign in, gives different permissions to different kinds of users, lets an administrator manage accounts, verifies emails, resets passwords, and protects the system against common web attacks.

It is the foundation you would build on before adding the actual business features of a real website.