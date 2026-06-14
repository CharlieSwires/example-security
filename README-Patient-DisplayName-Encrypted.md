# Patient display name encryption

This build encrypts patient display names at rest.

For new appointment documents the backend writes:

- `patientDisplayNameEncrypted` — AES/GCM encrypted display name.
- `patientDisplayNameLookupHash` — deterministic HMAC-SHA256 lookup token.

The backend no longer writes plaintext `patientDisplayName` for new appointment documents. The old deprecated field remains in the Java model only so older local/dev records can still be read and migrated.

Appointment fields deliberately left plaintext:

- `appointmentDate`
- `appointmentTime`
- `appointmentType`
- `officeId`
- `patientUsername`

Those fields are needed for ordering, filtering and access-control boundaries. Encrypting appointment date/time would make normal MongoDB sorting and date-range queries awkward unless a separate searchable token/index strategy was added.
