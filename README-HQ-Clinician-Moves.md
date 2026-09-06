# HQ clinician and practice moves

The HQ screen moves:

- PATIENT users from the source office to the target office
- OFFICE and OFFICE_ADMIN users from the source office to the target office
- appointment documents from the source office to the target office

The old office is **not** deleted automatically. HQ/SUPER should review the move
and then delete the old office manually from the office list.

## Encryption maintenance

The web application contains no encryption-key rotation screen or backend
rotation endpoint. Key and salt maintenance is performed using the separate
`example-security-key-rotator` desktop/CLI repository while the application is
offline.

Before rotation, take and verify a backup and stop or drain every backend. After
the offline tool completes and verifies the migration, set the new
`FIELD_CRYPTO_PASSPHRASE` and `FIELD_CRYPTO_MASTER_SALT_B64` values on every
backend before restarting any of them.

Never commit either old or new secret to source control, screenshots or logs.
