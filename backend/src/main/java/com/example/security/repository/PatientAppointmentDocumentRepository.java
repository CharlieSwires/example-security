package com.example.security.repository;

import com.example.security.model.PatientAppointmentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PatientAppointmentDocumentRepository extends MongoRepository<PatientAppointmentDocument, String> {
    List<PatientAppointmentDocument> findByPatientUsernameOrderByAppointmentDateDescAppointmentTimeAsc(String patientUsername);
    Page<PatientAppointmentDocument> findByPatientUsernameOrderByAppointmentDateDescAppointmentTimeAsc(String patientUsername, Pageable pageable);
    List<PatientAppointmentDocument> findByOfficeIdOrderByAppointmentDateDescAppointmentTimeAsc(String officeId);
    Page<PatientAppointmentDocument> findByOfficeIdOrderByAppointmentDateDescAppointmentTimeAsc(String officeId, Pageable pageable);
    List<PatientAppointmentDocument> findByOfficeId(String officeId);
    List<PatientAppointmentDocument> findAllByOrderByAppointmentDateDescAppointmentTimeAsc();
    Page<PatientAppointmentDocument> findAllByOrderByAppointmentDateDescAppointmentTimeAsc(Pageable pageable);
}
