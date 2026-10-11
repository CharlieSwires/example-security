# Copyright

Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This is proprietary software. No permission is granted to copy, modify, distribute, sublicense or use this software except under a written licence issued by Charlie's Tenacity Ltd. Third-party components remain subject to their respective licences.

# Patient username lookup / autofill

The Office Admin create-appointment screen now treats the patient username as the source of truth.

When an OFFICE_ADMIN enters a patient username and leaves the field, or clicks **Lookup**, the frontend calls:

```text
GET /api/office-admin/patients/{username}
```

The backend:

- finds the `PATIENT` user account,
- checks the patient belongs to the same office as the OFFICE_ADMIN unless the caller is HQ/SUPER,
- decrypts the encrypted display name and telephone number in memory,
- returns only the details needed to populate the appointment form.

The appointment form then fills:

- Patient display name
- Patient telephone
- Office / clinic id

The display name and telephone fields are read-only on the appointment form so the admin does not accidentally create a different copy of the patient identity. To change those values, update the patient user record.

Sensitive values remain encrypted at rest in MongoDB/Atlas.
