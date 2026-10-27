package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
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
 * Public complaint-form patient autofill by exact document type + number.
 * No shared gateway/patient-service rate limiter exists for public endpoints;
 * this in-memory window limit is scoped to this permitAll lookup only.
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
    private final NotificationHelper notificationHelper;
    private final ConcurrentHashMap<String, List<Long>> requestTimestampsByClient = new ConcurrentHashMap<>();

    public PatientComplaintPublicLookupVM lookup(
        PatientComplaintPublicLookupRequestDTO request,
        String clientKey
    ) {
        enforceRateLimit(clientKey);

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
            .filter(patient -> patient != null && patient.getId() != null)
            .collect(Collectors.toMap(Patient::getId, patient -> patient, (left, right) -> left));

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

        Patient patient = patientsById.values().iterator().next();
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
