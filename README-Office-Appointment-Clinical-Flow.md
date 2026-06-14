# Office appointment and clinical document flow

This build adds the next ophthalmic clinic workflow layer.

## Roles and responsibilities

- `PATIENT`: read-only patient portal. The patient can see their own appointment documents, prescription, clinician and clinical notes.
- `OFFICE_ADMIN`: creates appointment documents for patients belonging to that office. The office admin enters appointment date, time, appointment type, clinic name and clinician name.
- `OFFICE`: clinician/office workflow. The clinician opens an office appointment and enters the prescription and clinical notes.
- `HQ` and `SUPER`: may see all offices in this demo build.

## Office boundary

`AppUser` now has an `officeId` field. `PatientAppointmentDocument` also has an `officeId` field.

For ordinary office users:

```text
OFFICE_ADMIN can create appointments only in their own office.
OFFICE can update prescription/notes only for appointments in their own office.
PATIENT can read only documents where patientUsername == logged-in username.
```

The demo seeds these users if missing:

```text
patient      / ChangeThisPassword123! / PATIENT      / goole
office       / ChangeThisPassword123! / OFFICE       / goole
officeadmin  / ChangeThisPassword123! / OFFICE_ADMIN / goole
super        / configured password    / SUPER        / global
```

## API endpoints

```text
GET  /api/patient/appointments
GET  /api/office/appointments
PUT  /api/office/appointments/{id}/clinical
GET  /api/office-admin/appointments
POST /api/office-admin/appointments
```

## UI flow

1. `officeadmin` logs in.
2. Open **Office admin**.
3. Create an appointment for patient username `patient`, with date/time and clinician.
4. `office` logs in.
5. Open **Office / clinicians**.
6. Select the appointment.
7. Enter prescription and clinical note.
8. `patient` logs in.
9. The patient portal shows the appointment as read-only, with More... / Less... note reveal controls.
