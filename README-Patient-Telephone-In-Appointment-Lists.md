# Copyright

Copyright © 2026 Charlie's Tenacity Ltd. All rights reserved.

This is proprietary software. No permission is granted to copy, modify, distribute, sublicense or use this software except under a written licence issued by Charlie's Tenacity Ltd. Third-party components remain subject to their respective licences.

# Patient telephone in appointment lists

The Office and Office Admin appointment lists now display the patient's telephone number returned by `PatientAppointmentDocumentDto.patientTelephone`.

The telephone number remains encrypted at rest in MongoDB as `patientTelephoneEncrypted`; it is decrypted only in the backend DTO mapper for authorised users who can access the appointment's office.

No telephone number is stored in plaintext in the appointment document.
