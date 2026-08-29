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

## New endpoints

- `GET /api/mfa/status` - authenticated; reports whether MFA is enabled.
- `POST /api/mfa/setup` - authenticated; starts enrolment and returns QR/manual secret.
- `POST /api/mfa/enable` - authenticated; confirms enrolment with an Authy code.
- `POST /api/mfa/disable` - authenticated; disables MFA after code verification.
- `POST /api/login/mfa` - permitted before full authentication; completes a password-authenticated MFA challenge.

## Build note

The backend POM now includes ZXing 3.5.3. Run your normal Maven build so Maven downloads those dependencies if they are not already cached.
