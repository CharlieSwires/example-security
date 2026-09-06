# Security hardening: validation, recovery and deployment

This update addresses the September 2026 security review while retaining Authy
TOTP, SUPER-only audit access and field-encryption salt rotation.

## Request and field limits

The load balancer rejects request bodies over 256 KiB. A backend filter provides
the same check for requests with a declared content length if the backend is
accidentally reached directly. Jakarta Bean Validation now constrains usernames,
email addresses, office identifiers, names, telephone numbers, passwords,
appointment fields, prescriptions and clinical notes. An appointment can hold no
more than 250 embedded notes.

## Password recovery

Forgot-password requests are persistently rate-limited in MongoDB by both client
IP address and a SHA-256 identifier derived from the normalized email address.
Defaults are three requests per account and twenty per IP in one hour, followed
by a one-hour lockout. Email delivery runs on a bounded asynchronous executor so
the public response does not wait for SMTP. Email logs never contain reset or
verification URLs.

Login throttling is also always stored in MongoDB. The earlier optional
in-memory path and `LOGIN_THROTTLE_PERSISTENT` switch have been removed, ensuring
all backend replicas use the same counters.

After token-based or administrator-driven password changes, all MongoDB-backed
Spring Sessions for that username are removed. Role changes and account deletion
also invalidate the user's sessions.

## Office isolation

The hardcoded `goole` fallback has been removed. OFFICE and OFFICE_ADMIN accounts
without a valid assigned office now receive HTTP 403. HQ and SUPER must explicitly
select an existing office for office-scoped mutations. Cross-office listing for
HQ/SUPER remains intentional where supported by the endpoint.

## Auditing and errors

Office creation, deletion and bulk practice moves are audited, including source,
destination and moved-record counts. Field-encryption rotation success/failure is
also audited without recording passphrases or salts.

Expected API conflicts use a dedicated safe exception. Unexpected
`IllegalStateException` details are logged with a reference UUID while the client
receives only a generic error. Opportunistic legacy-password-hash upgrade failure
is logged/audited but no longer converts otherwise valid authentication into a
failed login.

## MongoDB and Nginx

MongoDB is no longer published to the host by the default Compose file. The
official image creates a root administrator and a separate application user for
new empty volumes. See `README-MongoDB-Authentication.md` before migrating an
existing volume.

The TLS load balancer explicitly permits TLS 1.2 and 1.3, limits body size, hides
its version and supplies CSP, HSTS, frame, MIME-sniffing, referrer and permissions
headers. Inline React styles were moved to the stylesheet so the CSP does not need
`unsafe-inline` for scripts or styles.

Compose now waits for an authenticated MongoDB health check before starting the
backend. Nginx dynamically re-resolves frontend and backend service addresses so
recreated replicas do not leave stale, unreachable upstream IP addresses.

## Recovery codes

New recovery codes use an independent random 16-byte salt in their stored SHA-256
representation. Existing unsalted hashes remain valid until consumed. Code removal
uses an atomic MongoDB `$pull`, preventing two concurrent requests from consuming
the same recovery code successfully.
