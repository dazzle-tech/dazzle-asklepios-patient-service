package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionCreateDto;
import com.dazzle.asklepios.service.dto.patientPrescription.PatientPrescriptionUpdateDTO;
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

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionService {

    private final PatientPrescriptionRepository repo;

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PatientPrescriptionService.class);
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    private final PatientRepository patientRepository;
    private final PatientPrescriptionRepository patientPrescriptionRepository;

    public PatientPrescription create(PatientPrescriptionCreateDto prescriptionCreateDto) {

        Patient patient = getPatient(prescriptionCreateDto.patientId);

        PatientPrescription entity = PatientPrescription.builder()
                .patient(patient)
                .encounterId(prescriptionCreateDto.encounterId)
                .prescriptionDate(prescriptionCreateDto.prescriptionDate != null ? prescriptionCreateDto.prescriptionDate : LocalDate.now())
                .urgencyLevel(prescriptionCreateDto.urgencyLevel)
                .status(PrescriptionStatus.DRAFT)
                .fromFacilityId(prescriptionCreateDto.fromFacilityId)
                .fromDepartmentId(prescriptionCreateDto.fromDepartmentId)
                .toFacilityId(prescriptionCreateDto.toFacilityId)
                .toDepartmentId(prescriptionCreateDto.toDepartmentId)
                .build();

        return toDto(repo.save(entity));
    }

    public PatientPrescription update(Long id, PatientPrescriptionUpdateDTO vm) {
        PatientPrescription entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        if (vm.prescriptionDate != null) entity.setPrescriptionDate(vm.prescriptionDate);
        if (vm.urgencyLevel != null) entity.setUrgencyLevel(vm.urgencyLevel);
        if (vm.toFacilityId != null) entity.setToFacilityId(vm.toFacilityId);
        if (vm.toDepartmentId != null) entity.setToDepartmentId(vm.toDepartmentId);

        entity.setLastModifiedBy(vm.lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

        return toDto(repo.save(entity));
    }

    /**
     * Flow requirement:
     * - If there is already a record for this encounter -> return it (do NOT create new one)
     * - Otherwise create once and return it
     */
    public PatientPrescription createOrGetByEncounter(PatientPrescriptionCreateDto dto) {
        LOG.debug("createOrGetByEncounter Patient Prescription payload={}", dto);

        return patientPrescriptionRepository
                .findTopByEncounterIdAndStatusOrderByCreatedDateDesc(dto.getEncounterId(), PrescriptionStatus.DRAFT)
                .orElseGet(() -> {
                    Patient patient = getPatient(dto.getPatientId());
                    PatientPrescription entity = PatientPrescription.builder()
                            .patient(patient)
                            .encounterId(dto.getEncounterId())
                            .prescriptionDate(
                                    dto.getPrescriptionDate() != null
                                            ? dto.getPrescriptionDate()
                                            : java.time.LocalDate.now()
                            )
                            .status(PrescriptionStatus.DRAFT)
                            .fromFacilityId(dto.getFromFacilityId())
                            .fromDepartmentId(dto.getFromDepartmentId())
                            .build();
                    return patientPrescriptionRepository.save(entity);
                });

    }

    @Transactional(readOnly = true)
    public PatientPrescription getPrescription(Long id) {
        return repo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PatientPrescription> list(
            Long patientId,
            Long encounterId,
            PrescriptionStatus status,
            PrescriptionUrgencyLevel urgencyLevel,
            Long prescriptionNum,
            Pageable pageable
    ) {
        if (prescriptionNum != null) {
            if (patientId != null) {
                return repo.findByPatientIdAndPrescriptionNum(patientId, prescriptionNum, pageable).map(this::toDto);
            }
            return repo.findByPrescriptionNum(prescriptionNum, pageable).map(this::toDto);
        }

        // patientId + encounterId combos
        if (patientId != null && encounterId != null) {
            if (status != null && urgencyLevel != null) {
                return repo.findByPatientIdAndEncounterIdAndStatusAndUrgencyLevel(patientId, encounterId, status, urgencyLevel, pageable)
                        .map(this::toDto);
            }
            if (status != null) {
                return repo.findByPatientIdAndEncounterIdAndStatus(patientId, encounterId, status, pageable).map(this::toDto);
            }
            if (urgencyLevel != null) {
                return repo.findByPatientIdAndEncounterIdAndUrgencyLevel(patientId, encounterId, urgencyLevel, pageable).map(this::toDto);
            }
            return repo.findByPatientIdAndEncounterId(patientId, encounterId, pageable).map(this::toDto);
        }

        if (patientId != null) {
            return repo.findByPatientId(patientId, pageable).map(this::toDto);
        }

        return repo.findAll(pageable).map(this::toDto);
    }


    // ===== submit/cancel (مش VM، فقط id + user) =====
    public PatientPrescription submit(Long id, String lastModifiedBy) {
        PatientPrescription entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        entity.setStatus(PrescriptionStatus.SUBMITTED);
        entity.setLastModifiedBy(lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

        return toDto(repo.save(entity));
    }

    public PatientPrescription cancel(Long id, String lastModifiedBy) {
        PatientPrescription entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        entity.setStatus(PrescriptionStatus.CANCELLED);
        entity.setLastModifiedBy(lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

        return toDto(repo.save(entity));
    }

    // ===== mapper (Entity -> DTO) =====
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
        dto.setCreatedBy(e.getCreatedBy());
        dto.setCreatedDate(e.getCreatedDate());
        dto.setLastModifiedBy(e.getLastModifiedBy());
        dto.setLastModifiedDate(e.getLastModifiedDate());
        return dto;
    }

    private Patient getPatient(Long id) {
        return patientRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundAlertException("Patient not found: " + id, "Patient", "notfound"));
    }
}

