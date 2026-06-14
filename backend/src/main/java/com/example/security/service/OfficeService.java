package com.example.security.service;

import com.example.security.crypto.FieldCryptoService;
import com.example.security.dto.CreateOfficeRequest;
import com.example.security.dto.OfficeDto;
import com.example.security.dto.MovePracticePatientsRequest;
import com.example.security.dto.MovePracticePatientsResponse;
import com.example.security.model.OfficeAccount;
import com.example.security.model.AppUser;
import com.example.security.model.Role;
import com.example.security.model.PatientAppointmentDocument;
import com.example.security.repository.OfficeAccountRepository;
import com.example.security.repository.UserRepository;
import com.example.security.repository.PatientAppointmentDocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

@Service
public class OfficeService {
    private static final int SALT_BYTES = 20;
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final OfficeAccountRepository officeRepository;
    private final UserRepository userRepository;
    private final PatientAppointmentDocumentRepository appointmentRepository;
    private final FieldCryptoService crypto;
    private final SecureRandom rng = new SecureRandom();

    public OfficeService(OfficeAccountRepository officeRepository, UserRepository userRepository, PatientAppointmentDocumentRepository appointmentRepository, FieldCryptoService crypto) {
        this.officeRepository = officeRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.crypto = crypto;
    }

    public List<OfficeDto> findAll() {
        return officeRepository.findAllByOrderByOfficeIdAsc().stream().map(this::toDto).toList();
    }

    public Page<OfficeDto> findAll(Pageable pageable) {
        return officeRepository.findAllByOrderByOfficeIdAsc(pageable).map(this::toDto);
    }

    public OfficeDto create(CreateOfficeRequest request) {
        String officeId = requireText(request.officeId(), "Office id is required").toLowerCase();
        String username = requireText(request.username(), "Office username is required").toLowerCase();
        String password = requireText(request.password(), "Office password is required");
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Office password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (officeRepository.existsByOfficeId(officeId)) {
            throw new IllegalArgumentException("Office id already exists");
        }
        if (officeRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Office username already exists");
        }

        byte[] salt = new byte[SALT_BYTES];
        rng.nextBytes(salt);

        OfficeAccount office = new OfficeAccount();
        office.setOfficeId(officeId);
        office.setUsername(username);
        office.setSalt(salt);
        office.setHash(UserService.hashPassword(salt, password));
        office.setDisplayName(blankToNull(request.displayName()) == null ? titleCase(officeId) + " Ophthalmic Clinic" : request.displayName());
        office.setAddressEncrypted(crypto.encryptBlankAsNull(request.address()));
        office.setTelephoneEncrypted(crypto.encryptBlankAsNull(request.telephone()));
        office.setEmail(request.email());
        office.setCreatedAt(Instant.now());
        return toDto(officeRepository.save(office));
    }

    public void deleteByOfficeId(String officeId) {
        String normalized = requireText(officeId, "Office id is required").toLowerCase();
        if (!officeRepository.existsByOfficeId(normalized)) {
            throw new IllegalArgumentException("Office not found");
        }
        officeRepository.deleteByOfficeId(normalized);
    }



    public MovePracticePatientsResponse movePatientsBetweenOffices(MovePracticePatientsRequest request) {
        String fromOfficeId = requireText(request.fromOfficeId(), "From office id is required").toLowerCase();
        String toOfficeId = requireText(request.toOfficeId(), "To office id is required").toLowerCase();
        if (fromOfficeId.equals(toOfficeId)) {
            throw new IllegalArgumentException("From and to offices must be different");
        }
        if (!officeRepository.existsByOfficeId(fromOfficeId)) {
            throw new IllegalArgumentException("From office not found");
        }
        if (!officeRepository.existsByOfficeId(toOfficeId)) {
            throw new IllegalArgumentException("To office not found");
        }

        long patientsMoved = 0;
        for (AppUser user : userRepository.findByOfficeIdAndRolesContaining(fromOfficeId, Role.PATIENT)) {
            user.setOfficeId(toOfficeId);
            userRepository.save(user);
            patientsMoved++;
        }

        long cliniciansMoved = 0;
        Set<String> movedClinicianUsernames = new HashSet<>();
        for (AppUser user : userRepository.findByOfficeId(fromOfficeId)) {
            if (user.getRoles().contains(Role.OFFICE) || user.getRoles().contains(Role.OFFICE_ADMIN)) {
                user.setOfficeId(toOfficeId);
                userRepository.save(user);
                movedClinicianUsernames.add(user.getUsername());
            }
        }
        cliniciansMoved = movedClinicianUsernames.size();

        long appointmentsMoved = 0;
        for (PatientAppointmentDocument document : appointmentRepository.findByOfficeId(fromOfficeId)) {
            document.setOfficeId(toOfficeId);
            appointmentRepository.save(document);
            appointmentsMoved++;
        }

        // Intentionally do not delete the old office automatically. HQ can review, then delete manually.
        boolean deleted = false;

        return new MovePracticePatientsResponse(fromOfficeId, toOfficeId, patientsMoved, cliniciansMoved, appointmentsMoved, deleted);
    }

    public OfficeDto toDto(OfficeAccount office) {
        return new OfficeDto(
                office.getId(),
                office.getOfficeId(),
                office.getUsername(),
                office.getDisplayName(),
                crypto.decryptNullable(office.getAddressEncrypted()),
                crypto.decryptNullable(office.getTelephoneEncrypted()),
                office.getEmail()
        );
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) return "Office";
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
