package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.NephrologyKidneyAssessment;
import com.dazzle.asklepios.domain.enumeration.KidneyDiseaseCause;
import com.dazzle.asklepios.repository.NephrologyKidneyAssessmentRepository;
import com.dazzle.asklepios.service.dto.NephrologyKidneyAssessmentCreateDTO;
import com.dazzle.asklepios.service.dto.NephrologyKidneyAssessmentUpdateDTO;
import com.dazzle.asklepios.service.vm.NephrologyKidneyAssessmentResponseVM;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class NephrologyKidneyAssessmentService {

    private final NephrologyKidneyAssessmentRepository repository;

    public NephrologyKidneyAssessmentResponseVM create(
            NephrologyKidneyAssessmentCreateDTO dto
    ) {
        validateOtherCause(
                dto.getCauseOfKidneyDisease(),
                dto.getOtherCauseOfKidneyDisease()
        );

        repository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
                        dto.getPatientId(),
                        dto.getEncounterId()
                )
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Active nephrology kidney assessment already exists for this patient and encounter"
                    );
                });

        NephrologyKidneyAssessment entity =
                NephrologyKidneyAssessment.builder()
                        .patientId(dto.getPatientId())
                        .encounterId(dto.getEncounterId())
                        .ckdStage(dto.getCkdStage())
                        .kidneyCondition(dto.getKidneyCondition())
                        .causeOfKidneyDisease(dto.getCauseOfKidneyDisease())
                        .otherCauseOfKidneyDisease(
                                resolveOtherCause(
                                        dto.getCauseOfKidneyDisease(),
                                        dto.getOtherCauseOfKidneyDisease()
                                )
                        )
                        .diabetes(dto.getDiabetes())
                        .hypertension(dto.getHypertension())
                        .proteinuria(dto.getProteinuria())
                        .hematuria(dto.getHematuria())
                        .isActive(true)
                        .build();

        return toResponseVM(repository.save(entity));
    }

    public NephrologyKidneyAssessmentResponseVM update(
            NephrologyKidneyAssessmentUpdateDTO dto
    ) {
        NephrologyKidneyAssessment entity = repository
                .findById(dto.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Nephrology kidney assessment not found: " + dto.getId()
                        )
                );

        validateOtherCause(
                dto.getCauseOfKidneyDisease(),
                dto.getOtherCauseOfKidneyDisease()
        );

        entity.setCkdStage(dto.getCkdStage());
        entity.setKidneyCondition(dto.getKidneyCondition());
        entity.setCauseOfKidneyDisease(dto.getCauseOfKidneyDisease());
        entity.setOtherCauseOfKidneyDisease(
                resolveOtherCause(
                        dto.getCauseOfKidneyDisease(),
                        dto.getOtherCauseOfKidneyDisease()
                )
        );
        entity.setDiabetes(dto.getDiabetes());
        entity.setHypertension(dto.getHypertension());
        entity.setProteinuria(dto.getProteinuria());
        entity.setHematuria(dto.getHematuria());

        return toResponseVM(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<NephrologyKidneyAssessmentResponseVM>
    findByPatientAndEncounter(
            Long patientId,
            Long encounterId
    ) {

        return repository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
                        patientId,
                        encounterId
                )
                .map(this::toResponseVM);
    }

    private void validateOtherCause(
            KidneyDiseaseCause cause,
            String otherCause
    ) {
        if (
                cause == KidneyDiseaseCause.OTHER &&
                        (otherCause == null || otherCause.trim().isEmpty())
        ) {
            throw new IllegalArgumentException(
                    "Other kidney disease cause is required when cause is OTHER"
            );
        }
    }

    private String resolveOtherCause(
            KidneyDiseaseCause cause,
            String otherCause
    ) {
        if (cause != KidneyDiseaseCause.OTHER) {
            return null;
        }

        return otherCause == null ? null : otherCause.trim();
    }

    private NephrologyKidneyAssessmentResponseVM toResponseVM(
            NephrologyKidneyAssessment entity
    ) {
        return NephrologyKidneyAssessmentResponseVM.builder()
                .id(entity.getId())
                .patientId(entity.getPatientId())
                .encounterId(entity.getEncounterId())
                .ckdStage(entity.getCkdStage())
                .kidneyCondition(entity.getKidneyCondition())
                .causeOfKidneyDisease(entity.getCauseOfKidneyDisease())
                .otherCauseOfKidneyDisease(entity.getOtherCauseOfKidneyDisease())
                .diabetes(entity.getDiabetes())
                .hypertension(entity.getHypertension())
                .proteinuria(entity.getProteinuria())
                .hematuria(entity.getHematuria())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .lastModifiedBy(entity.getLastModifiedBy())
                .lastModifiedDate(entity.getLastModifiedDate())
                .build();
    }
}