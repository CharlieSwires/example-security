# Security hardening fixes

This update closes five security gaps without removing Authy/TOTP MFA or the
SUPER-only audit log.

## Password hashing

New and changed passwords use PBKDF2-HMAC-SHA256 with a unique 20-byte salt,
a 256-bit result and 600,000 iterations. Each user now stores the iteration count
used for that hash.

Existing records without an iteration count are treated as legacy 65,000-iteration
hashes. After a successful password check, the backend immediately generates a new
salt and replaces the legacy hash with a 600,000-iteration hash. Users do not need
to reset their passwords and are not locked out by the upgrade.

## Complete, bounded field-key rotation

Key rotation now decrypts and re-encrypts `totpSecretEncrypted` as well as user,
office, appointment, prescription and clinical-note fields. Recovery codes remain
one-way hashes and therefore do not require encryption-key rotation.

User, office and appointment collections are processed in stable `_id` order and
in bounded pages. Configure the page size if required:

```env
FIELD_CRYPTO_ROTATION_BATCH_SIZE=100
```

Accepted values are 1 through 1,000.

## Mandatory field-encryption secrets

The repository no longer contains a default field-encryption passphrase or master
salt. With `FIELD_CRYPTO_ENABLED=true`, startup fails unless both are supplied.
The master salt must be valid Base64 representing at least 32 random bytes.

Generate it once and keep it with the protected deployment secrets:

```bash
openssl rand -base64 32
```

Do not regenerate the salt for an existing database; doing so would make existing
ciphertext unreadable.

## Trusted proxy addresses

Application code no longer reads `X-Forwarded-For` directly. Tomcat handles the
forwarding headers and only does so when the immediate peer matches the configured
trusted-proxy expression. The default trusts loopback and RFC1918/private Docker
networks used by the included Nginx deployment.

If the proxy topology changes, set `TRUSTED_PROXY_REGEX` to the narrowest expression
covering the actual reverse-proxy addresses. Never include arbitrary public address
ranges. A direct request with a forged `X-Forwarded-For` value uses the socket peer
address for both audit events and login-throttle buckets.
