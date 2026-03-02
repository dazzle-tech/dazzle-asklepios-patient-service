package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.repository.AppointmentRepository;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PatientDocumentRepository patientDocumentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository, PatientRepository patientRepository, JdbcTemplate jdbcTemplate, PatientDocumentRepository patientDocumentRepository) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.patientDocumentRepository = patientDocumentRepository;
    }

    public List<Appointment> getAppointments(String resourceType, String facilityId, List<String> resources) {
        String normalizedResourceType = (resourceType != null && !resourceType.equals("null") && !resourceType.isEmpty())
                ? resourceType : null;
        String normalizedFacilityId = (facilityId != null && !facilityId.equals("null") && !facilityId.isEmpty())
                ? facilityId : null;
        List<String> normalizedResources = (resources != null && !resources.isEmpty())
                ? resources.stream()
                    .filter(r -> r != null && !r.equals("null") && !r.equals("undefined") && !r.isEmpty())
                    .collect(Collectors.toList())
                : null;

        if (normalizedResources != null && normalizedResources.isEmpty()) {
            normalizedResources = null;
        }

        final List<String> finalResourceKeys = normalizedResources;

        Specification<Appointment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            predicates.add(cb.equal(root.get("isValid"), true));
            predicates.add(cb.isNull(root.get("deletedAt")));
            
            if (normalizedResourceType != null) {
                predicates.add(cb.equal(root.get("resourceTypeLkey"), normalizedResourceType));
            }
            
            if (normalizedFacilityId != null) {
                predicates.add(cb.equal(root.get("facilityKey"), normalizedFacilityId));
            }
            
            if (finalResourceKeys != null && !finalResourceKeys.isEmpty()) {
                predicates.add(root.get("resourceKey").in(finalResourceKeys));
            }
            
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return appointmentRepository.findAll(spec);
    }

    public Map<String, Object> getPatient(Long id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found: " + id,
                        "Patient",
                        "notfound"
                ));

        Map<String, Object> out = new HashMap<>();

        out.put("key", patient.getId() != null ? String.valueOf(patient.getId()) : null);
        out.put("dob", patient.getDateOfBirth());

        out.put("first_name", patient.getFirstName());
        out.put("second_name", patient.getSecondName());
        out.put("third_name", patient.getThirdName());
        out.put("last_name", patient.getLastName());

        String fullName = buildFullName(
                patient.getFirstName(),
                patient.getSecondName(),
                patient.getThirdName(),
                patient.getLastName()
        );
        out.put("full_name", fullName);

        fillLegacyDocumentFields(out, patient.getId());

        out.put("phone_number", patient.getHomePhone());
        out.put("mobile_number", patient.getPrimaryMobileNumber());
        out.put("email", patient.getEmail());

        out.put("gender_lkey", patient.getSexAtBirth() != null ? patient.getSexAtBirth().name() : null);
        out.put("patient_mrn", patient.getMedicalRecordNumber());

        return out;
    }

    private String buildFullName(String first, String second, String third, String last) {
        StringBuilder sb = new StringBuilder();
        if (first != null && !first.isBlank()) sb.append(first.trim());
        if (second != null && !second.isBlank()) sb.append(sb.length() > 0 ? " " : "").append(second.trim());
        if (third != null && !third.isBlank()) sb.append(sb.length() > 0 ? " " : "").append(third.trim());
        if (last != null && !last.isBlank()) sb.append(sb.length() > 0 ? " " : "").append(last.trim());
        return sb.toString().trim();
    }

    private void fillLegacyDocumentFields(Map<String, Object> out, Long patientId) {
        out.put("document_type_lkey", null);
        out.put("document_no", null);

        if (patientId == null) return;

        PatientDocument doc = patientDocumentRepository
                .findFirstByPatient_IdAndIsPrimaryTrue(patientId)
                .orElseGet(() -> patientDocumentRepository.findFirstByPatient_IdOrderByIdAsc(patientId).orElse(null));

        if (doc == null) return;

        out.put("document_no", doc.getNumber());

        out.put("document_type_lkey", doc.getType() != null ? doc.getType().name() : null);
    }

    @Transactional
    public Appointment saveAppointment(Appointment appointment, String facilityId) {
        if ((appointment.getFacilityKey() == null || appointment.getFacilityKey().isEmpty()) 
                && facilityId != null && !facilityId.isEmpty() && !facilityId.equals("null")) {
            appointment.setFacilityKey(facilityId);
        }

        if (appointment.getKey() == null || appointment.getKey().isEmpty()) {
            String key = String.valueOf(System.nanoTime());
            appointment.setKey(key);
            appointment.setCreatedAt(new BigDecimal(System.currentTimeMillis()));
        } else {
            appointment.setUpdatedAt(new BigDecimal(System.currentTimeMillis()));
        }

        if (appointment.getIsValid() == null) {
            appointment.setIsValid(true);
        }

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment updateAppointment(Appointment appointment) {
        appointment.setUpdatedAt(new BigDecimal(System.currentTimeMillis()));
        return appointmentRepository.save(appointment);
    }

    public boolean appointmentExists(String patientKey, Date appointmentDate, String resourceTypeLkey, String resourceKey) {
        if (patientKey == null || appointmentDate == null || resourceTypeLkey == null || resourceKey == null) {
            return false;
        }

        try {
            LocalDate date = appointmentDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            String dateStr = date.toString();
            
            Specification<Appointment> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                
                predicates.add(cb.equal(root.get("patientKey"), patientKey));
                predicates.add(cb.equal(root.get("resourceTypeLkey"), resourceTypeLkey));
                predicates.add(cb.equal(root.get("resourceKey"), resourceKey));
                predicates.add(cb.equal(root.get("isValid"), true));
                predicates.add(cb.isNull(root.get("deletedAt")));
                predicates.add(cb.like(root.get("appointmentStart"), dateStr + "%"));
                
                return cb.and(predicates.toArray(new Predicate[0]));
            };

            return appointmentRepository.count(spec) > 0;
        } catch (Exception e) {
            log.error("Error checking appointment existence", e);
            return false;
        }
    }

}

