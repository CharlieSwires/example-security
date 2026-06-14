# Encrypted clinical/person-identifying fields

This build removes the demo patient/office/office-admin seed users and removes the seeded demo appointment documents. The application still creates the initial SUPER user when the database is empty.

## What is encrypted at rest

The backend now encrypts these fields before saving them to MongoDB/Atlas:

- patient actual/display name on appointment documents
- patient telephone number on appointment documents
- clinic display name on appointment documents
- clinician name
- prescription
- clinical note subject heading
- clinical note body text
- user actual/display name
- user telephone number

Operational routing fields remain plaintext:

- username
- officeId
- appointmentDate
- appointmentTime
- appointmentType
- roles
- verified email address

The appointment date and time are deliberately not encrypted because MongoDB needs them for sorting, filtering and appointment lists. If you encrypted the date itself, normal queries such as “show this office’s next appointments in date order” would either stop working or require decrypting many records in application memory.

## Deployment secret

Set a real 14+ random word passphrase before real use:

```env
FIELD_CRYPTO_ENABLED=true
FIELD_CRYPTO_PASSPHRASE=replace this with fourteen or more random words before real use
FIELD_CRYPTO_MASTER_SALT_B64=ZXhhbXBsZS1zZWN1cml0eS1kZXYtc2FsdC0yMDI2
```

The default built into `application.yml` is only to stop local development breaking when an old `env.list` is copied over. Do not use the default for real data.

Keep the phrase out of Git. Put it in `env.list`, Docker secrets, Kubernetes secrets, AWS Secrets Manager, Azure Key Vault, or your chosen secret store.

## Key change warning

Changing `FIELD_CRYPTO_PASSPHRASE` or `FIELD_CRYPTO_MASTER_SALT_B64` after data has been written means the existing encrypted fields cannot be decrypted unless you first migrate/rewrap them. For production, add a formal key registry and key-rotation job before changing live keys.

## Existing old demo records

This version no longer creates dummy records. If older dummy records already exist in your Atlas database, delete them from the SUPER user admin screen or directly from the MongoDB collections.

## Patient display name encryption update

`patientDisplayName` is treated as personal clinical data and is not stored as plaintext for new appointment records.

New records now store:

```text
patientDisplayNameEncrypted       AES-GCM encrypted display name
patientDisplayNameLookupHash      deterministic HMAC token for exact lookup without plaintext
```

The lookup hash is not reversible, but it is still sensitive because someone with the secret can test guesses. Keep the field crypto passphrase and salt secret. Appointment date, time, type, officeId and patientUsername remain plaintext so appointment lists can still be sorted, filtered and ownership-checked efficiently.

The deprecated plaintext fields are retained only so old local test records can still be read during migration; new writes clear `patientDisplayName` and write only the encrypted value plus lookup hash.
