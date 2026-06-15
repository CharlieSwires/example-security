package com.example.security.controller;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.dto.CreateAppointmentRequest;
import com.example.security.dto.PatientAppointmentDocumentDto;
import com.example.security.dto.PageResponse;
import com.example.security.dto.PatientLookupDto;
import com.example.security.dto.UpdateClinicalDocumentRequest;
import com.example.security.dto.UpdateAppointmentAdminRequest;
import com.example.security.dto.MoveUserOfficeRequest;
import com.example.security.model.AppUser;
import com.example.security.model.PatientAppointmentDocument;
import com.example.security.model.Role;
import com.example.security.repository.PatientAppointmentDocumentRepository;
import com.example.security.repository.UserRepository;
import com.example.security.repository.OfficeAccountRepository;
import com.example.security.service.PatientAppointmentMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
public class OfficeAppointmentController {
    private static final String DEFAULT_OFFICE_ID = "goole";
    private static final int PAGE_SIZE = 50;

    private final PatientAppointmentDocumentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final OfficeAccountRepository officeRepository;
    private final FieldCryptoService crypto;
    private final PatientAppointmentMapper mapper;

    public OfficeAppointmentController(
            PatientAppointmentDocumentRepository appointmentRepository,
            UserRepository userRepository,
            OfficeAccountRepository officeRepository,
            FieldCryptoService crypto,
            PatientAppointmentMapper mapper
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.crypto = crypto;
        this.mapper = mapper;
    }

    @GetMapping({"/api/office/appointments", "/api/office-admin/appointments"})
    public PageResponse<PatientAppointmentDocumentDto> officeAppointments(
            @RequestParam(required = false) String officeId,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        if (isHqOrSuper(current)) {
            String requestedOfficeId = normalizeOfficeId(officeId);
            if (requestedOfficeId != null) {
                return PageResponse.from(appointmentRepository.findByOfficeIdOrderByAppointmentDateDescAppointmentTimeAsc(requestedOfficeId, pageRequest)
                        .map(mapper::toDto));
            }
            return PageResponse.from(appointmentRepository.findAllByOrderByAppointmentDateDescAppointmentTimeAsc(pageRequest)
                    .map(mapper::toDto));
        }

        officeId = requiredOfficeId(current);
        return PageResponse.from(appointmentRepository.findByOfficeIdOrderByAppointmentDateDescAppointmentTimeAsc(officeId, pageRequest)
                .map(mapper::toDto));
    }


