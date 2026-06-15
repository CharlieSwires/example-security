# Current prescription and historical note prescriptions

This build keeps the appointment-level `prescriptionEncrypted` field in place, but the UI now labels it as **Current prescription**.

Clinical notes now also support:

- `noteDateTime` — clear/plain date-time for sorting and display.
- `prescriptionEncrypted` — encrypted prescription result for that specific historical appointment note.
- `subjectEncrypted` — encrypted note subject.
- `noteTextEncrypted` — encrypted note body.

The existing note `createdDate` field is retained for compatibility. New notes set both `createdDate` and `noteDateTime`.

## Why noteDateTime is clear

The note date/time is intentionally left plaintext so the system can sort and display clinical history without decrypting every note first. It should not contain clinical content.

## Why note prescription is encrypted

The prescription resulting from an appointment is clinical data, so it is encrypted at rest using `FieldCryptoService`.

## API change

`PUT /api/office/appointments/{id}/clinical` now accepts:

```json
{
  "prescription": "current prescription text",
  "noteDateTime": "2026-06-14T09:30",
  "noteSubject": "Refraction completed",
  "notePrescription": "historical prescription from this visit",
  "noteText": "clinical note text"
}
```

The older `noteDate` field is still accepted as a fallback.
