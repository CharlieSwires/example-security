# Patient / Office / HQ role model

This copy changes the demo role model shown in the UI and enforced by the backend.

## Active roles

- `PATIENT` - read-only patient access.
- `OFFICE` - normal office staff access.
- `OFFICE_ADMIN` - office administration access.
- `HQ` - head office access across offices.
- `SUPER` - system administration and user administration.

`USER` and `DEVELOPER` are retained only as legacy enum values so old Atlas user documents can still be read. They are normalised as:

- `USER` -> `PATIENT`
- `DEVELOPER` -> `OFFICE_ADMIN`

## UI changes

The React frontend now shows a Patient / Office / HQ dashboard with cards for:

- `/patient`
- `/office`
- `/office-admin`
- `/hq`
- `/super`

The SUPER user administration panel now creates users as `PATIENT` by default and shows checkboxes for `PATIENT`, `OFFICE`, `OFFICE_ADMIN`, `HQ`, and `SUPER`.

## Backend changes

Spring Security now protects the example endpoints as follows:

- `/patient`: `PATIENT`, `OFFICE`, `OFFICE_ADMIN`, `HQ`, `SUPER`
- `/office`: `OFFICE`, `OFFICE_ADMIN`, `HQ`, `SUPER`
- `/office-admin`: `OFFICE_ADMIN`, `HQ`, `SUPER`
- `/hq`: `HQ`, `SUPER`
- `/super`: `SUPER`
- `/api/admin/**`: `SUPER`

`/user` and `/developer` remain as compatibility endpoints, but the UI no longer uses them.

## Next step for a real patient system

This is role-based access control only. For real patient data you should also add ownership checks such as `patientUserId`, `officeId`, and service-layer rules so that one office cannot access another office's patients.