    @GetMapping("/api/office-admin/patients/{username}")
    public PatientLookupDto lookupPatientForAppointment(
            @PathVariable String username,
            @RequestParam(required = false) String officeId,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        String patientUsername = requireText(username, "Patient username is required");
        AppUser patient = userRepository.findByUsername(patientUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient username was not found"));

        if (!patient.getRoles().contains(Role.PATIENT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected user is not a PATIENT account");
        }

        String patientOfficeId = normalizeOfficeId(patient.getOfficeId());
        if (patientOfficeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient does not have an officeId");
        }
        if (isHqOrSuper(current)) {
            String selectedOfficeId = normalizeOfficeId(officeId);
            if (selectedOfficeId != null && !selectedOfficeId.equals(patientOfficeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient belongs to another office");
            }
        } else if (!requiredOfficeId(current).equals(patientOfficeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient belongs to another office");
        }

        String displayName = crypto.decryptNullable(patient.getDisplayNameEncrypted());
        String telephone = crypto.decryptNullable(patient.getTelephoneEncrypted());
        return new PatientLookupDto(
                patient.getUsername(),
                displayName == null ? patient.getUsername() : displayName,
                telephone == null ? "" : telephone,
                patientOfficeId
        );
    }

    @PostMapping("/api/office-admin/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientAppointmentDocumentDto createAppointment(
            @RequestBody CreateAppointmentRequest request,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        String officeId = chooseOfficeId(current, request.officeId());

        String patientUsername = requireText(request.patientUsername(), "Patient username is required");
        AppUser patient = userRepository.findByUsername(patientUsername)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient username does not exist"));
        String patientOfficeId = normalizeOfficeId(patient.getOfficeId());
        if (patientOfficeId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Patient does not have an officeId");
        }
        if (!patientOfficeId.equals(officeId) && !isHqOrSuper(current)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient belongs to another office");
        }

        String displayName = blankToNull(request.patientDisplayName());
        if (displayName == null) {
            displayName = crypto.decryptNullable(patient.getDisplayNameEncrypted());
        }
        if (displayName == null) {
            displayName = patientUsername;
        }
        String telephone = blankToNull(request.patientTelephone());
        if (telephone == null) {
            telephone = crypto.decryptNullable(patient.getTelephoneEncrypted());
        }

        PatientAppointmentDocument document = new PatientAppointmentDocument();
        document.setPatientUsername(patientUsername);
        document.setPatientDisplayNameEncrypted(crypto.encryptBlankAsNull(displayName));
        document.setPatientDisplayNameLookupHash(crypto.lookupHashNullable(displayName));
        document.setPatientDisplayName(null);
        document.setPatientTelephoneEncrypted(crypto.encryptBlankAsNull(telephone));
        document.setOfficeId(officeId);
        document.setAppointmentDate(request.appointmentDate() == null ? LocalDate.now() : request.appointmentDate());
        document.setAppointmentTime(blankToNull(request.appointmentTime()) == null ? "09:00" : request.appointmentTime().trim());
        document.setAppointmentType(blankToNull(request.appointmentType()) == null ? "Ophthalmic appointment" : request.appointmentType().trim());
        document.setClinicNameEncrypted(crypto.encryptBlankAsNull(blankToNull(request.clinicName()) == null ? titleCaseOffice(officeId) + " Ophthalmic Clinic" : request.clinicName().trim()));
        document.setClinicianEncrypted(crypto.encryptBlankAsNull(requireText(request.clinician(), "Clinician is required")));
        document.setPrescriptionEncrypted(null);

        return mapper.toDto(appointmentRepository.save(document));
    }



    @PutMapping("/api/office-admin/appointments/{id}/admin")
    public PatientAppointmentDocumentDto updateAppointmentAdmin(
            @PathVariable String id,
            @RequestBody UpdateAppointmentAdminRequest request,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        PatientAppointmentDocument document = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        ensureCanAccessOfficeDocument(current, document);

        if (request.appointmentDate() != null) {
            document.setAppointmentDate(request.appointmentDate());
        }
        if (blankToNull(request.appointmentTime()) != null) {
            document.setAppointmentTime(request.appointmentTime().trim());
        }
        if (blankToNull(request.appointmentType()) != null) {
            document.setAppointmentType(request.appointmentType().trim());
        }
        if (request.clinician() != null) {
            document.setClinicianEncrypted(crypto.encryptBlankAsNull(requireText(request.clinician(), "Clinician is required")));
            document.setClinician(null);
        }

        return mapper.toDto(appointmentRepository.save(document));
    }

    @DeleteMapping("/api/office-admin/appointments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAppointment(
            @PathVariable String id,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        PatientAppointmentDocument document = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        ensureCanAccessOfficeDocument(current, document);
        appointmentRepository.delete(document);
    }

    @PutMapping("/api/office-admin/patients/{username}/office")
    public PatientLookupDto movePatientToOffice(
            @PathVariable String username,
            @RequestBody MoveUserOfficeRequest request,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        String targetOfficeId = requireExistingOfficeId(request.targetOfficeId());
        AppUser patient = userRepository.findByUsername(requireText(username, "Patient username is required"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient username was not found"));
        if (!patient.getRoles().contains(Role.PATIENT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected user is not a PATIENT account");
        }
        ensureCanMoveUserFromCurrentOffice(current, patient, "Patient belongs to another office");
        patient.setOfficeId(targetOfficeId);
        userRepository.save(patient);

        appointmentRepository.findByPatientUsernameOrderByAppointmentDateDescAppointmentTimeAsc(patient.getUsername()).forEach(document -> {
            document.setOfficeId(targetOfficeId);
            appointmentRepository.save(document);
        });

        String displayName = crypto.decryptNullable(patient.getDisplayNameEncrypted());
        String telephone = crypto.decryptNullable(patient.getTelephoneEncrypted());
        return new PatientLookupDto(patient.getUsername(), displayName == null ? patient.getUsername() : displayName, telephone == null ? "" : telephone, targetOfficeId);
    }

    @PutMapping("/api/office-admin/clinicians/{username}/office")
    public PatientLookupDto moveClinicianToOffice(
            @PathVariable String username,
            @RequestBody MoveUserOfficeRequest request,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        String targetOfficeId = requireExistingOfficeId(request.targetOfficeId());
        AppUser clinician = userRepository.findByUsername(requireText(username, "Clinician username is required"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinician username was not found"));
        if (!clinician.getRoles().contains(Role.OFFICE) && !clinician.getRoles().contains(Role.OFFICE_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected user is not an OFFICE or OFFICE_ADMIN account");
        }
        ensureCanMoveUserFromCurrentOffice(current, clinician, "Clinician belongs to another office");
        clinician.setOfficeId(targetOfficeId);
        userRepository.save(clinician);

        String displayName = crypto.decryptNullable(clinician.getDisplayNameEncrypted());
        String telephone = crypto.decryptNullable(clinician.getTelephoneEncrypted());
        return new PatientLookupDto(clinician.getUsername(), displayName == null ? clinician.getUsername() : displayName, telephone == null ? "" : telephone, targetOfficeId);
    }

    @PutMapping("/api/office/appointments/{id}/clinical")
    public PatientAppointmentDocumentDto updateClinicalDocument(
            @PathVariable String id,
            @RequestBody UpdateClinicalDocumentRequest request,
            Authentication authentication
    ) {
        AppUser current = currentUser(authentication);
        PatientAppointmentDocument document = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        ensureCanAccessOfficeDocument(current, document);

        if (request.prescription() != null) {
            document.setPrescriptionEncrypted(crypto.encryptBlankAsNull(request.prescription()));
            document.setPrescription(null);
        }

        if (blankToNull(request.noteText()) != null || blankToNull(request.noteSubject()) != null || blankToNull(request.notePrescription()) != null) {
            PatientAppointmentDocument.PatientClinicalNote note = new PatientAppointmentDocument.PatientClinicalNote();
            LocalDateTime noteDateTime = request.noteDateTime();
            if (noteDateTime == null) {
                noteDateTime = request.noteDate() == null ? LocalDateTime.now() : request.noteDate().atStartOfDay();
            }
            note.setNoteDateTime(noteDateTime);
            note.setCreatedDate(noteDateTime.toLocalDate());
            note.setSubjectEncrypted(crypto.encryptBlankAsNull(blankToNull(request.noteSubject()) == null ? "Clinical note" : request.noteSubject().trim()));
            note.setNoteTextEncrypted(crypto.encryptBlankAsNull(blankToNull(request.noteText()) == null ? "" : request.noteText().trim()));
            note.setPrescriptionEncrypted(crypto.encryptBlankAsNull(blankToNull(request.notePrescription()) == null ? "" : request.notePrescription().trim()));
            note.setSubject(null);
            note.setNoteText(null);
            note.setPrescription(null);
            document.getNotes().add(note);
        }

        return mapper.toDto(appointmentRepository.save(document));
    }

    private AppUser currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user not found"));
    }

    private void ensureCanAccessOfficeDocument(AppUser current, PatientAppointmentDocument document) {
        if (isHqOrSuper(current)) {
            return;
        }
        String currentOfficeId = requiredOfficeId(current);
        String documentOfficeId = normalizeOfficeId(document.getOfficeId());
        if (!currentOfficeId.equals(documentOfficeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Appointment belongs to another office");
        }
    }

    private String chooseOfficeId(AppUser current, String requestedOfficeId) {
        if (isHqOrSuper(current)) {
            String chosen = normalizeOfficeId(requestedOfficeId);
            return chosen == null ? DEFAULT_OFFICE_ID : chosen;
        }
        return requiredOfficeId(current);
    }

    private String requiredOfficeId(AppUser current) {
        String officeId = normalizeOfficeId(current.getOfficeId());
        if (officeId == null) {
            return DEFAULT_OFFICE_ID;
        }
        return officeId;
    }

    private boolean isHqOrSuper(AppUser current) {
        Set<Role> roles = current.getRoles();
        return roles.contains(Role.HQ) || roles.contains(Role.SUPER);
    }



    private void ensureCanMoveUserFromCurrentOffice(AppUser current, AppUser targetUser, String wrongOfficeMessage) {
        if (isHqOrSuper(current)) {
            return;
        }
        String currentOfficeId = requiredOfficeId(current);
        String targetCurrentOfficeId = normalizeOfficeId(targetUser.getOfficeId());
        if (!currentOfficeId.equals(targetCurrentOfficeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, wrongOfficeMessage);
        }
    }

    private String requireExistingOfficeId(String officeId) {
        String normalized = normalizeOfficeId(requireText(officeId, "Target office id is required"));
        if (normalized == null || !officeRepository.existsByOfficeId(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target office does not exist");
        }
        return normalized;
    }

    private String normalizeOfficeId(String officeId) {
        return officeId == null || officeId.isBlank() ? null : officeId.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String titleCaseOffice(String officeId) {
        if (officeId == null || officeId.isBlank()) return "Local";
        return officeId.substring(0, 1).toUpperCase() + officeId.substring(1);
    }
}
