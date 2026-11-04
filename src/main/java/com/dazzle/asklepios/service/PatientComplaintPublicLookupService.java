package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.AuthoritiesConstants;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintPublicLookupRequestDTO;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintPublicLookupVM;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Complaint-form patient autofill by exact document type + number.
 * Anonymous callers must present a verified patient JWT (portal OTP login).
 * Authenticated staff (USER/ADMIN) may look up without a patient token.
 * Patient JWT lookups are restricted to the authenticated patient's identity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PatientComplaintPublicLookupService {

    private static final String ENTITY = "patient-complaint-lookup";
    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_SECONDS = 60L;

    private final PatientDocumentRepository patientDocumentRepository;
    private final PatientRepository patientRepository;
    private final NotificationHelper notificationHelper;
    private final ConcurrentHashMap<String, List<Long>> requestTimestampsByClient = new ConcurrentHashMap<>();

    public PatientComplaintPublicLookupVM lookup(
        PatientComplaintPublicLookupRequestDTO request,
        String clientKey
    ) {
        enforceRateLimit(clientKey);

        Patient patient = resolveUniquePatient(request);

        if (isAuthorizedStaffLookup()) {
            return toLookupVm(patient);
        }

        Patient authenticatedPatient = requireAuthenticatedPatient();
        if (!authenticatedPatient.getId().equals(patient.getId())) {
            throw new BadRequestAlertException(
                "Verified patient does not match this document",
                ENTITY,
                "otp.patient.mismatch"
            );
        }

        return toLookupVm(patient);
    }

    private Patient resolveUniquePatient(PatientComplaintPublicLookupRequestDTO request) {
        if (request == null || request.documentType() == null) {
            throw new BadRequestAlertException("Document type is required", ENTITY, "documentType.required");
        }
        if (request.documentNumber() == null || request.documentNumber().isBlank()) {
            throw new BadRequestAlertException("Document number is required", ENTITY, "documentNumber.required");
        }

        DocumentType documentType = request.documentType();
        String documentNumber = request.documentNumber().trim();

        List<PatientDocument> matches = patientDocumentRepository.findByTypeAndNumberIgnoreCase(
            documentType,
            documentNumber
        );

        Map<Long, Patient> patientsById = matches
            .stream()
            .map(PatientDocument::getPatient)
            .filter(matched -> matched != null && matched.getId() != null)
            .collect(Collectors.toMap(Patient::getId, matched -> matched, (left, right) -> left));

        if (patientsById.isEmpty()) {
            throw new BadRequestAlertException(
                "No matching patient found for this document",
                ENTITY,
                "patient.notfound"
            );
        }

        if (patientsById.size() > 1) {
            log.debug(
                "Public complaint lookup ambiguous ({} patients). documentType={}",
                patientsById.size(),
                documentType
            );
            throw new BadRequestAlertException(
                "Multiple patients match this document. Please enter your details manually.",
                ENTITY,
                "patient.ambiguous"
            );
        }

        return patientsById.values().iterator().next();
    }

    private Patient requireAuthenticatedPatient() {
        if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.PATIENT)) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Patient verification is required before loading patient details"
            );
        }

        String mrn = SecurityUtils
            .getCurrentUserLogin()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseThrow(() ->
                new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Patient verification is required before loading patient details"
                )
            );

        return patientRepository
            .findByMedicalRecordNumber(mrn)
            .orElseThrow(() ->
                new BadRequestAlertException(
                    "Authenticated patient was not found",
                    ENTITY,
                    "patient.notfound"
                )
            );
    }

    private static boolean isAuthorizedStaffLookup() {
        return (
            SecurityUtils.isAuthenticated() &&
            SecurityUtils.hasCurrentUserAnyOfAuthorities(
                AuthoritiesConstants.USER,
                AuthoritiesConstants.ADMIN
            )
        );
    }

    private PatientComplaintPublicLookupVM toLookupVm(Patient patient) {
        PatientComplaintPublicLookupVM vm = new PatientComplaintPublicLookupVM();
        vm.setPatientName(blankToNull(notificationHelper.getPatientName(patient)));
        vm.setContactPhone(blankToNull(patient.getPrimaryMobileNumber()));
        vm.setContactEmail(blankToNull(patient.getEmail()));
        return vm;
    }

    private void enforceRateLimit(String clientKey) {
        String key = sanitizeClientKey(clientKey);
        long now = Instant.now().getEpochSecond();
        long windowStart = now - WINDOW_SECONDS;

        List<Long> timestamps = requestTimestampsByClient.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (timestamps) {
            Iterator<Long> iterator = timestamps.iterator();
            while (iterator.hasNext()) {
                if (iterator.next() < windowStart) {
                    iterator.remove();
                }
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_WINDOW) {
                log.warn("Public complaint patient lookup rate-limited. client={}", key);
                throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many lookup requests. Please try again shortly."
                );
            }
            timestamps.add(now);
        }
    }

    private static String sanitizeClientKey(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return "unknown";
        }
        return clientKey.trim().replaceAll("[^a-zA-Z0-9.:_-]", "");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
