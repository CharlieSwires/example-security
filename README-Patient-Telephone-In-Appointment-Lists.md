# Patient telephone in appointment lists

The Office and Office Admin appointment lists now display the patient's telephone number returned by `PatientAppointmentDocumentDto.patientTelephone`.

The telephone number remains encrypted at rest in MongoDB as `patientTelephoneEncrypted`; it is decrypted only in the backend DTO mapper for authorised users who can access the appointment's office.

No telephone number is stored in plaintext in the appointment document.
