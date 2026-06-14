# Office Admin and HQ Practice Movement Workflow

This version adds the next ophthalmic workflow controls.

## OFFICE_ADMIN appointment controls

Office Admin users can now manage appointments for their own office:

- change the clinician assigned to an appointment
- reschedule the appointment date and time
- change the appointment type
- remove/delete an appointment

Backend endpoints:

```text
PUT    /api/office-admin/appointments/{id}/admin
DELETE /api/office-admin/appointments/{id}
```

The backend still enforces the office boundary. An OFFICE_ADMIN user can only alter appointments belonging to their own `officeId`. HQ and SUPER can work across offices, and SUPER may use the selected office context in the UI.

## OFFICE_ADMIN people movement

Office Admin users can move users out of their own practice into another existing practice:

- move a patient to another office/practice
- move a clinician/staff user to another office/practice

Backend endpoints:

```text
PUT /api/office-admin/patients/{username}/office
PUT /api/office-admin/clinicians/{username}/office
```

Request body:

```json
{
  "targetOfficeId": "rawcliffe"
}
```

When a patient is moved, existing appointment documents for that patient are also moved to the target `officeId` so the new practice can continue their records.

## HQ practice closure / merger

HQ can move all patients and appointment documents from one practice to another, optionally deleting the old office account afterwards.

Backend endpoint:

```text
POST /api/hq/offices/move-patients
```

Request body:

```json
{
  "fromOfficeId": "oldpractice",
  "toOfficeId": "newpractice",
  "deleteOldOffice": true
}
```

Response body:

```json
{
  "fromOfficeId": "oldpractice",
  "toOfficeId": "newpractice",
  "patientsMoved": 14,
  "appointmentsMoved": 28,
  "oldOfficeDeleted": true
}
```

## Security notes

- Appointment dates, times, appointment type, `officeId`, and usernames remain plaintext for sorting, routing, and access-control checks.
- Patient names, patient telephone numbers, clinician names, prescriptions and notes remain encrypted at rest.
- Office Admin movement checks the current office boundary before moving a patient or clinician.
- HQ/SUPER can move users across offices.
