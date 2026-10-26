# Copyright

Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This is proprietary software. No permission is granted to copy, modify, distribute, sublicense or use this software except under a written licence issued by Charlie's Tenacity Ltd. Third-party components remain subject to their respective licences.

# Authy-compatible MFA

This copy-over adds RFC 6238 TOTP multi-factor authentication designed for Authy and other standards-based authenticator apps.

## Enrolment

1. Log in normally.
2. Open **Multi-factor authentication (Authy)** on the dashboard.
3. Click **Set up Authy**.
4. In Authy, add an account and scan the QR code. If scanning is unavailable, type the displayed Base32 secret manually.
5. Enter Authy's current six-digit code and click **Enable MFA**.
6. Save the displayed single-use recovery codes securely.

## Login behaviour

For an MFA-enabled account, successful password authentication creates only a temporary server-side MFA challenge. The application does not put an authenticated Spring Security context into the session until the Authy TOTP code (or a valid unused recovery code) has been verified.

## Security details

- TOTP: RFC 6238, HMAC-SHA1, 6 digits, 30-second period, +/-1 time-step clock tolerance.
- TOTP secret: encrypted at rest using the existing `FieldCryptoService` AES-256-GCM field encryption.
- Recovery codes: 80 random bits each; only SHA-256 hashes are stored in MongoDB.
- Login MFA challenge: stored in the server-side HTTP session, 5-minute lifetime, maximum 5 verification attempts.
- MFA setup secret: stored only in the server-side HTTP session until successful confirmation; 10-minute setup lifetime.
- QR rendering: ZXing 3.5.3 (`core` and `javase`).
- Disable MFA: requires a current Authy TOTP code or an unused recovery code.

## BST/GMT and clock synchronisation

TOTP does not use British local time. RFC 6238 calculates the code from UTC
Unix epoch seconds, so the normal BST-to-GMT clock change must not invalidate a
code. The backend now receives an explicit `Clock.systemUTC()` and its Docker
runtime is pinned to `Etc/UTC`. Regression tests cover the October 2026 UK clock
change.

The October 2026 incident was confirmed **not** to have been caused by the
BST-to-GMT transition. Authy held an older enrolment secret which no longer
matched the encrypted TOTP secret stored for the application user. Re-enrolling
the user replaced the stale Authy entry with the current secret and restored
verification. A TOTP secret does not normally expire merely because of its
age, and `mfaEnrolledAt` is audit information rather than an expiry time.

A secret mismatch can follow an MFA reset, user recreation, database restore
or migration in which Authy and the application's user record cease to refer
to the same enrolment. If the clocks agree but every code is rejected, disable
and re-enrol MFA (using a recovery code or an authorised account-recovery
process if necessary). Remove or rename the obsolete Authy entry so that it is
not accidentally used again.

Clock drift remains a separate possible cause. A one-hour tolerance has
deliberately **not** been added because that would accept 241 separate
30-second code windows and materially weaken MFA.

Check the clocks before resetting MFA:

```bash
date -u
docker compose exec backend date -u
docker compose exec backend java -XshowSettings:properties -version 2>&1 | grep user.timezone
```

The first two UTC times should agree to within a few seconds and the JVM should
report `user.timezone = UTC`. On Windows, Docker Desktop or its WSL VM can retain
a stale clock across sleep or a daylight-saving transition. Quit and restart
Docker Desktop (or run `wsl --shutdown` and restart it), then rebuild/recreate
the backend:

```bash
docker compose down
docker compose up --build -d
```

Also ensure the phone running Authy has automatic date/time and automatic time
zone enabled. A recovery code can be used once if login is required while the
clock is being corrected or before MFA is safely re-enrolled.

## New endpoints

- `GET /api/mfa/status` - authenticated; reports whether MFA is enabled.
- `POST /api/mfa/setup` - authenticated; starts enrolment and returns QR/manual secret.
- `POST /api/mfa/enable` - authenticated; confirms enrolment with an Authy code.
- `POST /api/mfa/disable` - authenticated; disables MFA after code verification.
- `POST /api/login/mfa` - permitted before full authentication; completes a password-authenticated MFA challenge.

## Build note

The backend POM now includes ZXing 3.5.3. Run your normal Maven build so Maven downloads those dependencies if they are not already cached.
