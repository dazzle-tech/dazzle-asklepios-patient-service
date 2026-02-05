package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.service.vm.PatientPrescriptionCreateVM;
import com.dazzle.asklepios.service.vm.PatientPrescriptionUpdateVM;
import com.dazzle.asklepios.web.rest.dto.PatientPrescriptionDTO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.logging.Logger;

import static org.hibernate.id.SequenceMismatchStrategy.LOG;

@Service
@RequiredArgsConstructor
@Transactional
public class PatientPrescriptionService {

    private final PatientPrescriptionRepository repo;

    private static final Logger LOG = Logger.getLogger(PatientPrescriptionService.class.getName());
    private final PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;

    public PatientPrescriptionDTO create(PatientPrescriptionCreateVM vm) {

        PatientPrescription entity = PatientPrescription.builder()
                .patientId(vm.patientId)
                .encounterId(vm.encounterId)
                .prescriptionDate(vm.prescriptionDate != null ? vm.prescriptionDate : LocalDate.now())
                .urgencyLevel(vm.urgencyLevel)
                .status(PrescriptionStatus.DRAFT)
                .fromFacilityId(vm.fromFacilityId)
                .fromDepartmentId(vm.fromDepartmentId)
                .toFacilityId(vm.toFacilityId)
                .toDepartmentId(vm.toDepartmentId)
                .build();

        return toDto(repo.save(entity));
    }

    public PatientPrescriptionDTO update(Long id, PatientPrescriptionUpdateVM vm) {
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
    public PatientPrescriptionDTO createOrGetByEncounter(PatientPrescriptionDTO dto) {
        LOG.("createOrGetByEncounter Patient Prescription payload={}", dto);

        return patientPrescriptionMedicationRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(dto.getEncounterId())
                .orElseGet(() -> {
                    Patient patient = getPatient(dto.patientId());

                    EmergencyTriage entity = EmergencyTriage.builder()
                            .patient(patient)
                            .encounterId(dto.encounterId())
                            .build();

                    EmergencyTriage saved = emergencyTriageRepository.save(entity);
                    LOG.debug("createOrGetByEncounter: created id={}", saved.getId());
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public PatientPrescriptionDTO getPrescription(Long id) {
        return repo.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PatientPrescriptionDTO> list(
            Long patientId,
            Long encounterId,
            PrescriptionStatus status,
            PrescriptionUrgencyLevel urgencyLevel,
            Long prescriptionNum,
            Pageable pageable
    ) {
        // أهم فلتر لو موجود: prescriptionNum
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

        // patientId فقط
        if (patientId != null) {
            return repo.findByPatientId(patientId, pageable).map(this::toDto);
        }

        // fallback
        return repo.findAll(pageable).map(this::toDto);
    }


    // ===== submit/cancel (مش VM، فقط id + user) =====
    public PatientPrescriptionDTO submit(Long id, String lastModifiedBy) {
        PatientPrescription entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        entity.setStatus(PrescriptionStatus.SUBMITTED);
        entity.setLastModifiedBy(lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

        return toDto(repo.save(entity));
    }

    public PatientPrescriptionDTO cancel(Long id, String lastModifiedBy) {
        PatientPrescription entity = repo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        entity.setStatus(PrescriptionStatus.CANCELLED);
        entity.setLastModifiedBy(lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

        return toDto(repo.save(entity));
    }

    // ===== mapper (Entity -> DTO) =====
    private PatientPrescriptionDTO toDto(PatientPrescription e) {
        PatientPrescriptionDTO dto = new PatientPrescriptionDTO();
        dto.setId(e.getId());
        dto.setPatientId(e.getPatientId());
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
}

