package com.example.security.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "patient_appointment_documents")
public class PatientAppointmentDocument {
    @Id
    private String id;

    @Indexed
    private String patientUsername;

    @Indexed
    private String officeId;

    // Operational/sortable appointment fields are deliberately left plaintext.
    // Do not put clinical detail or actual person names in these fields.
    private LocalDate appointmentDate;
    private String appointmentTime;
    private String appointmentType;

    // Sensitive fields are encrypted at rest by FieldCryptoService before save.
    private String patientDisplayNameEncrypted;
    /** Deterministic HMAC lookup token; never stores the plaintext patient name. */
    @Indexed
    private String patientDisplayNameLookupHash;
    private String patientTelephoneEncrypted;
    private String clinicNameEncrypted;
    private String clinicianEncrypted;

    // Current prescription for this appointment/patient record.
    // Historical prescriptions resulting from individual visits are stored encrypted on each note.
    private String prescriptionEncrypted;
    private List<PatientClinicalNote> notes = new ArrayList<>();

    // Legacy plaintext fields retained only so old local/demo records can still be read and migrated.
    @Deprecated private String patientDisplayName;
    @Deprecated private String clinicName;
    @Deprecated private String clinician;
    @Deprecated private String prescription;

    public String getId() { return id; }
    public String getPatientUsername() { return patientUsername; }
    public String getOfficeId() { return officeId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public String getAppointmentTime() { return appointmentTime; }
    public String getAppointmentType() { return appointmentType; }
    public String getPatientDisplayNameEncrypted() { return patientDisplayNameEncrypted; }
    public String getPatientDisplayNameLookupHash() { return patientDisplayNameLookupHash; }
    public String getPatientTelephoneEncrypted() { return patientTelephoneEncrypted; }
    public String getClinicNameEncrypted() { return clinicNameEncrypted; }
    public String getClinicianEncrypted() { return clinicianEncrypted; }
    public String getPrescriptionEncrypted() { return prescriptionEncrypted; }
    public List<PatientClinicalNote> getNotes() { return notes; }

    @Deprecated public String getPatientDisplayName() { return patientDisplayName; }
    @Deprecated public String getClinicName() { return clinicName; }
    @Deprecated public String getClinician() { return clinician; }
    @Deprecated public String getPrescription() { return prescription; }

    public void setId(String id) { this.id = id; }
    public void setPatientUsername(String patientUsername) { this.patientUsername = patientUsername; }
    public void setOfficeId(String officeId) { this.officeId = officeId == null || officeId.isBlank() ? null : officeId.trim().toLowerCase(); }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public void setAppointmentTime(String appointmentTime) { this.appointmentTime = appointmentTime; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }
    public void setPatientDisplayNameEncrypted(String patientDisplayNameEncrypted) { this.patientDisplayNameEncrypted = patientDisplayNameEncrypted; }
    public void setPatientDisplayNameLookupHash(String patientDisplayNameLookupHash) { this.patientDisplayNameLookupHash = patientDisplayNameLookupHash; }
    public void setPatientTelephoneEncrypted(String patientTelephoneEncrypted) { this.patientTelephoneEncrypted = patientTelephoneEncrypted; }
    public void setClinicNameEncrypted(String clinicNameEncrypted) { this.clinicNameEncrypted = clinicNameEncrypted; }
    public void setClinicianEncrypted(String clinicianEncrypted) { this.clinicianEncrypted = clinicianEncrypted; }
    public void setPrescriptionEncrypted(String prescriptionEncrypted) { this.prescriptionEncrypted = prescriptionEncrypted; }
    public void setNotes(List<PatientClinicalNote> notes) { this.notes = notes == null ? new ArrayList<>() : notes; }

    @Deprecated public void setPatientDisplayName(String patientDisplayName) { this.patientDisplayName = patientDisplayName; }
    @Deprecated public void setClinicName(String clinicName) { this.clinicName = clinicName; }
    @Deprecated public void setClinician(String clinician) { this.clinician = clinician; }
    @Deprecated public void setPrescription(String prescription) { this.prescription = prescription; }

    public static class PatientClinicalNote {
        // Existing creation date kept for compatibility and clear date-only sorting/filtering.
        private LocalDate createdDate;

        // New clear date/time for the clinical event/note entry. Kept clear deliberately for sorting/display.
        private LocalDateTime noteDateTime;

        private String subjectEncrypted;
        private String noteTextEncrypted;

        // Historical prescription resulting from this appointment/note, encrypted at rest.
        private String prescriptionEncrypted;

        // Legacy plaintext fields retained only for old local/demo records.
        @Deprecated private String subject;
        @Deprecated private String noteText;
        @Deprecated private String prescription;

        public LocalDate getCreatedDate() { return createdDate; }
        public LocalDateTime getNoteDateTime() { return noteDateTime; }
        public String getSubjectEncrypted() { return subjectEncrypted; }
        public String getNoteTextEncrypted() { return noteTextEncrypted; }
        public String getPrescriptionEncrypted() { return prescriptionEncrypted; }
        @Deprecated public String getSubject() { return subject; }
        @Deprecated public String getNoteText() { return noteText; }
        @Deprecated public String getPrescription() { return prescription; }

        public void setCreatedDate(LocalDate createdDate) { this.createdDate = createdDate; }
        public void setNoteDateTime(LocalDateTime noteDateTime) { this.noteDateTime = noteDateTime; }
        public void setSubjectEncrypted(String subjectEncrypted) { this.subjectEncrypted = subjectEncrypted; }
        public void setNoteTextEncrypted(String noteTextEncrypted) { this.noteTextEncrypted = noteTextEncrypted; }
        public void setPrescriptionEncrypted(String prescriptionEncrypted) { this.prescriptionEncrypted = prescriptionEncrypted; }
        @Deprecated public void setSubject(String subject) { this.subject = subject; }
        @Deprecated public void setNoteText(String noteText) { this.noteText = noteText; }
        @Deprecated public void setPrescription(String prescription) { this.prescription = prescription; }
    }
}
