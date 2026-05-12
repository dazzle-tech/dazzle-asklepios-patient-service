package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionCreateDto;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionService {

    private final PatientPrescriptionRepository prescriptionRepository;
    private final PatientPrescriptionMedicationRepository prescriptionMedicationRepository;

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PatientPrescriptionService.class);
    private final PatientRepository patientRepository;
    private final PatientPrescriptionRepository patientPrescriptionRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (username == null) {
            LOG.warn("[PatientPrescriptionService] AUTH - unauthenticated request");
            throw new BadRequestAlertException(
                    "unauthenticated",
                    "prescription",
                    "No authenticated user"
            );
        }
        return username;
    }

    public PatientPrescription create(PatientPrescriptionCreateDto prescriptionCreateDto) {
        LOG.debug("create a prescriptionCreateDto={}", prescriptionCreateDto);

        Patient patient = getPatient(prescriptionCreateDto.patientId);
        PatientEncounter encounter = getEncounter(prescriptionCreateDto.encounterId);
        if (prescriptionCreateDto.fromFacilityId != null)
            facilityHelper.validateFacilityExists(prescriptionCreateDto.fromFacilityId);
        if (prescriptionCreateDto.toFacilityId != null)
            facilityHelper.validateFacilityExists(prescriptionCreateDto.toFacilityId);
        if (prescriptionCreateDto.fromDepartmentId != null)
            departmentHelper.validateDepartmentExists(prescriptionCreateDto.fromDepartmentId);
        if (prescriptionCreateDto.toDepartmentId != null)
            departmentHelper.validateDepartmentExists(prescriptionCreateDto.toDepartmentId);

        PatientPrescription entity = PatientPrescription.builder()
                .patient(patient)
                .encounterId(encounter.getId())
                .prescriptionDate(prescriptionCreateDto.prescriptionDate != null ? prescriptionCreateDto.prescriptionDate : LocalDate.now())
                .urgencyLevel(prescriptionCreateDto.urgencyLevel)
                .status(PrescriptionStatus.DRAFT)
                .fromFacilityId(prescriptionCreateDto.fromFacilityId)
                .fromDepartmentId(prescriptionCreateDto.fromDepartmentId)
                .toFacilityId(prescriptionCreateDto.toFacilityId)
                .toDepartmentId(prescriptionCreateDto.toDepartmentId)
                .build();

        return toDto(prescriptionRepository.save(entity));
    }

    public PatientPrescription update(Long id, PatientPrescriptionUpdateDTO patientPrescriptionUpdateDTO) {
        LOG.debug("update a PrescriptionMedicationCreateDTO={}", patientPrescriptionUpdateDTO);

        PatientPrescription entity = prescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        if (patientPrescriptionUpdateDTO.prescriptionDate != null)
            entity.setPrescriptionDate(patientPrescriptionUpdateDTO.prescriptionDate);
        if (patientPrescriptionUpdateDTO.urgencyLevel != null)
            entity.setUrgencyLevel(patientPrescriptionUpdateDTO.urgencyLevel);
        if (patientPrescriptionUpdateDTO.toFacilityId != null) {
            facilityHelper.validateFacilityExists(patientPrescriptionUpdateDTO.toFacilityId);
            entity.setToFacilityId(patientPrescriptionUpdateDTO.toFacilityId);
        }
        if (patientPrescriptionUpdateDTO.toDepartmentId != null) {
            departmentHelper.validateDepartmentExists(patientPrescriptionUpdateDTO.toDepartmentId);
            entity.setToDepartmentId(patientPrescriptionUpdateDTO.toDepartmentId);
        }

        return toDto(prescriptionRepository.save(entity));
    }

    /**
     * Flow requirement:
     * - If there is already a record for this encounter -> return it (do NOT create new one)
     * - Otherwise create once and return it
     */
    public PatientPrescription createOrGetByEncounter(PatientPrescriptionCreateDto patientPrescriptionCreateDto) {
        LOG.debug("createOrGetByEncounter Patient Prescription payload={}", patientPrescriptionCreateDto);

        if (patientPrescriptionCreateDto.fromFacilityId != null)
            facilityHelper.validateFacilityExists(patientPrescriptionCreateDto.fromFacilityId);
        if (patientPrescriptionCreateDto.fromDepartmentId != null)
            departmentHelper.validateDepartmentExists(patientPrescriptionCreateDto.fromDepartmentId);

        return patientPrescriptionRepository
                .findTopByEncounterIdAndStatusOrderByCreatedDateDesc(patientPrescriptionCreateDto.getEncounterId(), PrescriptionStatus.DRAFT)
                .orElseGet(() -> {
                    Patient patient = getPatient(patientPrescriptionCreateDto.getPatientId());
                    PatientPrescription entity = PatientPrescription.builder()
                            .patient(patient)
                            .encounterId(patientPrescriptionCreateDto.getEncounterId())
                            .prescriptionDate(
                                    patientPrescriptionCreateDto.getPrescriptionDate() != null
                                            ? patientPrescriptionCreateDto.getPrescriptionDate()
                                            : java.time.LocalDate.now()
                            )
                            .status(PrescriptionStatus.DRAFT)
                            .fromFacilityId(patientPrescriptionCreateDto.getFromFacilityId())
                            .fromDepartmentId(patientPrescriptionCreateDto.getFromDepartmentId())
                            .build();
                    return patientPrescriptionRepository.save(entity);
                });

    }

    @Transactional(readOnly = true)
    public PatientPrescription getPrescription(Long id) {
        LOG.debug("get a Prescription for id ={}", id);

        return prescriptionRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));
    }

    public Page<PatientPrescription> list(
            Long patientId,
            Long encounterId,
            PrescriptionStatus status,
            PrescriptionUrgencyLevel urgencyLevel,
            Long prescriptionNum,
            boolean includeCanceled,
            Pageable pageable
    ) {

        if (patientId != null && encounterId != null) {
            if (status != null) {
                return patientPrescriptionRepository
                        .findByPatientIdAndEncounterIdAndStatus(patientId, encounterId, status, pageable);
            }

            if (!includeCanceled) {
                return patientPrescriptionRepository
                        .findByPatientIdAndEncounterIdAndStatusNot(
                                patientId, encounterId, PrescriptionStatus.CANCELLED, pageable
                        );
            }

            return patientPrescriptionRepository
                    .findByPatientIdAndEncounterId(patientId, encounterId, pageable);
        }

        if (patientId != null) {
            if (status != null) {
                return patientPrescriptionRepository
                        .findByPatientIdAndStatus(patientId, status, pageable);
            }

            if (!includeCanceled) {
                return patientPrescriptionRepository
                        .findByPatientIdAndStatusNot(patientId, PrescriptionStatus.CANCELLED, pageable);
            }

            return patientPrescriptionRepository
                    .findByPatientId(patientId, pageable);
        }

        return patientPrescriptionRepository.findAll(pageable);
    }

    public PatientPrescription submit(Long id) {
        LOG.debug("submit prescription for id ={}", id);

        PatientPrescription entity = prescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        String username = currentUsername();
        Instant now = Instant.now();

        entity.setStatus(PrescriptionStatus.SUBMITTED);
        entity.setSubmitedBy(username);
        entity.setSubmitedDate(now);

        if (entity.getMedications() != null) {
            for (PatientPrescriptionMedication med : entity.getMedications()) {
                if (PrescriptionStatus.CANCELLED.equals(med.getStatus())) {
                    continue;
                }
                med.setStatus(PrescriptionStatus.SUBMITTED);
                med.setLastModifiedBy(username);
                med.setLastModifiedDate(now);
                prescriptionMedicationRepository.save(med);
            }
        }

        return toDto(prescriptionRepository.save(entity));
    }

    public PatientPrescription cancel(Long id, String lastModifiedBy) {

        LOG.debug("cancel prescription for id ={}", id);
        PatientPrescription entity = prescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        entity.setStatus(PrescriptionStatus.CANCELLED);

        return toDto(prescriptionRepository.save(entity));
    }

    private PatientPrescription toDto(PatientPrescription e) {
        PatientPrescription dto = new PatientPrescription();
        dto.setId(e.getId());
        dto.setPatient(e.getPatient());
        dto.setEncounterId(e.getEncounterId());
        dto.setPrescriptionNum(e.getPrescriptionNum());
        dto.setPrescriptionDate(e.getPrescriptionDate());
        dto.setUrgencyLevel(e.getUrgencyLevel());
        dto.setStatus(e.getStatus());
        dto.setFromFacilityId(e.getFromFacilityId());
        dto.setFromDepartmentId(e.getFromDepartmentId());
        dto.setToFacilityId(e.getToFacilityId());
        dto.setToDepartmentId(e.getToDepartmentId());
        return dto;
    }

    private Patient getPatient(Long id) {
        return patientRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundAlertException("Patient not found: " + id, "Patient", "notfound"));
    }


    public Set<Long> findEncounterIdsWithOrders(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptySet();
        }

        return prescriptionRepository.findDistinctByEncounterIdIn(encounterIds)
                .stream()
                .map(prescription -> prescription.getEncounterId())
                .collect(Collectors.toSet());
    }

    private PatientEncounter getEncounter(Long id) {
        return patientEncounterRepository.findById(id).orElseThrow(() -> new BadRequestAlertException("notfound" + id, "PatientPrescription", "Patient Encounter not found: "));
    }
}
