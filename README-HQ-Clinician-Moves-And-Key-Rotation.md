# HQ clinician moves and one-shot field-encryption rotation

## HQ practice move

The HQ screen now moves:

- PATIENT users from the source office to the target office
- OFFICE and OFFICE_ADMIN users from the source office to the target office
- appointment documents from the source office to the target office

The old office is **not** deleted automatically. HQ/SUPER should review the move and then delete the old office manually from the office list.

## SUPER-only field-encryption key and salt rotation

The Super admin screen has a **Rotate field-encryption key and salt** panel. Both
the URL security rules and method-level security restrict this operation to the
`SUPER` role.

Enter:

- current 14-word passphrase and Base64 master salt
- new 14-word passphrase and confirmation
- new Base64 master salt and confirmation

The screen can generate a cryptographically random 32-byte salt. The existing
30-byte development salt remains accepted only as an input/current salt so that
data encrypted with it can be migrated. Every newly selected salt must contain
at least 32 bytes.

The backend checks that the old passphrase/salt pair matches the currently
running `FIELD_CRYPTO_PASSPHRASE` and `FIELD_CRYPTO_MASTER_SALT_B64`. If it does
not match, no rotation starts. The passphrase may remain unchanged when only the
salt is being rotated.

When the rotation starts, the backend writes a document to `crypto_rotation_records` using a deterministic old-key-to-new-key rotation id. If the same rotation is attempted again, it is refused. This is what prevents the same re-encryption pass being accidentally repeated.

The rotation re-encrypts:

- encrypted user actual names and telephone numbers
- encrypted Authy/TOTP secrets
- encrypted office addresses and telephone numbers
- encrypted patient display names and telephone numbers
- encrypted clinic names
- encrypted clinician names
- encrypted prescriptions
- encrypted clinical note subjects and note text

Records are read and saved in configurable, stable `_id`-ordered pages rather than
using an unbounded `findAll()`. The default is 100 records per page:

```env
FIELD_CRYPTO_ROTATION_BATCH_SIZE=100
```

After a successful rotation, immediately update deployment config:

```env
FIELD_CRYPTO_PASSPHRASE=<new 14-word string>
FIELD_CRYPTO_MASTER_SALT_B64=<new Base64 salt>
```

Then restart all backend containers. Perform rotation in a maintenance window
with one backend instance and no concurrent application writes. A second backend
still using the old pair must not handle traffic once migration begins.

Do not keep old/new passphrases in source control, screenshots, logs, or chat transcripts for a real clinical deployment.
