package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.repository.AppointmentRepository;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final JdbcTemplate jdbcTemplate;

    public AppointmentService(AppointmentRepository appointmentRepository, JdbcTemplate jdbcTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.jdbcTemplate = jdbcTemplate;
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

    public Map<String, Object> getPatient(String patientKey) {
        if (patientKey == null || patientKey.isEmpty()) {
            return null;
        }

        try {
            String query = "SELECT key, first_name, last_name, dob, full_name " +
                    "FROM ap_patient WHERE key = ?";
            List<Map<String, Object>> result = jdbcTemplate.queryForList(query, patientKey);
            if (!result.isEmpty()) {
                Map<String, Object> patient = result.get(0);
                
                if (patient.get("full_name") == null || ((String) patient.get("full_name")).isEmpty()) {
                    String firstName = (String) patient.get("first_name");
                    String lastName = (String) patient.get("last_name");
                    if (firstName != null || lastName != null) {
                        String fullName = (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
                        patient.put("full_name", fullName.trim());
                    }
                }
                
                return patient;
            }
        } catch (Exception e) {
            log.error("Failed to query patient", e);
        }

        return null;
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

