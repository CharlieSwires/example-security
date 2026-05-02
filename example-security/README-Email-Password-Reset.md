# Email verification and password reset

This version adds:

- A verified email address for each user.
- Proposed email addresses are not saved into the main `email` field until the user clicks the verification URL.
- SUPER users can propose or change an email address from the admin table.
- The login screen has a **Forgot password?** button.
- Forgot password asks for an email address.
- If the email does not match a verified stored email, the backend returns the same safe response and sends nothing.
- If the email matches a verified stored email, the backend sends a reset link.
- Every logged-in user can request a password change link using **Email me a change password link**.
- Password reset tokens are stored hashed, not as plain reset tokens.

## Local Docker email testing

Docker Compose includes Mailpit.

Run:

```bash
docker compose up --build
```

Open the app:

```text
http://localhost:5173
```

Open Mailpit:

```text
http://localhost:8025
```

Verification and reset emails appear in Mailpit.

## Important flows

### Proposed email

```text
SUPER enters proposed email
Backend creates verification token
Backend stores pendingEmail, token hash and expiry
Backend sends email to proposed email
User clicks verification link
Backend copies pendingEmail to email and marks emailVerified=true
```

### Forgot password

```text
User clicks Forgot password?
User enters email
Backend checks whether it matches a verified email
If it does not match, backend sends no email
If it matches, backend sends password reset link
User clicks reset link
User enters new password
Backend stores only new salt and hash
```

## Production SMTP

Set these environment variables:

```text
MAIL_HOST
MAIL_PORT
MAIL_USERNAME
MAIL_PASSWORD
MAIL_FROM
MAIL_SMTP_AUTH
MAIL_SMTP_STARTTLS
FRONTEND_BASE_URL
BACKEND_BASE_URL
```

Use HTTPS/TLS in production so passwords and reset tokens are encrypted in transit.
