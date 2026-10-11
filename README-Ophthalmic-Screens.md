# Copyright

Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This is proprietary software. No permission is granted to copy, modify, distribute, sublicense or use this software except under a written licence issued by Charlie's Tenacity Ltd. Third-party components remain subject to their respective licences.

# Ophthalmic clinic screens

This copy updates the demo UI from generic role endpoint cards into role-based ophthalmic clinic screens.

## Screens

- **Patient portal**: read-only view for patients. Intended for appointments, prescriptions, letters and encrypted patient documents.
- **Office / clinicians**: clinical workflow for an ophthalmic office/clinic. Intended for patient search, clinic list and clinical observations.
- **Office admin**: clinic administration for OFFICE_ADMIN users. Intended for office settings, staff and appointments.
- **HQ**: multi-office reporting for HQ users.
- **Super admin**: system administration for SUPER users, including existing user/role administration and future key-management operations.

## Roles

- `PATIENT`: read only.
- `OFFICE`: clinician/staff access for one clinic/office.
- `OFFICE_ADMIN`: clinic administration.
- `HQ`: head-office view across clinics.
- `SUPER`: system administration.

The screens are currently UI scaffolding plus the existing security-protected test endpoints. Real patient/office data should be implemented with `officeId` and `patientUserId` checks in the service layer before any sensitive encrypted document data is returned.
