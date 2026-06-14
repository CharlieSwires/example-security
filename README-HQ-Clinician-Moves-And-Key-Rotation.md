# HQ clinician moves and one-shot 14-word key rotation

## HQ practice move

The HQ screen now moves:

- PATIENT users from the source office to the target office
- OFFICE and OFFICE_ADMIN users from the source office to the target office
- appointment documents from the source office to the target office

The old office is **not** deleted automatically. HQ/SUPER should review the move and then delete the old office manually from the office list.

## Super field-encryption key rotation

The Super admin screen now has a **Rotate 14-word field key** panel.

Enter:

- old 14-word string
- new 14-word string
- confirmation of the new 14-word string

The backend checks that the old string matches the currently running `FIELD_CRYPTO_PASSPHRASE`. If it does not match, no rotation starts.

When the rotation starts, the backend writes a document to `crypto_rotation_records` using a deterministic old-key-to-new-key rotation id. If the same rotation is attempted again, it is refused. This is what prevents the same re-encryption pass being accidentally repeated.

The rotation re-encrypts:

- encrypted user actual names and telephone numbers
- encrypted office addresses and telephone numbers
- encrypted patient display names and telephone numbers
- encrypted clinic names
- encrypted clinician names
- encrypted prescriptions
- encrypted clinical note subjects and note text

After a successful rotation, immediately update deployment config:

```env
FIELD_CRYPTO_PASSPHRASE=<new 14-word string>
```

Then restart all backend containers.

Do not keep old/new passphrases in source control, screenshots, logs, or chat transcripts for a real clinical deployment.
