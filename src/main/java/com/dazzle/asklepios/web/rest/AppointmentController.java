package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Appointment;
import com.dazzle.asklepios.service.AppointmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;

@RestController
@RequestMapping("/appointment")
@Slf4j
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping(value = "/appointments-list", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAppointments(
            @RequestParam(value = "resource-type", required = false) String resourceType,
            @RequestParam(value = "facility-id", required = false) String facilityId,
            @RequestParam(value = "resources", required = false) List<String> resources,
            @RequestHeader(required = false) String lang) {
        try {
            List<Appointment> appointments = appointmentService.getAppointments(resourceType, facilityId, resources);
            List<Map<String, Object>> appointmentsWithPatients = new ArrayList<>();
            
            for (Appointment appointment : appointments) {
                Map<String, Object> appointmentMap = new HashMap<>();
                appointmentMap.put("key", appointment.getKey());
                appointmentMap.put("patientKey", appointment.getPatientKey());
                appointmentMap.put("facilityKey", appointment.getFacilityKey());
                appointmentMap.put("resourceTypeLkey", appointment.getResourceTypeLkey());
                appointmentMap.put("resourceKey", appointment.getResourceKey());
                appointmentMap.put("visitTypeLkey", appointment.getVisitTypeLkey());
                appointmentMap.put("durationLkey", appointment.getDurationLkey());
                appointmentMap.put("appointmentStart", appointment.getAppointmentStart());
                appointmentMap.put("appointmentEnd", appointment.getAppointmentEnd());
                appointmentMap.put("instructions", appointment.getInstructions());
                appointmentMap.put("notes", appointment.getNotes());
                appointmentMap.put("priorityLkey", appointment.getPriorityLkey());
                appointmentMap.put("isReminder", appointment.getIsReminder());
                appointmentMap.put("reminderLkey", appointment.getReminderLkey());
                appointmentMap.put("consentForm", appointment.getConsentForm());
                appointmentMap.put("referingPhysicianLkey", appointment.getReferingPhysicianLkey());
                appointmentMap.put("externalPhysician", appointment.getExternalPhysician());
                appointmentMap.put("procedureLevelLkey", appointment.getProcedureLevelLkey());
                appointmentMap.put("resourceLkey", appointment.getResourceLkey());
                appointmentMap.put("instructionsLkey", appointment.getInstructionsLkey());
                appointmentMap.put("appointmentStatus", appointment.getAppointmentStatus());
                appointmentMap.put("reasonLkey", appointment.getReasonLkey());
                appointmentMap.put("reasonValue", appointment.getReasonValue());
                appointmentMap.put("otherReason", appointment.getOtherReason());
                appointmentMap.put("noShowReasonLkey", appointment.getNoShowReasonLkey());
                appointmentMap.put("noShowReasonValue", appointment.getNoShowReasonValue());
                appointmentMap.put("noShowOtherReason", appointment.getNoShowOtherReason());
                appointmentMap.put("isValid", appointment.getIsValid());
                
                if (appointment.getPatientKey() != null) {
                    Map<String, Object> patient = appointmentService.getPatient(appointment.getPatientKey());
                    if (patient != null) {
                        appointmentMap.put("patient", patient);
                    }
                }
                
                appointmentsWithPatients.add(appointmentMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("object", appointmentsWithPatients);
            response.put("extraNumeric", (long) appointments.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching appointments", e);
            return ResponseEntity.status(500).body("Error fetching appointments: " + e.getMessage());
        }
    }

    @PostMapping(value = "/save-appointment", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveAppointment(@RequestBody Map<String, Object> appointmentData,
                                             @RequestHeader(required = false) String facility_id,
                                             @RequestHeader(required = false) Integer access_level,
                                             @RequestHeader(required = false) String lang) {
        try {
            Appointment appointment = mapToAppointment(appointmentData);
            
            if (appointment.getKey() == null || appointment.getKey().isEmpty()) {
                Date appointmentDate = null;
                if (appointmentData.containsKey("appointmentDate")) {
                    Object dateObj = appointmentData.get("appointmentDate");
                    if (dateObj instanceof Date) {
                        appointmentDate = (Date) dateObj;
                    } else if (dateObj instanceof String) {
                        try {
                            appointmentDate = new Date(Long.parseLong((String) dateObj));
                        } catch (Exception e) {
                            log.warn("Could not parse appointmentDate: {}", dateObj);
                        }
                    }
                }
                
                if (appointmentDate != null && appointment.getPatientKey() != null 
                        && appointment.getResourceTypeLkey() != null && appointment.getResourceKey() != null) {
                    boolean exists = appointmentService.appointmentExists(
                            appointment.getPatientKey(), 
                            appointmentDate, 
                            appointment.getResourceTypeLkey(), 
                            appointment.getResourceKey()
                    );
                    
                    if (exists) {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .body(Map.of("message", "The patient already has an appointment on this day."));
                    }
                }
            }

            Appointment saved = appointmentService.saveAppointment(appointment, facility_id);

            Map<String, Object> response = new HashMap<>();
            response.put("object", saved);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error saving appointment", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping(value = "/change-appointment-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> changeAppointmentStatus(@RequestBody Map<String, Object> appointmentData,
                                                      @RequestHeader(required = false) String facility_id,
                                                      @RequestHeader(required = false) Integer access_level,
                                                      @RequestHeader(required = false) String lang) {
        try {
            Appointment appointment = mapToAppointment(appointmentData);
            Appointment updated = appointmentService.updateAppointment(appointment);

            Map<String, Object> response = new HashMap<>();
            response.put("object", updated);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error updating appointment status", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private Appointment mapToAppointment(Map<String, Object> data) {
        Appointment appointment = new Appointment();
        
        if (data.containsKey("key")) appointment.setKey((String) data.get("key"));
        if (data.containsKey("patientKey")) appointment.setPatientKey((String) data.get("patientKey"));
        if (data.containsKey("facilityKey")) appointment.setFacilityKey((String) data.get("facilityKey"));
        if (data.containsKey("resourceTypeLkey")) appointment.setResourceTypeLkey((String) data.get("resourceTypeLkey"));
        if (data.containsKey("resourceKey")) appointment.setResourceKey((String) data.get("resourceKey"));
        if (data.containsKey("visitTypeLkey")) appointment.setVisitTypeLkey((String) data.get("visitTypeLkey"));
        if (data.containsKey("durationLkey")) appointment.setDurationLkey((String) data.get("durationLkey"));
        
        if (data.containsKey("appointmentStart")) {
            Object startObj = data.get("appointmentStart");
            
            if (startObj instanceof Date) {
                Date dateObj = (Date) startObj;
                LocalDateTime localDateTime = LocalDateTime.ofInstant(
                    dateObj.toInstant(), 
                    ZoneId.systemDefault()
                );
                ZoneOffset systemOffset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
                OffsetDateTime startDateTime = localDateTime.atOffset(systemOffset);
                appointment.setAppointmentStart(startDateTime.toString());
            } else if (startObj instanceof String) {
                appointment.setAppointmentStart((String) startObj);
            } else if (startObj instanceof Number) {
                Date dateObj = new Date(((Number) startObj).longValue());
                LocalDateTime localDateTime = LocalDateTime.ofInstant(
                    dateObj.toInstant(), 
                    ZoneId.systemDefault()
                );
                ZoneOffset systemOffset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
                OffsetDateTime startDateTime = localDateTime.atOffset(systemOffset);
                appointment.setAppointmentStart(startDateTime.toString());
            }
        }
        
        if (data.containsKey("appointmentEnd")) {
            Object endObj = data.get("appointmentEnd");
            
            if (endObj instanceof Date) {
                Date dateObj = (Date) endObj;
                LocalDateTime localDateTime = LocalDateTime.ofInstant(
                    dateObj.toInstant(), 
                    ZoneId.systemDefault()
                );
                ZoneOffset systemOffset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
                OffsetDateTime endDateTime = localDateTime.atOffset(systemOffset);
                appointment.setAppointmentEnd(endDateTime.toString());
            } else if (endObj instanceof String) {
                appointment.setAppointmentEnd((String) endObj);
            } else if (endObj instanceof Number) {
                Date dateObj = new Date(((Number) endObj).longValue());
                LocalDateTime localDateTime = LocalDateTime.ofInstant(
                    dateObj.toInstant(), 
                    ZoneId.systemDefault()
                );
                ZoneOffset systemOffset = ZoneId.systemDefault().getRules().getOffset(Instant.now());
                OffsetDateTime endDateTime = localDateTime.atOffset(systemOffset);
                appointment.setAppointmentEnd(endDateTime.toString());
            }
        }
        if (data.containsKey("instructions")) appointment.setInstructions((String) data.get("instructions"));
        if (data.containsKey("notes")) appointment.setNotes((String) data.get("notes"));
        if (data.containsKey("priorityLkey")) appointment.setPriorityLkey((String) data.get("priorityLkey"));
        if (data.containsKey("isReminder")) appointment.setIsReminder((String) data.get("isReminder"));
        if (data.containsKey("reminderLkey")) appointment.setReminderLkey((String) data.get("reminderLkey"));
        if (data.containsKey("consentForm")) appointment.setConsentForm((String) data.get("consentForm"));
        if (data.containsKey("referingPhysicianLkey")) appointment.setReferingPhysicianLkey((String) data.get("referingPhysicianLkey"));
        if (data.containsKey("externalPhysician")) appointment.setExternalPhysician((String) data.get("externalPhysician"));
        if (data.containsKey("procedureLevelLkey")) appointment.setProcedureLevelLkey((String) data.get("procedureLevelLkey"));
        if (data.containsKey("resourceLkey")) appointment.setResourceLkey((String) data.get("resourceLkey"));
        if (data.containsKey("instructionsLkey")) appointment.setInstructionsLkey((String) data.get("instructionsLkey"));
        if (data.containsKey("appointmentStatus")) appointment.setAppointmentStatus((String) data.get("appointmentStatus"));
        if (data.containsKey("reasonLkey")) appointment.setReasonLkey((String) data.get("reasonLkey"));
        if (data.containsKey("reasonValue")) appointment.setReasonValue((String) data.get("reasonValue"));
        if (data.containsKey("otherReason")) appointment.setOtherReason((String) data.get("otherReason"));
        if (data.containsKey("noShowReasonLkey")) appointment.setNoShowReasonLkey((String) data.get("noShowReasonLkey"));
        if (data.containsKey("noShowReasonValue")) appointment.setNoShowReasonValue((String) data.get("noShowReasonValue"));
        if (data.containsKey("noShowOtherReason")) appointment.setNoShowOtherReason((String) data.get("noShowOtherReason"));
        if (data.containsKey("createdBy")) appointment.setCreatedBy((String) data.get("createdBy"));
        if (data.containsKey("updatedBy")) appointment.setUpdatedBy((String) data.get("updatedBy"));
        if (data.containsKey("deletedBy")) appointment.setDeletedBy((String) data.get("deletedBy"));
        if (data.containsKey("isValid")) {
            Object isValid = data.get("isValid");
            if (isValid instanceof Boolean) {
                appointment.setIsValid((Boolean) isValid);
            } else if (isValid instanceof String) {
                appointment.setIsValid(Boolean.parseBoolean((String) isValid));
            }
        }
        
        return appointment;
    }
}

