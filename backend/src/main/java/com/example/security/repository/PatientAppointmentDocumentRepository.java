package com.example.security.repository;

import com.example.security.model.PatientAppointmentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PatientAppointmentDocumentRepository extends MongoRepository<PatientAppointmentDocument, String> {
    List<PatientAppointmentDocument> findByPatientUsernameOrderByAppointmentDateDescAppointmentTimeAsc(String patientUsername);
    List<PatientAppointmentDocument> findByOfficeIdOrderByAppointmentDateDescAppointmentTimeAsc(String officeId);
    List<PatientAppointmentDocument> findByOfficeId(String officeId);
    List<PatientAppointmentDocument> findAllByOrderByAppointmentDateDescAppointmentTimeAsc();
}
