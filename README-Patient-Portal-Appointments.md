# Patient Portal Appointment Documents

This update adds a real read-only patient appointment document flow.

## Backend

New backend pieces:

- `PatientAppointmentDocument` MongoDB document
- `PatientAppointmentDocumentRepository`
- `PatientPortalController`
- `GET /api/patient/appointments`

The endpoint returns appointment documents for the currently logged-in username only. A patient receives their own appointment documents, including:

- appointment date/time/type
- clinic name
- clinician
- prescription text
- clinical note creation dates
- clinical note subject headings
- note text

Only `GET /api/patient/**` is exposed. There are no patient POST/PUT/DELETE endpoints, so the patient portal is read-only.

Demo records are seeded for the initial `super` username and for a `patient` username if no records already exist for those accounts.

## Frontend

The Patient Portal screen now loads `/api/patient/appointments` after login.

The patient landing screen shows:

- a list of appointments on the left
- clicking an appointment opens the appointment document
- the document reveals appointment, prescription and clinician
- clinical notes are listed by creation date and subject
- each note has a `More...` / `Less...` button to reveal/hide the note text

The screen deliberately has no edit, save, delete, or note-entry controls.

## Security note

This is a starter implementation. The next production step should add a proper `patientId`/`officeId` model rather than relying only on username, and encrypted patient document payloads should be added before storing real ophthalmic records.
