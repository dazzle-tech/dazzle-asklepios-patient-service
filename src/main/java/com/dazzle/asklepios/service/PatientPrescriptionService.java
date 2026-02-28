package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.PatientPrescriptionMedication;
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

    private final PatientPrescriptionRepository prescriptionRepository;
    private final PatientPrescriptionMedicationRepository prescriptionMedicationRepository;

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(PatientPrescriptionService.class);
    private final PatientRepository patientRepository;
    private final PatientPrescriptionRepository patientPrescriptionRepository;

    public PatientPrescription create(PatientPrescriptionCreateDto prescriptionCreateDto) {
        LOG.debug("create a prescriptionCreateDto={}",prescriptionCreateDto);

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

        return toDto(prescriptionRepository.save(entity));
    }

    public PatientPrescription update(Long id, PatientPrescriptionUpdateDTO patientPrescriptionUpdateDTO) {
        LOG.debug("update a PrescriptionMedicationCreateDTO={}",patientPrescriptionUpdateDTO);

        PatientPrescription entity = prescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        if (patientPrescriptionUpdateDTO.prescriptionDate != null) entity.setPrescriptionDate(patientPrescriptionUpdateDTO.prescriptionDate);
        if (patientPrescriptionUpdateDTO.urgencyLevel != null) entity.setUrgencyLevel(patientPrescriptionUpdateDTO.urgencyLevel);
        if (patientPrescriptionUpdateDTO.toFacilityId != null) entity.setToFacilityId(patientPrescriptionUpdateDTO.toFacilityId);
        if (patientPrescriptionUpdateDTO.toDepartmentId != null) entity.setToDepartmentId(patientPrescriptionUpdateDTO.toDepartmentId);

        entity.setLastModifiedBy(patientPrescriptionUpdateDTO.lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

        return toDto(prescriptionRepository.save(entity));
    }

    /**
     * Flow requirement:
     * - If there is already a record for this encounter -> return it (do NOT create new one)
     * - Otherwise create once and return it
     */
    public PatientPrescription createOrGetByEncounter(PatientPrescriptionCreateDto patientPrescriptionCreateDto) {
        LOG.debug("createOrGetByEncounter Patient Prescription payload={}", patientPrescriptionCreateDto);

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
        LOG.debug("get a Prescription for id ={}",id);

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
                return patientPrescriptionRepository.findByPatientIdAndEncounterIdAndStatus(patientId, encounterId, status, pageable);
            }

            if (!includeCanceled) {
                return patientPrescriptionRepository.findByPatientIdAndEncounterIdAndStatusNot(
                        patientId, encounterId, PrescriptionStatus.CANCELLED, pageable
                );
            }

            return patientPrescriptionRepository.findByPatientIdAndEncounterId(patientId, encounterId, pageable);
        }

        return patientPrescriptionRepository.findAll(pageable).map(this::toDto);
    }


    public PatientPrescription submit(Long id, String lastModifiedBy) {
        LOG.debug("submit prescription for id ={}",id);

        PatientPrescription entity = prescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        entity.setStatus(PrescriptionStatus.SUBMITTED);
        entity.setLastModifiedBy(lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

        if (entity.getMedications() != null) {
            for (PatientPrescriptionMedication med : entity.getMedications()) {
                med.setStatus(PrescriptionStatus.SUBMITTED);
                med.setLastModifiedBy(lastModifiedBy);
                med.setLastModifiedDate(Instant.now());
                prescriptionMedicationRepository.save(med);
            }
        }

        return toDto(prescriptionRepository.save(entity));
    }

    public PatientPrescription cancel(Long id, String lastModifiedBy) {

        LOG.debug("cancel prescription for id ={}",id);
        PatientPrescription entity = prescriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("PatientPrescription not found: " + id));

        entity.setStatus(PrescriptionStatus.CANCELLED);
        entity.setLastModifiedBy(lastModifiedBy);
        entity.setLastModifiedDate(Instant.now());

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

