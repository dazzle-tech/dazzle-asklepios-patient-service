package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.NephrologyTreatmentPlan;
import com.dazzle.asklepios.domain.enumeration.DialysisAccessSite;
import com.dazzle.asklepios.repository.NephrologyTreatmentPlanRepository;
import com.dazzle.asklepios.service.dto.NephrologyTreatmentPlanCreateDTO;
import com.dazzle.asklepios.service.dto.NephrologyTreatmentPlanUpdateDTO;
import com.dazzle.asklepios.service.vm.NephrologyTreatmentPlanResponseVM;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class NephrologyTreatmentPlanService {

    private final NephrologyTreatmentPlanRepository repository;

    public NephrologyTreatmentPlanResponseVM create(
            NephrologyTreatmentPlanCreateDTO dto
    ) {
        validateOtherAccessSite(
                dto.getAccessSite(),
                dto.getOtherAccessSite()
        );

        repository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
                        dto.getPatientId(),
                        dto.getEncounterId()
                )
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "Active nephrology treatment plan already exists for this patient and encounter"
                    );
                });

        NephrologyTreatmentPlan entity =
                NephrologyTreatmentPlan.builder()
                        .patientId(dto.getPatientId())
                        .encounterId(dto.getEncounterId())
                        .treatmentType(dto.getTreatmentType())
                        .frequency(dto.getFrequency())
                        .schedule(dto.getSchedule())
                        .dialysisDuration(dto.getDialysisDuration())
                        .dryWeight(dto.getDryWeight())
                        .targetWeight(dto.getTargetWeight())
                        .dialysisAccess(dto.getDialysisAccess())
                        .accessSite(dto.getAccessSite())
                        .otherAccessSite(
                                resolveOtherAccessSite(
                                        dto.getAccessSite(),
                                        dto.getOtherAccessSite()
                                )
                        )
                        .bloodFlowRate(dto.getBloodFlowRate())
                        .dialysateFlow(dto.getDialysateFlow())
                        .dialysate(dto.getDialysate())
                        .nephrologistId(dto.getNephrologistId())
                        .startDate(dto.getStartDate())
                        .status(dto.getStatus())
                        .isActive(true)
                        .build();

        return toResponseVM(repository.save(entity));
    }

    public NephrologyTreatmentPlanResponseVM update(
            NephrologyTreatmentPlanUpdateDTO dto
    ) {
        NephrologyTreatmentPlan entity = repository
                .findById(dto.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Nephrology treatment plan not found: " + dto.getId()
                        )
                );

        validateOtherAccessSite(
                dto.getAccessSite(),
                dto.getOtherAccessSite()
        );

        entity.setTreatmentType(dto.getTreatmentType());
        entity.setFrequency(dto.getFrequency());
        entity.setSchedule(dto.getSchedule());
        entity.setDialysisDuration(dto.getDialysisDuration());
        entity.setDryWeight(dto.getDryWeight());
        entity.setTargetWeight(dto.getTargetWeight());
        entity.setDialysisAccess(dto.getDialysisAccess());
        entity.setAccessSite(dto.getAccessSite());
        entity.setOtherAccessSite(
                resolveOtherAccessSite(
                        dto.getAccessSite(),
                        dto.getOtherAccessSite()
                )
        );
        entity.setBloodFlowRate(dto.getBloodFlowRate());
        entity.setDialysateFlow(dto.getDialysateFlow());
        entity.setDialysate(dto.getDialysate());
        entity.setNephrologistId(dto.getNephrologistId());
        entity.setStartDate(dto.getStartDate());
        entity.setStatus(dto.getStatus());

        return toResponseVM(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Optional<NephrologyTreatmentPlanResponseVM>
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

    private void validateOtherAccessSite(
            DialysisAccessSite accessSite,
            String otherAccessSite
    ) {
        if (
                accessSite == DialysisAccessSite.OTHER &&
                        (otherAccessSite == null || otherAccessSite.trim().isEmpty())
        ) {
            throw new IllegalArgumentException(
                    "Other access site is required when access site is OTHER"
            );
        }
    }

    private String resolveOtherAccessSite(
            DialysisAccessSite accessSite,
            String otherAccessSite
    ) {
        if (accessSite != DialysisAccessSite.OTHER) {
            return null;
        }

        return otherAccessSite == null ? null : otherAccessSite.trim();
    }

    private NephrologyTreatmentPlanResponseVM toResponseVM(
            NephrologyTreatmentPlan entity
    ) {
        return NephrologyTreatmentPlanResponseVM.builder()
                .id(entity.getId())
                .patientId(entity.getPatientId())
                .encounterId(entity.getEncounterId())
                .treatmentType(entity.getTreatmentType())
                .frequency(entity.getFrequency())
                .schedule(entity.getSchedule())
                .dialysisDuration(entity.getDialysisDuration())
                .dryWeight(entity.getDryWeight())
                .targetWeight(entity.getTargetWeight())
                .dialysisAccess(entity.getDialysisAccess())
                .accessSite(entity.getAccessSite())
                .otherAccessSite(entity.getOtherAccessSite())
                .bloodFlowRate(entity.getBloodFlowRate())
                .dialysateFlow(entity.getDialysateFlow())
                .dialysate(entity.getDialysate())
                .nephrologistId(entity.getNephrologistId())
                .startDate(entity.getStartDate())
                .status(entity.getStatus())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .lastModifiedBy(entity.getLastModifiedBy())
                .lastModifiedDate(entity.getLastModifiedDate())
                .build();
    }
}
